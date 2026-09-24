package com.discipline.os.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.discipline.os.ui.MainActivity

class SyncForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "discipline_sync_service_channel"
        const val NOTIFICATION_ID = 8080

        fun startService(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        LocalSyncServer.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LocalSyncServer.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalSyncServer.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DisciplineOS AI Assistant Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps local sync server active for 24/7 PC assistant control"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.discipline.os.R.drawable.ic_stat_bridge)
            .setColor(0xFF8B5CF6.toInt())
            .setContentTitle("⚡ DisciplineOS • AI Bridge Active")
            .setContentText("Listening on port 8080 • Direct assistant control")
            .setSubText("Obsidian Sync Engine")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("⚡ DisciplineOS • AI Companion Bridge")
                    .setSummaryText("Port 8080")
                    .bigText("24/7 background bridge active. Assistant can schedule alarms, complete habits, and queue videos instantly.")
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
