package com.aura.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.getcapacitor.Bridge

/**
 * Receives native commands from MusicService and forwards them to the JS layer.
 * Register this in MainActivity after bridge is ready.
 */
class WebCommandReceiver(private val bridge: Bridge) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return

        // Map native command → JS call
        val js = when {
            cmd == "play"        -> "window._nativeCmd && window._nativeCmd('play')"
            cmd == "pause"       -> "window._nativeCmd && window._nativeCmd('pause')"
            cmd == "next"        -> "window.nextTrack && window.nextTrack()"
            cmd == "prev"        -> "window.prevTrack && window.prevTrack()"
            cmd.startsWith("seek:") -> {
                val ms = cmd.substringAfter("seek:").toLongOrNull() ?: return
                "window._nativeCmd && window._nativeCmd('seek', $ms)"
            }
            cmd == "focus:gain"  -> "window._nativeCmd && window._nativeCmd('focusGain')"
            cmd == "focus:loss"  -> "window._nativeCmd && window._nativeCmd('focusLoss')"
            cmd == "focus:duck"  -> "window._nativeCmd && window._nativeCmd('focusDuck')"
            else -> return
        }

        bridge.webView.post {
            bridge.webView.evaluateJavascript(js, null)
        }
    }

    companion object {
        fun register(context: Context, bridge: Bridge): WebCommandReceiver {
            val receiver = WebCommandReceiver(bridge)
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter("com.aura.WEB_COMMAND"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            return receiver
        }
    }
}
