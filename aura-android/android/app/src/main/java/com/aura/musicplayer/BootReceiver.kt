package com.aura.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start service silently — it will re-attach to last session via persistent state
            val serviceIntent = Intent(context, MusicService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
