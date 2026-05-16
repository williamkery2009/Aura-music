package com.aura.musicplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.getcapacitor.BridgeActivity
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "Aura")
class MainActivity : BridgeActivity() {

    private var musicService: MusicService? = null
    private var serviceBound  = false

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            musicService = (binder as MusicService.LocalBinder).service
            serviceBound  = true
            // Inject JS bridge after service ready
            runOnUiThread { injectJSBridge() }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            musicService = null
            serviceBound  = false
        }
    }

    private var webCommandReceiver: WebCommandReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Immersive fullscreen BEFORE super.onCreate ──
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Register Capacitor plugins before super.onCreate
        registerPlugin(NativeBridgePlugin::class.java)

        super.onCreate(savedInstanceState)

        setupImmersive()
        startAndBindService()
        addJavascriptInterface()

        // Register native→web command receiver
        webCommandReceiver = WebCommandReceiver.register(this, bridge)
    }

    private fun setupImmersive() {
        val ctrl = WindowInsetsControllerCompat(window, window.decorView)
        ctrl.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Keep status bar visible but overlay — Aura draws under it
        ctrl.hide(WindowInsetsCompat.Type.navigationBars())
        // Make bars translucent
        window.statusBarColor     = 0x00000000
        window.navigationBarColor = 0x00000000
    }

    private fun startAndBindService() {
        val intent = Intent(this, MusicService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConn, Context.BIND_AUTO_CREATE)
    }

    private fun addJavascriptInterface() {
        // Exposed as window.NativeAura in the WebView
        bridge.webView.addJavascriptInterface(AuraBridge(), "NativeAura")
    }

    private fun injectJSBridge() {
        // Tell the web layer that native bridge is ready
        bridge.webView.post {
            bridge.webView.evaluateJavascript(
                "window._nativeBridgeReady && window._nativeBridgeReady();", null
            )
        }
    }

    /** Thin JS ↔ Kotlin bridge exposed as window.NativeAura */
    inner class AuraBridge {

        @JavascriptInterface
        fun updateMediaSession(title: String, artist: String, album: String, durationMs: Long) {
            musicService?.updateSession(title, artist, album, durationMs)
        }

        @JavascriptInterface
        fun updatePlaybackState(isPlaying: Boolean, positionMs: Long, speedF: Float) {
            musicService?.updatePlaybackState(isPlaying, positionMs, speedF)
        }

        @JavascriptInterface
        fun setArtworkFromUrl(url: String) {
            musicService?.setArtworkFromUrl(url)
        }

        @JavascriptInterface
        fun requestAudioFocus(): Boolean =
            musicService?.requestAudioFocus() ?: false

        @JavascriptInterface
        fun abandonAudioFocus() {
            musicService?.abandonAudioFocus()
        }

        @JavascriptInterface
        fun vibrate(pattern: String) {
            // pattern = "8" or "6,20,6"
            val vibrator = getSystemService(android.os.Vibrator::class.java)
            val parts = pattern.split(",").mapNotNull { it.trim().toLongOrNull() }
            if (parts.size == 1) {
                vibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        parts[0], android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else if (parts.size > 1) {
                vibrator?.vibrate(
                    android.os.VibrationEffect.createWaveform(parts.toLongArray(), -1)
                )
            }
        }

        @JavascriptInterface
        fun scanMusicFolder(path: String): String {
            // Trigger background scan, return task id
            val taskId = "scan_${System.currentTimeMillis()}"
            MusicScanner.scan(this@MainActivity, path, taskId) { result ->
                bridge.webView.post {
                    bridge.webView.evaluateJavascript(
                        "window._onScanResult && window._onScanResult('$taskId', ${result});", null
                    )
                }
            }
            return taskId
        }

        @JavascriptInterface
        fun getDeviceInfo(): String {
            val ram = Runtime.getRuntime().maxMemory() / (1024 * 1024)
            val cores = Runtime.getRuntime().availableProcessors()
            return """{"ram":$ram,"cores":$cores,"sdk":${android.os.Build.VERSION.SDK_INT},"model":"${android.os.Build.MODEL}"}"""
        }

        @JavascriptInterface
        fun keepScreenOn(on: Boolean) {
            runOnUiThread {
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onDestroy() {
        webCommandReceiver?.let { unregisterReceiver(it) }
        if (serviceBound) {
            unbindService(serviceConn)
            serviceBound = false
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersive() // re-apply after dialogs/notifications
    }
}
