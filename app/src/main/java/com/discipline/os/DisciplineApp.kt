package com.discipline.os

import android.app.Application
import android.content.Context
import com.discipline.os.alarm.AlarmScheduler
import com.discipline.os.data.AppDatabase
import com.discipline.os.data.DailyLog
import com.discipline.os.sync.LocalSyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DisciplineApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Database
        val db = AppDatabase.getDatabase(this)

        // 2. Register Notification Channel
        createNotificationChannel()

        // 3. Start Local Sync Foreground Service & HTTP Server (port 8080) for PC assistant
        com.discipline.os.sync.SyncForegroundService.startService(this)
        LocalSyncServer.start(this)

        // 4. Ensure defaults seeded, check 24-hour midnight auto-reset & schedule alarms
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.seedDefaultData(db)
            checkAndPerformDailyReset(db)
            scheduleAllAlarms(db)
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                com.discipline.os.alarm.AlarmReceiver.CHANNEL_ID,
                com.discipline.os.alarm.AlarmReceiver.CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Maximum potential rapid vibration alerts for daily habits"
                enableVibration(true)
                vibrationPattern = com.discipline.os.alarm.VibrationHelper.RAPID_PULSE_TIMINGS
            }
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private suspend fun checkAndPerformDailyReset(db: AppDatabase) {
        val prefs = getSharedPreferences("discipline_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString("last_reset_date", null)

        if (lastDate != null && lastDate != today) {
            // New day detected! Log yesterday's score
            val taskDao = db.taskDao()
            val tasks = taskDao.getAllTasksSync()
            val total = tasks.size
            val completed = tasks.count { it.isCompleted }
            val pct = if (total > 0) (completed.toFloat() / total) * 100f else 0f

            val tasksArray = org.json.JSONArray()
            for (t in tasks) {
                tasksArray.put(org.json.JSONObject().apply {
                    put("title", t.title)
                    put("category", t.category)
                    put("priority", t.priority)
                    put("isCompleted", t.isCompleted)
                    put("scheduledTime", t.scheduledTime)
                })
            }

            db.dailyLogDao().insertLog(
                DailyLog(
                    date = lastDate,
                    completedCount = completed,
                    totalCount = total,
                    percentage = pct,
                    tasksSnapshotJson = tasksArray.toString()
                )
            )

            // Reset tasks for the new day
            taskDao.resetAllTasks()
        }

        prefs.edit().putString("last_reset_date", today).apply()
    }

    private suspend fun scheduleAllAlarms(db: AppDatabase) {
        val tasks = db.taskDao().getAllTasksSync()
        for (task in tasks) {
            if (task.scheduledTime.isNotBlank()) {
                AlarmScheduler.scheduleTaskAlarm(this, task)
            }
        }
    }
}
