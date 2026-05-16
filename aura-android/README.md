# 🎵 Aura v25 — Android App

Premium music player. Web layer (index.html) + Capacitor native shell + full Android architecture.

---

## Requirements

| Tool | Version |
|------|---------|
| Node.js | 18+ |
| Java (JDK) | 17+ |
| Android Studio | Hedgehog+ |
| Android SDK | API 34 |
| Gradle | 8.6 (auto-downloaded) |

---

## Quick Build

```bash
chmod +x build-apk.sh
./build-apk.sh
```

That's it. The APK lands at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Manual Steps

```bash
# 1. Install deps
npm install

# 2. Inject native bridge into index.html
node scripts/build.js

# 3. Sync Capacitor → Android
npx cap sync android

# 4. Build debug APK
cd android && ./gradlew assembleDebug

# 5. Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Architecture

```
aura-android/
├── src/
│   ├── index.html          ← Aura v25 web app (your full player)
│   └── native-bridge.js   ← JS↔Native wiring (auto-injected at build)
│
├── android/
│   └── app/src/main/java/com/aura/musicplayer/
│       ├── AuraApp.kt              ← Application class, notification channels
│       ├── MainActivity.kt         ← Capacitor activity + JS bridge
│       ├── MusicService.kt         ← Background playback + MediaSession + audio focus
│       ├── MusicScanner.kt         ← Device music library scanner (MediaStore)
│       ├── NativeBridgePlugin.kt   ← Capacitor plugin (AuraNative)
│       ├── WebCommandReceiver.kt   ← Native→Web command dispatcher
│       └── BootReceiver.kt         ← Auto-start on device reboot
│
├── scripts/
│   └── build.js           ← Injects bridge.js into index.html
│
├── capacitor.config.ts    ← Capacitor config
├── package.json
└── build-apk.sh           ← One-shot build script
```

---

## JS→Native API

Available as `window.NativeAura` inside the WebView:

```js
// Update MediaSession (lockscreen / notification)
NativeAura.updateMediaSession(title, artist, album, durationMs)

// Update playback state
NativeAura.updatePlaybackState(isPlaying, positionMs, speed)

// Set notification artwork
NativeAura.setArtworkFromUrl(url)

// Audio focus
NativeAura.requestAudioFocus()   // → boolean
NativeAura.abandonAudioFocus()

// Haptics
NativeAura.vibrate("8")          // single pulse
NativeAura.vibrate("6,20,6")     // pattern

// Scan device music
NativeAura.scanMusicFolder("")   // → taskId (result via window._onScanResult)

// Device info
NativeAura.getDeviceInfo()       // → JSON: {ram, cores, sdk, model}

// Keep screen on
NativeAura.keepScreenOn(true|false)
```

---

## Native→JS Events

```js
// Service commands (play/pause/next/prev/seek/focus)
window._nativeCmd = function(cmd, arg) { ... }

// Scan results
window._onScanResult = function(taskId, tracksJson) { ... }

// Bridge ready
window._nativeBridgeReady = function() { ... }
```

---

## Permissions

| Permission | Why |
|-----------|-----|
| `READ_MEDIA_AUDIO` | Device music scan (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Device music scan (Android 12-) |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background playback |
| `WAKE_LOCK` | Keep audio running when screen off |
| `VIBRATE` | Haptic feedback |
| `POST_NOTIFICATIONS` | Now Playing notification |

---

## Release Build

1. Generate keystore:
```bash
keytool -genkey -v -keystore aura-release.jks -keyAlias aura -keyalg RSA -keysize 2048 -validity 10000
```

2. Add to `android/app/build.gradle` signingConfigs:
```groovy
signingConfigs {
    release {
        storeFile file('../../aura-release.jks')
        storePassword 'YOUR_PASS'
        keyAlias 'aura'
        keyPassword 'YOUR_PASS'
    }
}
```

3. Build:
```bash
cd android && ./gradlew assembleRelease
```
