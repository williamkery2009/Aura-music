/**
 * Aura Native Bridge v25
 * Injected into the WebView to wire web events → native Android APIs.
 *
 * Available globals after load:
 *   window.NativeAura      — direct JavascriptInterface (Android only)
 *   window._nativeBridgeReady() — called by MainActivity when service bound
 *   window._nativeCmd(cmd, arg)  — called by WebCommandReceiver on native events
 *   window._onScanResult(taskId, json) — called when music scan completes
 */
(function AuraNativeBridge() {
  'use strict';

  // ── Detect environment ──────────────────────────────────────────
  const isAndroid = /Android/i.test(navigator.userAgent) && !!window.NativeAura;
  if (!isAndroid) {
    console.log('[Bridge] Not Android native — bridge inactive');
    return;
  }
  console.log('[Bridge] Android native detected');

  // ── 1. NATIVE MEDIA SESSION SYNC ───────────────────────────────
  // Patch existing updateMediaSession() to also call native
  const _origUpdateMS = window.updateMediaSession;
  window.updateMediaSession = function(track) {
    if (_origUpdateMS) _origUpdateMS.apply(this, arguments);
    if (!track) return;
    try {
      NativeAura.updateMediaSession(
        track.title  || '',
        track.artist || '',
        track.album  || '',
        Math.round((track.duration || 0) * 1000)
      );
      // Send artwork blob URL as data URL for notification
      if (track._artObjectURL) {
        NativeAura.setArtworkFromUrl(track._artObjectURL);
      }
    } catch(e) {}
  };

  // Sync playback state on every timeupdate + play/pause
  const audio = window.audio;
  if (audio) {
    let _lastSent = 0;
    audio.addEventListener('timeupdate', () => {
      const now = Date.now();
      if (now - _lastSent < 1000) return; // throttle to 1/s
      _lastSent = now;
      try {
        NativeAura.updatePlaybackState(
          !audio.paused,
          Math.round(audio.currentTime * 1000),
          audio.playbackRate || 1.0
        );
      } catch(e) {}
    }, { passive: true });

    audio.addEventListener('play',  () => {
      try { NativeAura.updatePlaybackState(true,  Math.round(audio.currentTime * 1000), audio.playbackRate || 1.0); } catch(e) {}
    }, { passive: true });
    audio.addEventListener('pause', () => {
      try { NativeAura.updatePlaybackState(false, Math.round(audio.currentTime * 1000), audio.playbackRate || 1.0); } catch(e) {}
    }, { passive: true });
  }

  // ── 2. AUDIO FOCUS ─────────────────────────────────────────────
  // Wrap play() to request audio focus first
  if (audio) {
    const _origPlay = audio.play.bind(audio);
    audio.play = async function(...args) {
      try {
        const granted = NativeAura.requestAudioFocus();
        if (!granted) {
          console.warn('[Bridge] Audio focus denied');
          return;
        }
      } catch(e) {}
      return _origPlay(...args);
    };

    audio.addEventListener('pause', () => {
      // Don't abandon focus on pause — user may resume
    }, { passive: true });
    audio.addEventListener('ended', () => {
      try { NativeAura.abandonAudioFocus(); } catch(e) {}
    }, { passive: true });
  }

  // ── 3. NATIVE VIBRATION (replaces navigator.vibrate) ───────────
  // Override window._auraVibrate to use native instead of Web API
  window._auraVibrate = function(pattern) {
    try {
      const p = Array.isArray(pattern) ? pattern.join(',') : String(pattern);
      NativeAura.vibrate(p);
    } catch(e) {
      // Fallback to Web Vibration API
      if (navigator.vibrate) navigator.vibrate(pattern);
    }
  };

  // ── 4. NATIVE COMMANDS ← service (play/pause/next/prev/seek) ───
  window._nativeCmd = function(cmd, arg) {
    switch(cmd) {
      case 'play':
        if (audio && audio.paused) {
          audio.play().catch(() => {});
          if (window.state) window.state.isPlaying = true;
        }
        break;
      case 'pause':
        if (audio && !audio.paused) {
          audio.pause();
          if (window.state) window.state.isPlaying = false;
        }
        break;
      case 'next':
        window.nextTrack && window.nextTrack();
        break;
      case 'prev':
        window.prevTrack && window.prevTrack();
        break;
      case 'seek':
        if (audio && arg != null) {
          audio.currentTime = arg / 1000;
        }
        break;
      case 'focusGain':
        // Re-play if we were playing before focus loss
        if (window.state && window.state.isPlaying && audio && audio.paused) {
          audio.play().catch(() => {});
        }
        break;
      case 'focusLoss':
        // Permanent focus loss (another app took over)
        if (audio && !audio.paused) {
          audio.pause();
          if (window.state) window.state.isPlaying = false;
        }
        break;
      case 'focusDuck':
        // Transient focus loss — duck volume
        if (audio) audio.volume = Math.max(0.1, audio.volume * 0.3);
        break;
    }
  };

  // ── 5. DEVICE INFO → PERF TIER ─────────────────────────────────
  window._nativeBridgeReady = function() {
    try {
      const info = JSON.parse(NativeAura.getDeviceInfo());
      console.log('[Bridge] Device:', info);

      // Override web-computed perf tier with native RAM data
      const ram = info.ram; // MB
      document.body.classList.remove('perf-low','perf-med','perf-high');
      if      (ram < 512)  document.body.classList.add('perf-low');
      else if (ram < 1536) document.body.classList.add('perf-med');
      else                 document.body.classList.add('perf-high');

      window._auraPerfTier = ram < 512 ? 'low' : ram < 1536 ? 'med' : 'high';
      window._auraDeviceInfo = info;
    } catch(e) {}
  };

  // ── 6. MUSIC SCANNER ───────────────────────────────────────────
  window._onScanResult = function(taskId, tracksJson) {
    try {
      const tracks = typeof tracksJson === 'string' ? JSON.parse(tracksJson) : tracksJson;
      // Convert MediaStore tracks to Aura track format and import
      if (Array.isArray(tracks) && window.importNativeTracks) {
        window.importNativeTracks(tracks);
      }
      // Dispatch event for any listener
      document.dispatchEvent(new CustomEvent('aura:scanComplete', { detail: { taskId, tracks } }));
    } catch(e) {
      console.error('[Bridge] Scan result parse error', e);
    }
  };

  // Convert native MediaStore track → Aura track format
  window.importNativeTracks = function(nativeTracks) {
    if (!window.state || !Array.isArray(nativeTracks)) return;
    const existing = new Set(window.state.tracks.map(t => t._nativeUri));

    const newTracks = nativeTracks
      .filter(nt => !existing.has(nt.uri))
      .map(nt => ({
        title:       nt.title,
        artist:      nt.artist,
        album:       nt.album,
        duration:    nt.duration / 1000,
        _nativeUri:  nt.uri,
        _artUri:     nt.artUri,
        _hash:       nt.hash,
        _from:       'device',
        file:        null,   // no File object for native tracks
        url:         nt.uri, // use content:// URI
      }));

    if (newTracks.length === 0) return;
    window.state.tracks.push(...newTracks);
    window.renderLibrary && window.renderLibrary();
    window.showToast && window.showToast(`Added ${newTracks.length} tracks from device`);
    console.log(`[Bridge] Imported ${newTracks.length} native tracks`);
  };

  // Expose scan trigger
  window.scanDeviceMusic = function(path = null) {
    try {
      const taskId = NativeAura.scanMusicFolder(path || '');
      console.log('[Bridge] Scan started:', taskId);
      window.showToast && window.showToast('Scanning device music…');
    } catch(e) {
      console.error('[Bridge] Scan error', e);
    }
  };

  // ── 8. NATIVE LRC IMPORT (file picker → device storage) ───────
  // Adds a native "Import LRC" handler that works with content:// URIs
  window.nativeImportLrc = async function() {
    // Create a file input that accepts .lrc
    const inp = document.createElement('input');
    inp.type   = 'file';
    inp.accept = '.lrc,text/plain';
    inp.style.display = 'none';
    document.body.appendChild(inp);
    inp.click();
    inp.onchange = async () => {
      const file = inp.files && inp.files[0];
      if (!file) return;
      const text = await file.text();
      document.dispatchEvent(new CustomEvent('aura:lrcImported', { detail: { content: text, fileName: file.name } }));
      inp.remove();
    };
  };

  // ── 9. NATIVE LRC EXPORT via FileProvider ──────────────────────
  // Overrides the JS export to also save to device storage
  const _origExport = window.exportCurrentLRC;
  if (_origExport && window.Capacitor) {
    window.exportCurrentLRC = async function() {
      // Still run the blob download version first
      _origExport.apply(this, arguments);

      // Additionally save via native FileProvider for proper Android share
      const track = window.currentTrack && window.currentTrack();
      if (!track) return;
      // Get LRC content same way as JS export
      const hash = track._hash;
      const rec  = hash && window._lyricsHotCache && window._lyricsHotCache.get(hash);
      if (!rec) return;
      const content = rec.lrc || (rec.parsed || []).map(l => {
        const t = l.time, m = Math.floor(t/60), s = (t%60).toFixed(2).padStart(5,'0');
        return `[${String(m).padStart(2,'0')}:${s}]${l.text}`;
      }).join('\n');

      try {
        const { AuraNative } = window.Capacitor.Plugins;
        const result = await AuraNative.exportLrc({
          artist: track.artist || '', title: track.title || '', content
        });
        window.showToast && window.showToast(`Saved: ${result.fileName}`);
      } catch(e) {
        // Capacitor plugin unavailable — JS blob download already handled it
      }
    };
  }

  // ── 10. SCREEN KEEP-ON DURING PLAYBACK ──────────────────────────
  if (audio) {
    audio.addEventListener('play',  () => { try { NativeAura.keepScreenOn(true);  } catch(e) {} }, { passive: true });
    audio.addEventListener('pause', () => { try { NativeAura.keepScreenOn(false); } catch(e) {} }, { passive: true });
  }

  console.log('[Bridge] Aura native bridge v25 ready');
})();
