package com.laxwifi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

object ActionRunner {
    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_ALERTS = "alerts"
    private const val TAG = "LaxWifi"

    private val nextId = AtomicInteger((System.currentTimeMillis() % 1_000_000).toInt() + 100)

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Wi-Fi monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing notification while Lax Wi-Fi watches for network changes"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Network alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications you set up for connecting to or leaving a network"
            }
        )
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun run(context: Context, network: SavedNetwork, actions: List<WifiAction>) {
        for (action in actions) {
            when (action) {
                is WifiAction.SetRinger -> setRinger(context, network, action.mode)
                is WifiAction.Notify -> notify(
                    context,
                    network.name,
                    action.text.replace("{network}", network.name).ifBlank { network.name },
                )
            }
        }
    }

    private fun setRinger(context: Context, network: SavedNetwork, mode: RingerMode) {
        val audio = context.getSystemService(AudioManager::class.java)
        try {
            audio.ringerMode = when (mode) {
                RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Ringer change refused", e)
            notify(
                context,
                network.name,
                "Couldn't switch to ${mode.label}. Allow Do Not Disturb access for Lax Wi-Fi.",
            )
        }
    }

    private fun notify(context: Context, title: String, text: String) {
        ensureChannels(context)
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setColor(0xFFFF00D4.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(nextId.getAndIncrement(), notification)
    }
}
