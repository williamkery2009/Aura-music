package com.aura.musicplayer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.bumptech.glide.Glide
import kotlinx.coroutines.*
import java.net.URL

class MusicService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY   = "com.aura.PLAY"
        const val ACTION_PAUSE  = "com.aura.PAUSE"
        const val ACTION_NEXT   = "com.aura.NEXT"
        const val ACTION_PREV   = "com.aura.PREV"
        const val ACTION_STOP   = "com.aura.STOP"
    }

    inner class LocalBinder : Binder() {
        val service: MusicService get() = this@MusicService
    }

    private val binder     = LocalBinder()
    private val scope      = CoroutineScope(Dispatchers.Main + SupervisorJob())

    lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus    = false
    private var isPlaying   = false

    // Current track metadata
    private var currentTitle    = "Aura"
    private var currentArtist   = "Music Player"
    private var currentAlbum    = ""
    private var currentDuration = 0L
    private var currentPosition = 0L
    private var currentArtwork: Bitmap? = null

    // ── Lifecycle ──────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        initMediaSession()
        startForegroundWithNotification()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle hardware media button intents
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY  -> broadcastToWeb("play")
            ACTION_PAUSE -> broadcastToWeb("pause")
            ACTION_NEXT  -> broadcastToWeb("next")
            ACTION_PREV  -> broadcastToWeb("prev")
            ACTION_STOP  -> { stopSelf(); return START_NOT_STICKY }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession.release()
        abandonAudioFocus()
        super.onDestroy()
    }

    // ── Media Session ──────────────────────────────────────────────

    private fun initMediaSession() {
        val mediaButtonIntent = Intent(
            Intent.ACTION_MEDIA_BUTTON, null,
            this, MediaButtonReceiver::class.java
        )
        val pendingFlag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val mediaButtonPi = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, pendingFlag)

        mediaSession = MediaSessionCompat(this, "AuraSession").apply {
            setMediaButtonReceiver(mediaButtonPi)
            isActive = true

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()         = broadcastToWeb("play")
                override fun onPause()        = broadcastToWeb("pause")
                override fun onSkipToNext()   = broadcastToWeb("next")
                override fun onSkipToPrevious() = broadcastToWeb("prev")
                override fun onStop()         { stopSelf() }
                override fun onSeekTo(pos: Long) = broadcastToWeb("seek:$pos")
            })
        }
    }

    fun updateSession(title: String, artist: String, album: String, durationMs: Long) {
        currentTitle    = title
        currentArtist   = artist
        currentAlbum    = album
        currentDuration = durationMs

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,  album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .apply { currentArtwork?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
            .build()

        mediaSession.setMetadata(metadata)
        updateNotification()
    }

    fun updatePlaybackState(playing: Boolean, positionMs: Long, speed: Float) {
        isPlaying       = playing
        currentPosition = positionMs

        val stateCode = if (playing)
            PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED

        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        val state = PlaybackStateCompat.Builder()
            .setState(stateCode, positionMs, speed)
            .setActions(actions)
            .build()

        mediaSession.setPlaybackState(state)
        updateNotification()
    }

    private var notificationColor: Int = 0xfffc3d6a.toInt()

    fun setArtworkFromUrl(url: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val bmp = Glide.with(applicationContext)
                    .asBitmap()
                    .load(url)
                    .submit(512, 512)
                    .get()
                currentArtwork = bmp

                // Extract dynamic color for notification
                DynamicColorExtractor.extract(bmp) { colors ->
                    notificationColor = colors.primary
                }

                withContext(Dispatchers.Main) {
                    updateSession(currentTitle, currentArtist, currentAlbum, currentDuration)
                }
            } catch (e: Exception) {
                // Artwork load failed — keep existing
            }
        }
    }

    // ── Audio Focus ────────────────────────────────────────────────

    fun requestAudioFocus(): Boolean {
        if (hasFocus) return true

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN             -> { hasFocus = true; broadcastToWeb("focus:gain") }
                    AudioManager.AUDIOFOCUS_LOSS             -> { hasFocus = false; broadcastToWeb("focus:loss") }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT   -> broadcastToWeb("focus:duck")
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> broadcastToWeb("focus:duck")
                }
            }
            .build()
            .also { focusRequest = it }

        val result = audioManager.requestAudioFocus(req)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        hasFocus = false
    }

    // ── Foreground Notification ───────────────────────────────────

    private fun startForegroundWithNotification() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun updateNotification() {
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val pendingFlag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun actionPi(action: String, reqCode: Int): PendingIntent =
            PendingIntent.getService(
                this, reqCode,
                Intent(this, MusicService::class.java).setAction(action),
                pendingFlag
            )

        val playPauseIcon = if (isPlaying)
            android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            pendingFlag
        )

        return NotificationCompat.Builder(this, AuraApp.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(currentTitle)
            .setContentText(if (currentArtist.isNotEmpty()) "$currentArtist • $currentAlbum" else currentAlbum)
            .setLargeIcon(currentArtwork)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setShowWhen(false)
            // Prev
            .addAction(
                android.R.drawable.ic_media_previous, "Previous",
                actionPi(ACTION_PREV, 1)
            )
            // Play/Pause
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play",
                actionPi(playPauseAction, 2)
            )
            // Next
            .addAction(
                android.R.drawable.ic_media_next, "Next",
                actionPi(ACTION_NEXT, 3)
            )
                MediaStyle().setColor(notificationColor)
.setStyle(
    MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(actionPi(ACTION_STOP, 4))
            )
            .build()
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Post a command back to the WebView JS layer */
    private fun broadcastToWeb(command: String) {
        // MainActivity will inject the bridge JS; we use LocalBroadcastManager pattern
        sendBroadcast(
            Intent("com.aura.WEB_COMMAND").putExtra("cmd", command)
        )
    }
}
