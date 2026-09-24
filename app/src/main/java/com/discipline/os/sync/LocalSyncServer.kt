package com.discipline.os.sync

import android.content.Context
import com.discipline.os.data.AppDatabase
import com.discipline.os.data.DailyLog
import com.discipline.os.data.FuelEntry
import com.discipline.os.data.Task
import com.discipline.os.data.VideoEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

object LocalSyncServer {
    private const val PORT = 8080
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PORT)
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(context, client)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private suspend fun handleClient(context: Context, socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

            val requestLine = reader.readLine() ?: return
            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) return

            val method = requestParts[0].uppercase()
            val fullPath = requestParts[1]
            val path = if (fullPath.contains("?")) fullPath.substringBefore("?") else fullPath
            val queryParams = parseQueryParams(fullPath)

            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            var body = ""
            if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                reader.read(charArray, 0, contentLength)
                body = String(charArray)
            }

            val db = AppDatabase.getDatabase(context)
            val taskDao = db.taskDao()
            val fuelDao = db.fuelDao()
            val logDao = db.dailyLogDao()
            val videoDao = db.videoDao()

            var responseJson = "{}"
            var statusCode = 200

            when {
                // 1. Overall Status
                path == "/api/status" && method == "GET" -> {
                    val tasks = taskDao.getAllTasksSync()
                    val completed = tasks.count { it.isCompleted }
                    val p1Count = tasks.count { it.priority == 1 }
                    val p1Completed = tasks.count { it.priority == 1 && it.isCompleted }
                    val videos = videoDao.getAllVideosSync()
                    val vPending = videos.count { !it.isWatched }
                    val vWatched = videos.count { it.isWatched }

                    val status = JSONObject().apply {
                        put("app", "DisciplineOS")
                        put("version", "2.1")
                        put("total_tasks", tasks.size)
                        put("completed_count", completed)
                        put("percentage", if (tasks.isNotEmpty()) (completed * 100 / tasks.size) else 0)
                        put("p1_critical_total", p1Count)
                        put("p1_critical_completed", p1Completed)
                        put("videos_pending", vPending)
                        put("videos_watched", vWatched)
                        put("port", PORT)
                    }
                    responseJson = status.toString()
                }

                // 2. Tasks List with optional filters (?category=... or ?priority=...)
                path == "/api/tasks" && method == "GET" -> {
                    var tasks = taskDao.getAllTasksSync()
                    queryParams["category"]?.let { cat ->
                        tasks = tasks.filter { it.category.equals(cat, ignoreCase = true) }
                    }
                    queryParams["priority"]?.toIntOrNull()?.let { p ->
                        tasks = tasks.filter { it.priority == p }
                    }
                    queryParams["completed"]?.toBooleanStrictOrNull()?.let { c ->
                        tasks = tasks.filter { it.isCompleted == c }
                    }

                    val array = JSONArray()
                    for (t in tasks) {
                        array.put(taskToJson(t))
                    }
                    responseJson = array.toString()
                }

                // 3. Single Task details
                path == "/api/task" && method == "GET" -> {
                    val id = queryParams["id"]?.toLongOrNull() ?: 0L
                    val task = taskDao.getTaskById(id)
                    if (task != null) {
                        responseJson = taskToJson(task).toString()
                    } else {
                        statusCode = 404
                        responseJson = "{\"error\": \"Task not found\"}"
                    }
                }

                // 4. Create Task
                (path == "/api/task/add" || (path == "/api/tasks" && method == "POST")) -> {
                    val json = JSONObject(body)
                    val newTask = Task(
                        title = json.getString("title"),
                        description = json.optString("description", ""),
                        category = json.optString("category", "Habit"),
                        priority = json.optInt("priority", 2),
                        scheduledTime = json.optString("scheduledTime", ""),
                        ringSound = json.optBoolean("ringSound", false),
                        subtasksJson = json.optJSONArray("subtasks")?.toString() ?: json.optString("subtasksJson", "[]"),
                        sortOrder = json.optInt("sortOrder", 0)
                    )
                    val id = taskDao.insertTask(newTask)
                    val created = taskDao.getTaskById(id)
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("task", created?.let { taskToJson(it) })
                    }.toString()
                }

                // 5. Edit / Update Task (Full CRUD update)
                (path == "/api/task/update" || path == "/api/task/edit" || (path == "/api/tasks" && method == "PUT")) -> {
                    val json = JSONObject(body)
                    val id = json.getLong("id")
                    val existing = taskDao.getTaskById(id)
                    if (existing != null) {
                        val updated = existing.copy(
                            title = json.optString("title", existing.title),
                            description = json.optString("description", existing.description),
                            category = json.optString("category", existing.category),
                            priority = json.optInt("priority", existing.priority),
                            scheduledTime = json.optString("scheduledTime", existing.scheduledTime),
                            ringSound = if (json.has("ringSound")) json.getBoolean("ringSound") else existing.ringSound,
                            isCompleted = if (json.has("isCompleted")) json.getBoolean("isCompleted") else existing.isCompleted,
                            subtasksJson = json.optJSONArray("subtasks")?.toString() ?: json.optString("subtasksJson", existing.subtasksJson)
                        )
                        taskDao.updateTask(updated)
                        responseJson = JSONObject().apply {
                            put("success", true)
                            put("task", taskToJson(updated))
                        }.toString()
                    } else {
                        statusCode = 404
                        responseJson = "{\"error\": \"Task not found to update\"}"
                    }
                }

                // 6. Toggle Task Completion
                path == "/api/task/toggle" && method == "POST" -> {
                    val json = JSONObject(body)
                    val id = json.getLong("id")
                    val completed = json.getBoolean("completed")
                    taskDao.setTaskCompleted(id, completed)
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("id", id)
                        put("completed", completed)
                    }.toString()
                }

                // 7. Toggle Individual Subtask inside a Task
                path == "/api/task/subtask/toggle" && method == "POST" -> {
                    val json = JSONObject(body)
                    val taskId = json.getLong("taskId")
                    val subtaskIndex = json.getInt("subtaskIndex")
                    val task = taskDao.getTaskById(taskId)
                    if (task != null) {
                        val subtasks = JSONArray(task.subtasksJson)
                        if (subtaskIndex in 0 until subtasks.length()) {
                            val sub = subtasks.getJSONObject(subtaskIndex)
                            val currentDone = sub.optBoolean("done", false)
                            sub.put("done", !currentDone)
                            taskDao.updateSubtasks(taskId, subtasks.toString())
                            responseJson = JSONObject().apply {
                                put("success", true)
                                put("subtasks", subtasks)
                            }.toString()
                        } else {
                            statusCode = 400
                            responseJson = "{\"error\": \"Index out of bounds\"}"
                        }
                    } else {
                        statusCode = 404
                        responseJson = "{\"error\": \"Task not found\"}"
                    }
                }

                // 8. Add a Subtask to a Task
                path == "/api/task/subtask/add" && method == "POST" -> {
                    val json = JSONObject(body)
                    val taskId = json.getLong("taskId")
                    val text = json.getString("text")
                    val task = taskDao.getTaskById(taskId)
                    if (task != null) {
                        val subtasks = JSONArray(task.subtasksJson)
                        subtasks.put(JSONObject().apply {
                            put("text", text)
                            put("done", false)
                        })
                        taskDao.updateSubtasks(taskId, subtasks.toString())
                        responseJson = JSONObject().apply {
                            put("success", true)
                            put("subtasks", subtasks)
                        }.toString()
                    } else {
                        statusCode = 404
                        responseJson = "{\"error\": \"Task not found\"}"
                    }
                }

                // Delete Subtask from a Task
                path == "/api/task/subtask/delete" && method == "POST" -> {
                    val json = JSONObject(body)
                    val taskId = json.getLong("taskId")
                    val subtaskIndex = json.getInt("subtaskIndex")
                    val task = taskDao.getTaskById(taskId)
                    if (task != null) {
                        val subtasks = JSONArray(task.subtasksJson)
                        if (subtaskIndex in 0 until subtasks.length()) {
                            val newArr = JSONArray()
                            for (i in 0 until subtasks.length()) {
                                if (i != subtaskIndex) newArr.put(subtasks.get(i))
                            }
                            taskDao.updateSubtasks(taskId, newArr.toString())
                            responseJson = JSONObject().apply {
                                put("success", true)
                                put("subtasks", newArr)
                            }.toString()
                        } else {
                            statusCode = 400
                            responseJson = "{\"error\": \"Index out of bounds\"}"
                        }
                    } else {
                        statusCode = 404
                        responseJson = "{\"error\": \"Task not found\"}"
                    }
                }

                // Set / Change Alarm for a Task
                path == "/api/task/alarm" && method == "POST" -> {
                    val json = JSONObject(body)
                    val id = json.getLong("id")
                    val time = json.optString("time", "")
                    val ringSound = json.optBoolean("ringSound", false)
                    val task = taskDao.getTaskById(id)
                    if (task != null) {
                        val updated = task.copy(scheduledTime = time, ringSound = ringSound)
                        taskDao.updateTask(updated)
                        if (time.isNotBlank()) {
                            com.discipline.os.alarm.AlarmScheduler.scheduleTaskAlarm(context, updated)
                        } else {
                            com.discipline.os.alarm.AlarmScheduler.cancelTaskAlarm(context, updated)
                        }
                        responseJson = JSONObject().apply {
                            put("success", true)
                            put("id", id)
                            put("scheduledTime", time)
                            put("ringSound", ringSound)
                            put("message", if (time.isNotBlank()) "Alarm set for $time" else "Alarm cancelled")
                        }.toString()
                    } else {
                        statusCode = 404
                        responseJson = "{\"error\": \"Task not found\"}"
                    }
                }

                // 9. Delete Task
                (path == "/api/task/delete" || path == "/api/task") && (method == "POST" || method == "DELETE") -> {
                    val id = if (body.isNotBlank()) JSONObject(body).optLong("id") else queryParams["id"]?.toLongOrNull() ?: 0L
                    taskDao.deleteTaskById(id)
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("deleted_id", id)
                    }.toString()
                }

                // 10. Prove Them Wrong (Fuel) List
                path == "/api/fuel" && method == "GET" -> {
                    val list = fuelDao.getAllFuelSync()
                    val array = JSONArray()
                    for (f in list) {
                        array.put(fuelToJson(f))
                    }
                    responseJson = array.toString()
                }

                // 11. Add Fuel Entry
                (path == "/api/fuel/add" || (path == "/api/fuel" && method == "POST")) -> {
                    val json = JSONObject(body)
                    val entry = FuelEntry(
                        personOrIncident = json.getString("personOrIncident"),
                        defianceVow = json.getString("defianceVow"),
                        category = json.optString("category", "Doubter")
                    )
                    val id = fuelDao.insertFuel(entry)
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("id", id)
                        put("entry", fuelToJson(entry.copy(id = id)))
                    }.toString()
                }

                // 12. Edit Fuel Entry
                (path == "/api/fuel/update" || (path == "/api/fuel" && method == "PUT")) -> {
                    val json = JSONObject(body)
                    val id = json.getLong("id")
                    val existing = fuelDao.getFuelById(id)
                    if (existing != null) {
                        val updated = existing.copy(
                            personOrIncident = json.optString("personOrIncident", existing.personOrIncident),
                            defianceVow = json.optString("defianceVow", existing.defianceVow),
                            category = json.optString("category", existing.category)
                        )
                        fuelDao.updateFuel(updated)
                        responseJson = JSONObject().apply {
                            put("success", true)
                            put("entry", fuelToJson(updated))
                        }.toString()
                    } else {
                        statusCode = 404
                        responseJson = "{\"error\": \"Fuel entry not found\"}"
                    }
                }

                // 13. Delete Fuel Entry
                (path == "/api/fuel/delete" && method == "POST") || (path == "/api/fuel" && method == "DELETE") -> {
                    val id = if (body.isNotBlank()) JSONObject(body).optLong("id") else queryParams["id"]?.toLongOrNull() ?: 0L
                    fuelDao.deleteFuelById(id)
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("deleted_id", id)
                    }.toString()
                }

                // 14. YouTube Videos List
                path == "/api/videos" && method == "GET" -> {
                    var videos = videoDao.getAllVideosSync()
                    queryParams["category"]?.let { cat ->
                        videos = videos.filter { it.category.equals(cat, ignoreCase = true) }
                    }
                    queryParams["watched"]?.toBooleanStrictOrNull()?.let { w ->
                        videos = videos.filter { it.isWatched == w }
                    }
                    val array = JSONArray()
                    for (v in videos) {
                        array.put(videoToJson(v))
                    }
                    responseJson = array.toString()
                }

                // 15. Add YouTube Video
                (path == "/api/video/add" || (path == "/api/videos" && method == "POST")) -> {
                    val json = JSONObject(body)
                    val reminderMs = json.optLong("reminderEpochMs", 0L)
                    val entry = VideoEntry(
                        title = json.getString("title"),
                        url = json.getString("url"),
                        category = json.optString("category", "Coding"),
                        reminderEpochMs = reminderMs,
                        reminderDelayText = json.optString("reminderDelayText", ""),
                        reminderType = json.optString("reminderType", "RAPID_VIBRATE"),
                        notes = json.optString("notes", "")
                    )
                    val id = videoDao.insertVideo(entry)
                    val saved = entry.copy(id = id)
                    if (reminderMs > System.currentTimeMillis()) {
                        com.discipline.os.alarm.AlarmScheduler.scheduleVideoReminder(context, saved)
                    }
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("id", id)
                        put("video", videoToJson(saved))
                    }.toString()
                }

                // 16. Toggle Video Watched
                path == "/api/video/toggle" && method == "POST" -> {
                    val json = JSONObject(body)
                    val id = json.getLong("id")
                    val watched = json.getBoolean("watched")
                    videoDao.setVideoWatched(id, watched)
                    if (watched) {
                        com.discipline.os.alarm.VibrationHelper.triggerRapidVibration(context)
                    }
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("id", id)
                        put("watched", watched)
                    }.toString()
                }

                // 17. Edit / Update Video
                (path == "/api/video/update" || (path == "/api/videos" && method == "PUT")) -> {
                    val json = JSONObject(body)
                    val id = json.getLong("id")
                    val existing = videoDao.getVideoById(id)
                    if (existing != null) {
                        val updated = existing.copy(
                            title = json.optString("title", existing.title),
                            url = json.optString("url", existing.url),
                            category = json.optString("category", existing.category),
                            reminderEpochMs = json.optLong("reminderEpochMs", existing.reminderEpochMs),
                            reminderDelayText = json.optString("reminderDelayText", existing.reminderDelayText),
                            reminderType = json.optString("reminderType", existing.reminderType),
                            isWatched = if (json.has("isWatched")) json.getBoolean("isWatched") else existing.isWatched,
                            notes = json.optString("notes", existing.notes)
                        )
                        videoDao.updateVideo(updated)
                        if (updated.reminderEpochMs > System.currentTimeMillis() && !updated.isWatched) {
                            com.discipline.os.alarm.AlarmScheduler.scheduleVideoReminder(context, updated)
                        }
                        responseJson = JSONObject().apply {
                            put("success", true)
                            put("video", videoToJson(updated))
                        }.toString()
                    } else {
                        statusCode = 404
                        responseJson = "{\"error\": \"Video not found\"}"
                    }
                }

                // 18. Delete Video
                (path == "/api/video/delete" && method == "POST") || (path == "/api/video" && method == "DELETE") -> {
                    val id = if (body.isNotBlank()) JSONObject(body).optLong("id") else queryParams["id"]?.toLongOrNull() ?: 0L
                    videoDao.deleteVideoById(id)
                    com.discipline.os.alarm.AlarmScheduler.cancelVideoReminder(context, id)
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("deleted_id", id)
                    }.toString()
                }

                // 14. Advanced Analytics
                path == "/api/analytics" && method == "GET" -> {
                    val tasks = taskDao.getAllTasksSync()
                    val logs = logDao.getAllLogsSync()
                    val total = tasks.size
                    val completed = tasks.count { it.isCompleted }

                    val categoryStats = JSONObject()
                    tasks.groupBy { it.category }.forEach { (cat, list) ->
                        val catObj = JSONObject().apply {
                            put("total", list.size)
                            put("completed", list.count { it.isCompleted })
                        }
                        categoryStats.put(cat, catObj)
                    }

                    val priorityStats = JSONObject().apply {
                        put("P1_Urgent", tasks.count { it.priority == 1 && it.isCompleted }.toString() + "/" + tasks.count { it.priority == 1 })
                        put("P2_High", tasks.count { it.priority == 2 && it.isCompleted }.toString() + "/" + tasks.count { it.priority == 2 })
                        put("P3_Normal", tasks.count { it.priority == 3 && it.isCompleted }.toString() + "/" + tasks.count { it.priority == 3 })
                    }

                    val analytics = JSONObject().apply {
                        put("today_score_percentage", if (total > 0) (completed * 100 / total) else 0)
                        put("completed_count", completed)
                        put("total_tasks", total)
                        put("categories", categoryStats)
                        put("priorities", priorityStats)
                        put("history_days_count", logs.size)
                        val historyArr = JSONArray()
                        logs.take(7).forEach { l ->
                            historyArr.put(JSONObject().apply {
                                put("date", l.date)
                                put("score", "${l.completedCount}/${l.totalCount}")
                                put("percentage", l.percentage)
                            })
                        }
                        put("recent_history", historyArr)
                    }
                    responseJson = analytics.toString()
                }

                // 15. Complete JSON Database Backup
                path == "/api/backup" && method == "GET" -> {
                    val tasks = taskDao.getAllTasksSync()
                    val fuel = fuelDao.getAllFuelSync()
                    val logs = logDao.getAllLogsSync()
                    val videos = videoDao.getAllVideosSync()

                    val backup = JSONObject().apply {
                        put("version", "2.1")
                        put("export_timestamp", System.currentTimeMillis())
                        val tArray = JSONArray()
                        tasks.forEach { tArray.put(taskToJson(it)) }
                        put("tasks", tArray)

                        val fArray = JSONArray()
                        fuel.forEach { fArray.put(fuelToJson(it)) }
                        put("fuel", fArray)

                        val vArray = JSONArray()
                        videos.forEach { vArray.put(videoToJson(it)) }
                        put("videos", vArray)

                        val lArray = JSONArray()
                        logs.forEach { l ->
                            lArray.put(JSONObject().apply {
                                put("date", l.date)
                                put("completedCount", l.completedCount)
                                put("totalCount", l.totalCount)
                                put("percentage", l.percentage)
                            })
                        }
                        put("logs", lArray)
                    }
                    responseJson = backup.toString()
                }

                // 16. Restore from JSON Database Backup
                path == "/api/restore" && method == "POST" -> {
                    val json = JSONObject(body)
                    if (json.has("tasks")) {
                        val tArray = json.getJSONArray("tasks")
                        taskDao.clearAllTasks()
                        for (i in 0 until tArray.length()) {
                            val obj = tArray.getJSONObject(i)
                            taskDao.insertTask(
                                Task(
                                    title = obj.getString("title"),
                                    description = obj.optString("description", ""),
                                    category = obj.optString("category", "Habit"),
                                    priority = obj.optInt("priority", 2),
                                    scheduledTime = com.discipline.os.util.TimeHelper.normalizeTo24Hour(obj.optString("scheduledTime", "")),
                                    ringSound = obj.optBoolean("ringSound", false),
                                    isCompleted = obj.optBoolean("isCompleted", false),
                                    subtasksJson = obj.optString("subtasksJson", "[]")
                                )
                            )
                        }
                    }
                    if (json.has("videos")) {
                        val vArray = json.getJSONArray("videos")
                        videoDao.clearAllVideos()
                        for (i in 0 until vArray.length()) {
                            val obj = vArray.getJSONObject(i)
                            videoDao.insertVideo(
                                VideoEntry(
                                    title = obj.getString("title"),
                                    url = obj.getString("url"),
                                    category = obj.optString("category", "Coding"),
                                    reminderEpochMs = obj.optLong("reminderEpochMs", 0L),
                                    reminderDelayText = obj.optString("reminderDelayText", ""),
                                    reminderType = obj.optString("reminderType", "RAPID_VIBRATE"),
                                    isWatched = obj.optBoolean("isWatched", false),
                                    notes = obj.optString("notes", "")
                                )
                            )
                        }
                    }
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("message", "Database successfully restored from backup")
                    }.toString()
                }

                // 17. Manual Day Reset
                path == "/api/reset" && method == "POST" -> {
                    taskDao.resetAllTasks()
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("message", "All tasks reset for new day")
                    }.toString()
                }

                // 17.5 Deduplicate & Clean Database
                (path == "/api/deduplicate" || path == "/api/clean") && method == "POST" -> {
                    val count = AppDatabase.deduplicateDatabase(db)
                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("purgedCount", count)
                        put("message", "Database deduplication complete. Removed $count duplicates.")
                    }.toString()
                }

                // 18. Trigger Vibration / Alarm Test
                path == "/api/test-alarm" && method == "POST" -> {
                    com.discipline.os.alarm.VibrationHelper.triggerRapidVibration(context)
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val builder = androidx.core.app.NotificationCompat.Builder(context, com.discipline.os.alarm.AlarmReceiver.CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("⚡ DISCIPLINE ALERT: High-Priority Habit")
                        .setContentText("No excuses today. Your yesterday's self is watching.")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                        .setAutoCancel(true)
                        .setVibrate(com.discipline.os.alarm.VibrationHelper.RAPID_PULSE_TIMINGS)
                    nm.notify(999, builder.build())

                    responseJson = JSONObject().apply {
                        put("success", true)
                        put("message", "Triggered rapid vibration and notification")
                    }.toString()
                }

                // 18.5 Push / Notify Live Update to Active App Screen
                (path == "/api/update/notify" || path == "/api/update/trigger") && method == "POST" -> {
                    try {
                        val json = if (body.isNotBlank()) JSONObject(body) else JSONObject()
                        val vCode = json.optLong("versionCode", 0L)
                        val vName = json.optString("versionName", "")
                        val title = json.optString("title", "DisciplineOS Update")
                        val apkUrl = json.optString("apkUrl", "")
                        val notes = mutableListOf<String>()
                        json.optJSONArray("releaseNotes")?.let { arr ->
                            for (i in 0 until arr.length()) notes.add(arr.getString(i))
                        }
                        val fileSizeBytes = json.optLong("fileSizeBytes", 0L)

                        val info = if (vCode > 0) {
                            com.discipline.os.update.UpdateInfo(
                                versionCode = vCode,
                                versionName = vName,
                                title = title,
                                apkUrl = apkUrl,
                                releaseNotes = notes,
                                fileSizeBytes = fileSizeBytes
                            )
                        } else {
                            com.discipline.os.update.UpdateManager.checkForUpdate(context, forceCheck = true).getOrNull()
                        }

                        if (info != null) {
                            com.discipline.os.update.UpdateManager.liveUpdateNotificationFlow.tryEmit(info)
                            responseJson = JSONObject().apply {
                                put("success", true)
                                put("message", "Live update popup triggered on screen")
                                put("versionCode", info.versionCode)
                                put("versionName", info.versionName)
                            }.toString()
                        } else {
                            statusCode = 404
                            responseJson = "{\"error\": \"No valid update info found\"}"
                        }
                    } catch (e: Exception) {
                        statusCode = 400
                        responseJson = JSONObject().apply {
                            put("success", false)
                            put("error", e.message ?: "Failed to trigger update")
                        }.toString()
                    }
                }

                // 19. Universal Agent Command Dispatcher (Single Unified AI Control Endpoint)
                (path == "/api/command" || path == "/api/exec") && method == "POST" -> {
                    try {
                        val json = JSONObject(body)
                        val action = json.optString("action", "").uppercase()
                        val params = json.optJSONObject("params") ?: JSONObject()
                        responseJson = executeUniversalCommand(context, db, action, params).toString()
                    } catch (e: Exception) {
                        statusCode = 400
                        responseJson = JSONObject().apply {
                            put("success", false)
                            put("error", e.message ?: "Invalid JSON or command")
                        }.toString()
                    }
                }

                else -> {
                    statusCode = 404
                    responseJson = "{\"error\": \"Not Found: $method $path\"}"
                }
            }

            val responseBytes = responseJson.toByteArray(Charsets.UTF_8)
            writer.write("HTTP/1.1 $statusCode OK\r\n")
            writer.write("Content-Type: application/json; charset=utf-8\r\n")
            writer.write("Content-Length: ${responseBytes.size}\r\n")
            writer.write("Access-Control-Allow-Origin: *\r\n")
            writer.write("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n")
            writer.write("Access-Control-Allow-Headers: Content-Type\r\n")
            writer.write("\r\n")
            writer.flush()
            socket.getOutputStream().write(responseBytes)
            socket.getOutputStream().flush()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val err = JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Internal Server Error")
                }.toString().toByteArray(Charsets.UTF_8)
                val w = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                w.write("HTTP/1.1 500 Internal Server Error\r\n")
                w.write("Content-Type: application/json; charset=utf-8\r\n")
                w.write("Content-Length: ${err.size}\r\n")
                w.write("Access-Control-Allow-Origin: *\r\n\r\n")
                w.flush()
                socket.getOutputStream().write(err)
                socket.getOutputStream().flush()
            } catch (_: Exception) {}
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (!url.contains("?")) return params
        val queryString = url.substringAfter("?")
        for (pair in queryString.split("&")) {
            val parts = pair.split("=")
            if (parts.size == 2) {
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = URLDecoder.decode(parts[1], "UTF-8")
                params[key] = value
            }
        }
        return params
    }

    private fun taskToJson(t: Task): JSONObject {
        return JSONObject().apply {
            put("id", t.id)
            put("title", t.title)
            put("description", t.description)
            put("category", t.category)
            put("priority", t.priority)
            put("priorityLabel", when(t.priority) { 1 -> "P1 🔥 Urgent"; 2 -> "P2 High"; 3 -> "P3 Medium"; else -> "P4 Low" })
            put("isCompleted", t.isCompleted)
            put("scheduledTime", t.scheduledTime)
            put("displayTime", com.discipline.os.util.TimeHelper.formatDisplayTime(t.scheduledTime))
            put("periodTag", com.discipline.os.util.TimeHelper.getPeriodTag(t.scheduledTime))
            put("periodEmoji", com.discipline.os.util.TimeHelper.getPeriodEmoji(t.scheduledTime))
            put("ringSound", t.ringSound)
            put("subtasks", JSONArray(t.subtasksJson))
            put("estimatedMinutes", t.estimatedMinutes)
            put("createdAt", t.createdAt)
        }
    }

    private fun fuelToJson(f: FuelEntry): JSONObject {
        return JSONObject().apply {
            put("id", f.id)
            put("personOrIncident", f.personOrIncident)
            put("defianceVow", f.defianceVow)
            put("category", f.category)
            put("timestamp", f.timestamp)
        }
    }

    private fun videoToJson(v: VideoEntry): JSONObject {
        return JSONObject().apply {
            put("id", v.id)
            put("title", v.title)
            put("url", v.url)
            put("category", v.category)
            put("reminderEpochMs", v.reminderEpochMs)
            put("reminderDelayText", v.reminderDelayText)
            put("reminderType", v.reminderType)
            put("isWatched", v.isWatched)
            put("notes", v.notes)
            put("createdAt", v.createdAt)
        }
    }

    private suspend fun executeUniversalCommand(context: Context, db: AppDatabase, action: String, params: JSONObject): JSONObject {
        val taskDao = db.taskDao()
        val fuelDao = db.fuelDao()
        val videoDao = db.videoDao()
        val result = JSONObject()

        try {
            when (action) {
                "SET_ALARM" -> {
                    val id = if (params.has("id")) params.optLong("id") else params.optLong("taskId", -1L)
                    val rawTime = if (params.has("time")) params.optString("time") else params.optString("scheduledTime", "")
                    val time = com.discipline.os.util.TimeHelper.normalizeTo24Hour(rawTime)
                    val ringSound = params.optBoolean("ringSound", false)
                    val task = taskDao.getTaskById(id)
                    if (task != null) {
                        val updated = task.copy(scheduledTime = time, ringSound = ringSound)
                        taskDao.updateTask(updated)
                        com.discipline.os.alarm.AlarmScheduler.scheduleTaskAlarm(context, updated)
                        val displayTime = com.discipline.os.util.TimeHelper.formatDisplayTime(time)
                        result.put("success", true)
                        result.put("message", "Alarm set for '${task.title}' at $displayTime (Sound: $ringSound)")
                        result.put("task", taskToJson(updated))
                    } else {
                        result.put("success", false)
                        result.put("error", "Task #$id not found")
                    }
                }

                "CANCEL_ALARM" -> {
                    val id = if (params.has("id")) params.optLong("id") else params.optLong("taskId", -1L)
                    val task = taskDao.getTaskById(id)
                    if (task != null) {
                        val updated = task.copy(scheduledTime = "")
                        taskDao.updateTask(updated)
                        com.discipline.os.alarm.AlarmScheduler.cancelTaskAlarm(context, task)
                        result.put("success", true)
                        result.put("message", "Alarm cancelled for '${task.title}'")
                    } else {
                        result.put("success", false)
                        result.put("error", "Task #$id not found")
                    }
                }

                "ADD_TASK" -> {
                    val title = params.optString("title", "New Task")
                    val desc = params.optString("description", "")
                    val category = params.optString("category", "Coding")
                    val priority = params.optInt("priority", 2)
                    val rawTime = if (params.has("scheduledTime")) params.optString("scheduledTime") else params.optString("time", "")
                    val time = com.discipline.os.util.TimeHelper.normalizeTo24Hour(rawTime)
                    val ringSound = params.optBoolean("ringSound", false)
                    val subtasks = params.optJSONArray("subtasks")?.toString() ?: "[]"
                    val newTask = Task(
                        title = title,
                        description = desc,
                        category = category,
                        priority = priority,
                        scheduledTime = time,
                        ringSound = ringSound,
                        subtasksJson = subtasks
                    )
                    val id = taskDao.insertTask(newTask)
                    val saved = newTask.copy(id = id)
                    if (time.isNotBlank()) {
                        com.discipline.os.alarm.AlarmScheduler.scheduleTaskAlarm(context, saved)
                    }
                    val displayTime = if (time.isNotBlank()) com.discipline.os.util.TimeHelper.formatDisplayTime(time) else "unscheduled"
                    result.put("success", true)
                    result.put("id", id)
                    result.put("message", "Added task #$id '$title' scheduled for $displayTime")
                    result.put("task", taskToJson(saved))
                }

                "UPDATE_TASK", "EDIT_TASK" -> {
                    val id = if (params.has("id")) params.optLong("id") else params.optLong("taskId", -1L)
                    val existing = taskDao.getTaskById(id)
                    if (existing != null) {
                        val rawTime = if (params.has("scheduledTime")) params.optString("scheduledTime") else if (params.has("time")) params.optString("time") else existing.scheduledTime
                        val time = com.discipline.os.util.TimeHelper.normalizeTo24Hour(rawTime)
                        val updated = existing.copy(
                            title = params.optString("title", existing.title),
                            description = params.optString("description", existing.description),
                            category = params.optString("category", existing.category),
                            priority = params.optInt("priority", existing.priority),
                            scheduledTime = time,
                            ringSound = if (params.has("ringSound")) params.getBoolean("ringSound") else existing.ringSound,
                            isCompleted = if (params.has("isCompleted")) params.getBoolean("isCompleted") else existing.isCompleted,
                            subtasksJson = params.optJSONArray("subtasks")?.toString() ?: params.optString("subtasksJson", existing.subtasksJson)
                        )
                        taskDao.updateTask(updated)
                        if (updated.scheduledTime.isNotBlank()) {
                            com.discipline.os.alarm.AlarmScheduler.scheduleTaskAlarm(context, updated)
                        }
                        result.put("success", true)
                        result.put("message", "Task #$id updated successfully")
                        result.put("task", taskToJson(updated))
                    } else {
                        result.put("success", false)
                        result.put("error", "Task #$id not found")
                    }
                }

                "DELETE_TASK", "REMOVE_TASK" -> {
                    val id = if (params.has("id")) params.optLong("id") else params.optLong("taskId", -1L)
                    val task = taskDao.getTaskById(id)
                    if (task != null) {
                        taskDao.deleteTaskById(id)
                        com.discipline.os.alarm.AlarmScheduler.cancelTaskAlarm(context, task)
                        result.put("success", true)
                        result.put("message", "Task #$id '${task.title}' deleted")
                    } else {
                        result.put("success", false)
                        result.put("error", "Task #$id not found")
                    }
                }

                "TOGGLE_TASK" -> {
                    val id = if (params.has("id")) params.optLong("id") else params.optLong("taskId", -1L)
                    val task = taskDao.getTaskById(id)
                    if (task != null) {
                        val completed = if (params.has("completed")) params.getBoolean("completed") else !task.isCompleted
                        taskDao.setTaskCompleted(id, completed)
                        if (completed) {
                            com.discipline.os.alarm.VibrationHelper.triggerRapidVibration(context)
                        }
                        result.put("success", true)
                        result.put("id", id)
                        result.put("completed", completed)
                        result.put("message", "Task #$id marked as " + if (completed) "COMPLETED" else "TODO")
                    } else {
                        result.put("success", false)
                        result.put("error", "Task #$id not found")
                    }
                }

                "COMPLETE_ALL" -> {
                    val priority = params.optInt("priority", 0)
                    val category = params.optString("category", "")
                    val tasks = taskDao.getAllTasksSync()
                    var count = 0
                    for (t in tasks) {
                        var match = true
                        if (priority > 0 && t.priority != priority) match = false
                        if (category.isNotBlank() && !t.category.equals(category, ignoreCase = true)) match = false
                        if (match && !t.isCompleted) {
                            taskDao.setTaskCompleted(t.id, true)
                            count++
                        }
                    }
                    com.discipline.os.alarm.VibrationHelper.triggerRapidVibration(context)
                    result.put("success", true)
                    result.put("completed_count", count)
                    result.put("message", "Marked $count tasks completed")
                }

                "RESET_ALL" -> {
                    taskDao.resetAllTasks()
                    result.put("success", true)
                    result.put("message", "All tasks reset for today")
                }

                "ADD_SUBTASK" -> {
                    val taskId = if (params.has("taskId")) params.optLong("taskId") else params.optLong("id", -1L)
                    val text = if (params.has("text")) params.optString("text") else params.optString("subtask", "")
                    val task = taskDao.getTaskById(taskId)
                    if (task != null) {
                        val arr = JSONArray(task.subtasksJson)
                        arr.put(JSONObject().apply {
                            put("text", text)
                            put("done", false)
                        })
                        taskDao.updateSubtasks(taskId, arr.toString())
                        result.put("success", true)
                        result.put("subtasks", arr)
                        result.put("message", "Subtask '$text' added to Task #$taskId")
                    } else {
                        result.put("success", false)
                        result.put("error", "Task #$taskId not found")
                    }
                }

                "TOGGLE_SUBTASK" -> {
                    val taskId = if (params.has("taskId")) params.optLong("taskId") else params.optLong("id", -1L)
                    val idx = if (params.has("subtaskIndex")) params.optInt("subtaskIndex") else if (params.has("subtask_index")) params.optInt("subtask_index") else params.optInt("index", 0)
                    val task = taskDao.getTaskById(taskId)
                    if (task != null) {
                        val arr = JSONArray(task.subtasksJson)
                        if (idx in 0 until arr.length()) {
                            val sub = arr.getJSONObject(idx)
                            val done = !sub.optBoolean("done", false)
                            sub.put("done", done)
                            taskDao.updateSubtasks(taskId, arr.toString())
                            if (done) com.discipline.os.alarm.VibrationHelper.triggerRapidVibration(context)
                            result.put("success", true)
                            result.put("subtasks", arr)
                            result.put("message", "Subtask #$idx toggled to " + if (done) "DONE" else "TODO")
                        } else {
                            result.put("success", false)
                            result.put("error", "Subtask index out of bounds")
                        }
                    } else {
                        result.put("success", false)
                        result.put("error", "Task #$taskId not found")
                    }
                }

                "DELETE_SUBTASK" -> {
                    val taskId = if (params.has("taskId")) params.optLong("taskId") else params.optLong("id", -1L)
                    val idx = if (params.has("subtaskIndex")) params.optInt("subtaskIndex") else if (params.has("subtask_index")) params.optInt("subtask_index") else params.optInt("index", 0)
                    val task = taskDao.getTaskById(taskId)
                    if (task != null) {
                        val arr = JSONArray(task.subtasksJson)
                        if (idx in 0 until arr.length()) {
                            arr.remove(idx)
                            taskDao.updateSubtasks(taskId, arr.toString())
                            result.put("success", true)
                            result.put("subtasks", arr)
                            result.put("message", "Subtask #$idx removed from Task #$taskId")
                        } else {
                            result.put("success", false)
                            result.put("error", "Subtask index out of bounds")
                        }
                    } else {
                        result.put("success", false)
                        result.put("error", "Task #$taskId not found")
                    }
                }

                "ADD_VIDEO" -> {
                    val title = params.optString("title", "Study Video")
                    val url = if (params.has("url")) params.optString("url") else params.optString("youtubeUrl", "")
                    val cat = params.optString("category", "Coding")
                    val preset = if (params.has("reminderPreset")) params.optString("reminderPreset") else params.optString("preset", "")
                    var remMins = params.optInt("reminderMinutes", 0)
                    if (remMins == 0 && preset.isNotBlank()) {
                        remMins = when (preset.uppercase()) {
                            "IN_10_MINS", "10M" -> 10
                            "IN_30_MINS", "30M" -> 30
                            "IN_1_HOUR", "1H" -> 60
                            "IN_2_HOURS", "2H" -> 120
                            "TONIGHT" -> 240
                            else -> 0
                        }
                    }
                    val remEpoch = if (remMins > 0) System.currentTimeMillis() + remMins * 60 * 1000L else params.optLong("reminderEpochMs", 0L)
                    val delayText = if (remMins > 0) "In ${remMins}m" else params.optString("reminderDelayText", "")
                    val remType = if (params.has("reminderType")) params.optString("reminderType") else params.optString("alertType", "RAPID_VIBRATE")
                    val notes = params.optString("notes", "")
                    val video = VideoEntry(
                        title = title,
                        url = url,
                        category = cat,
                        reminderEpochMs = remEpoch,
                        reminderDelayText = delayText,
                        reminderType = remType,
                        notes = notes
                    )
                    val id = videoDao.insertVideo(video)
                    val saved = video.copy(id = id)
                    if (remEpoch > System.currentTimeMillis()) {
                        com.discipline.os.alarm.AlarmScheduler.scheduleVideoReminder(context, saved)
                    }
                    result.put("success", true)
                    result.put("id", id)
                    result.put("message", "Video #$id '$title' queued with $remType reminder")
                    result.put("video", videoToJson(saved))
                }

                "DELETE_VIDEO" -> {
                    val id = if (params.has("id")) params.optLong("id") else params.optLong("videoId", -1L)
                    videoDao.deleteVideoById(id)
                    com.discipline.os.alarm.AlarmScheduler.cancelVideoReminder(context, id)
                    result.put("success", true)
                    result.put("message", "Video #$id removed")
                }

                "TOGGLE_VIDEO" -> {
                    val id = if (params.has("id")) params.optLong("id") else params.optLong("videoId", -1L)
                    val watched = if (params.has("watched")) params.optBoolean("watched") else true
                    videoDao.setVideoWatched(id, watched)
                    if (watched) com.discipline.os.alarm.VibrationHelper.triggerRapidVibration(context)
                    result.put("success", true)
                    result.put("message", "Video #$id marked as " + if (watched) "WATCHED" else "UNWATCHED")
                }

                "ADD_FUEL" -> {
                    val person = if (params.has("personOrIncident")) params.optString("personOrIncident")
                                 else if (params.has("personName")) params.optString("personName")
                                 else params.optString("person", "Doubter")
                    val vow = if (params.has("defianceVow")) params.optString("defianceVow")
                              else if (params.has("words")) params.optString("words")
                              else params.optString("vow", "I will be better and prove them wrong.")
                    val cat = params.optString("category", "Doubter")
                    val id = fuelDao.insertFuel(FuelEntry(personOrIncident = person, defianceVow = vow, category = cat))
                    result.put("success", true)
                    result.put("id", id)
                    result.put("message", "Fuel #$id added to Prove Them Wrong board")
                }

                "DELETE_FUEL" -> {
                    val id = if (params.has("id")) params.optLong("id") else params.optLong("fuelId", -1L)
                    fuelDao.deleteFuelById(id)
                    result.put("success", true)
                    result.put("message", "Fuel #$id removed")
                }

                "TRIGGER_VIBRATION" -> {
                    com.discipline.os.alarm.VibrationHelper.triggerRapidVibration(context)
                    result.put("success", true)
                    result.put("message", "Triggered high-potential rapid vibration burst")
                }

                "TRIGGER_ALARM" -> {
                    val title = params.optString("title", "High-Priority Discipline Alarm")
                    val sound = params.optBoolean("sound", false)
                    com.discipline.os.alarm.VibrationHelper.triggerRapidVibration(context)
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val builder = androidx.core.app.NotificationCompat.Builder(context, com.discipline.os.alarm.AlarmReceiver.CHANNEL_ID)
                        .setSmallIcon(com.discipline.os.R.drawable.ic_stat_discipline)
                        .setColor(0xFF8B5CF6.toInt())
                        .setContentTitle("⚡ Focus Check: $title")
                        .setContentText("Focus check! No excuses today.")
                        .setSubText("DisciplineOS • P1 Urgent")
                        .setStyle(
                            androidx.core.app.NotificationCompat.BigTextStyle()
                                .setBigContentTitle("⚡ $title")
                                .setSummaryText("P1 Critical Habit")
                                .bigText("Focus check! No excuses today.\n\n\"Your yesterday's self is watching. Put in the work.\"")
                        )
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                        .setAutoCancel(true)
                        .setVibrate(com.discipline.os.alarm.VibrationHelper.RAPID_PULSE_TIMINGS)
                    if (sound) {
                        builder.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
                    }
                    nm.notify(998, builder.build())
                    result.put("success", true)
                    result.put("message", "Triggered alarm alert for '$title'")
                }

                "FULL_STATE" -> {
                    val tasks = taskDao.getAllTasksSync()
                    val fuel = fuelDao.getAllFuelSync()
                    val videos = videoDao.getAllVideosSync()
                    val tArr = JSONArray()
                    tasks.forEach { tArr.put(taskToJson(it)) }
                    val fArr = JSONArray()
                    fuel.forEach { fArr.put(fuelToJson(it)) }
                    val vArr = JSONArray()
                    videos.forEach { vArr.put(videoToJson(it)) }

                    result.put("success", true)
                    result.put("tasks", tArr)
                    result.put("fuel", fArr)
                    result.put("videos", vArr)
                }

                "BATCH" -> {
                    val list = params.getJSONArray("commands")
                    val resultsArr = JSONArray()
                    for (i in 0 until list.length()) {
                        val subCmd = list.getJSONObject(i)
                        val subAction = subCmd.getString("action").uppercase()
                        val subParams = subCmd.optJSONObject("params") ?: JSONObject()
                        resultsArr.put(executeUniversalCommand(context, db, subAction, subParams))
                    }
                    result.put("success", true)
                    result.put("results", resultsArr)
                }

                else -> {
                    result.put("success", false)
                    result.put("error", "Unknown action: $action")
                }
            }
        } catch (e: Exception) {
            result.put("success", false)
            result.put("error", e.message ?: "Execution failed")
        }

        return result
    }
}
