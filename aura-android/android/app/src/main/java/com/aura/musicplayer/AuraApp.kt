package com.aura.musicplayer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AuraApp : Application() {

    companion object {
        const val CHANNEL_PLAYBACK = "aura_playback"
        const val CHANNEL_GENERAL  = "aura_general"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        // Persistent playback notification
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLAYBACK,
                "Now Playing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description       = "Music playback controls"
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
        )

        // General alerts
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GENERAL,
                "Aura",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "General Aura notifications"
            }
        )
    }
}
