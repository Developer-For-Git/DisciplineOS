package com.discipline.os.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.discipline.os.R
import com.discipline.os.data.AppDatabase
import com.discipline.os.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "discipline_high_priority_alarms"
        const val CHANNEL_NAME = "Discipline Accountability Alarms"

        const val ACTION_COMPLETE_TASK = "com.discipline.os.action.COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK = "com.discipline.os.action.SNOOZE_TASK"
        const val ACTION_COMPLETE_VIDEO = "com.discipline.os.action.COMPLETE_VIDEO"

        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_RING_SOUND = "extra_ring_sound"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_TASK_DESC = "extra_task_desc"

        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_VIDEO_CATEGORY = "extra_video_category"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Handle Direct Notification Actions
        when (intent.action) {
            ACTION_COMPLETE_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val notifId = intent.getIntExtra("notif_id", taskId.toInt())
                notificationManager.cancel(notifId)
                if (taskId > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(context)
                        db.taskDao().setTaskCompleted(taskId, true)
                        VibrationHelper.triggerRapidVibration(context)
                    }
                }
                return
            }

            ACTION_SNOOZE_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val notifId = intent.getIntExtra("notif_id", taskId.toInt())
                notificationManager.cancel(notifId)
                if (taskId > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(context)
                        val task = db.taskDao().getTaskById(taskId)
                        if (task != null) {
                            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 10) }
                            val snoozeTime = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                            AlarmScheduler.scheduleTaskAlarm(context, task.copy(scheduledTime = snoozeTime))
                        }
                    }
                }
                return
            }

            ACTION_COMPLETE_VIDEO -> {
                val videoId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
                val notifId = intent.getIntExtra("notif_id", (videoId + 50000).toInt())
                notificationManager.cancel(notifId)
                if (videoId > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(context)
                        db.videoDao().setVideoWatched(videoId, true)
                        VibrationHelper.triggerRapidVibration(context)
                    }
                }
                return
            }
        }

        // Check if Video Study Reminder
        val isVideoReminder = intent.hasExtra(EXTRA_VIDEO_URL)

        if (isVideoReminder) {
            val videoId = intent.getLongExtra(EXTRA_VIDEO_ID, 0L)
            val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Study Video"
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: "https://youtube.com"
            val category = intent.getStringExtra(EXTRA_VIDEO_CATEGORY) ?: "Coding"
            val reminderType = intent.getStringExtra(EXTRA_REMINDER_TYPE) ?: "RAPID_VIBRATE"

            val isSoundAlarm = reminderType == "ALARM_SOUND"
            val isRapidVibrate = reminderType == "RAPID_VIBRATE" || isSoundAlarm

            ensureChannel(notificationManager, isSoundAlarm)

            val notifId = (videoId + 50000).toInt()

            // Action: Watch Now on YouTube
            val watchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingWatchIntent = PendingIntent.getActivity(
                context,
                notifId,
                watchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Action: Mark Watched
            val completeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_COMPLETE_VIDEO
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra("notif_id", notifId)
            }
            val pendingComplete = PendingIntent.getBroadcast(
                context,
                notifId + 1,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = if (isSoundAlarm) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else null

            val bigText = "Category: $category\n\n🎯 Study Takeaway:\n\"Focus intently. Compile and write code yourself. Zero distractions.\""

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_video)
                .setColor(0xFF38BDF8.toInt()) // Cyan accent
                .setContentTitle("🎬 Study Time: $videoTitle")
                .setContentText("[$category] Tap to watch on YouTube now.")
                .setSubText("Study Vault • $category")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle("🎬 Study Session: $videoTitle")
                        .setSummaryText(category)
                        .bigText(bigText)
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingWatchIntent)
                .addAction(R.drawable.ic_stat_video, "▶ Watch Now", pendingWatchIntent)
                .addAction(R.drawable.ic_stat_check, "✓ Mark Watched", pendingComplete)
                .setVibrate(VibrationHelper.RAPID_PULSE_TIMINGS)

            if (isSoundAlarm && soundUri != null) {
                builder.setSound(soundUri)
            }

            notificationManager.notify(notifId, builder.build())

            if (isRapidVibrate) {
                VibrationHelper.triggerRapidVibration(context, repeat = false)
            }
            return
        }

        // Standard Habit Task Alarm (Big Tech / Obsidian Polish)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Habit Execution"
        val ringSound = intent.getBooleanExtra(EXTRA_RING_SOUND, false)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 1L)
        val notifId = taskId.toInt()

        ensureChannel(notificationManager, ringSound)

        // Open app intent
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct Action: Mark Complete
        val markDoneIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_COMPLETE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra("notif_id", notifId)
        }
        val pendingMarkDone = PendingIntent.getBroadcast(
            context,
            notifId + 100,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct Action: Snooze 10m
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra("notif_id", notifId)
        }
        val pendingSnooze = PendingIntent.getBroadcast(
            context,
            notifId + 200,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = if (ringSound) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else null

        val bigText = "⚡ High-Priority Discipline Alert\n\n\"No excuses today. Your yesterday's self is watching. Put in the work.\""

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_discipline)
            .setColor(0xFF8B5CF6.toInt()) // Obsidian signature violet
            .setContentTitle("⚡ Focus Check: $taskTitle")
            .setContentText("No excuses today. Execute now.")
            .setSubText("DisciplineOS • P1 Urgent")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("⚡ $taskTitle")
                    .setSummaryText("P1 Critical")
                    .bigText(bigText)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(R.drawable.ic_stat_check, "✓ Mark Done", pendingMarkDone)
            .addAction(R.drawable.ic_stat_snooze, "+10m Snooze", pendingSnooze)
            .setVibrate(VibrationHelper.RAPID_PULSE_TIMINGS)

        if (ringSound && soundUri != null) {
            builder.setSound(soundUri)
        }

        notificationManager.notify(notifId, builder.build())

        // Physical high-potential vibration burst
        VibrationHelper.triggerRapidVibration(context, repeat = false)
    }

    private fun ensureChannel(nm: NotificationManager, sound: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Obsidian high-speed vibration alerts & study reminders"
                enableVibration(true)
                vibrationPattern = VibrationHelper.RAPID_PULSE_TIMINGS
                enableLights(true)
                lightColor = Color.rgb(139, 92, 246)
                if (!sound) {
                    setSound(null, null)
                }
            }
            nm.createNotificationChannel(channel)
        }
    }
}
