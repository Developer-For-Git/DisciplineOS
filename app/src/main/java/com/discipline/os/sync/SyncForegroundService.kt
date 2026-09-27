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
import com.discipline.os.telemetry.StepTracker
import com.discipline.os.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stepJob: Job? = null
    private var lastNotifiedSteps = -1

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0, 0f))
        LocalSyncServer.start(this)

        val tracker = StepTracker.getInstance(this)
        tracker.startTracking()

        stepJob = serviceScope.launch {
            tracker.stepStats.collect { stats ->
                updateNotification(stats.todaySteps, stats.distanceKm)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LocalSyncServer.start(this)
        StepTracker.getInstance(this).startTracking()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stepJob?.cancel()
        serviceScope.cancel()
        LocalSyncServer.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DisciplineOS Pedometer & AI Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps direct phone pedometer and local sync server active 24/7"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(steps: Int, distanceKm: Float) {
        // Update notification when step count changes noticeably
        if (lastNotifiedSteps == -1 || kotlin.math.abs(steps - lastNotifiedSteps) >= 3) {
            lastNotifiedSteps = steps
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, buildNotification(steps, distanceKm))
        }
    }

    private fun buildNotification(steps: Int = 0, distanceKm: Float = 0f): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val distStr = String.format(java.util.Locale.US, "%.1f", distanceKm)
        val title = if (steps > 0) {
            "⚡ DisciplineOS • 🚶 %,d Steps ($distStr km)".format(steps)
        } else {
            "⚡ DisciplineOS • Direct Phone Pedometer Active"
        }

        val text = "Direct phone movement sensors active • Port 8080"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.discipline.os.R.drawable.ic_stat_bridge)
            .setColor(0xFF8B5CF6.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Life Telemetry")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .setSummaryText("Pedometer")
                    .bigText("Phone hardware pedometer tracking footsteps 24/7 in background. Walking: %,d steps today ($distStr km). Local assistant server active on port 8080.".format(steps))
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
