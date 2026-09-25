package com.discipline.os.agent

import android.content.Context
import com.discipline.os.alarm.AlarmScheduler
import com.discipline.os.alarm.VibrationHelper
import com.discipline.os.data.*
import com.discipline.os.util.TimeHelper
import org.json.JSONArray
import org.json.JSONObject

data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val summary: String,
    val outputJson: String
)

object AgentTools {

    /**
     * Standard OpenAI-compatible tools definition list for API tool calling.
     */
    val toolsJsonArray: JSONArray by lazy {
        JSONArray().apply {
            put(buildToolObj(
                name = "get_protocols",
                description = "Get list of all habits/protocols for today, including their scheduled times, categories, priorities, and completion status."
            ))

            put(buildToolObj(
                name = "add_protocol",
                description = "Add a new daily habit or protocol to the routine.",
                properties = JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Title of the protocol (e.g. 'Coding for 1 hour', 'Do 10 push-ups')")
                    })
                    put("scheduledTime", JSONObject().apply {
                        put("type", "string")
                        put("description", "Scheduled time in 24h format (e.g. '07:30', '17:27', '20:27') or leave blank for anytime")
                    })
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "Category name (e.g. 'Coding', 'College', 'Health', 'Security', 'Language', 'Bedtime')")
                    })
                    put("priority", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Priority from 1 (Critical / Non-negotiable) to 3 (Low)")
                    })
                    put("ringSound", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "Whether to ring audio alarm (false = silent rapid vibration only)")
                    })
                },
                required = listOf("title")
            ))

            put(buildToolObj(
                name = "toggle_protocol",
                description = "Mark a protocol as completed or uncompleted. Triggers high-potential rapid vibration when marked complete.",
                properties = JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Title keyword or task ID to identify the habit")
                    })
                    put("completed", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "True to mark completed, false to mark uncompleted")
                    })
                },
                required = listOf("query", "completed")
            ))

            put(buildToolObj(
                name = "update_protocol_time",
                description = "Change the scheduled time of an existing habit and update its hardware alarm.",
                properties = JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Title keyword or ID of the protocol")
                    })
                    put("newTime", JSONObject().apply {
                        put("type", "string")
                        put("description", "New time in 24h format (e.g. '08:00', '21:30') or empty to clear")
                    })
                },
                required = listOf("query", "newTime")
            ))

            put(buildToolObj(
                name = "delete_protocol",
                description = "Remove a protocol from the routine.",
                properties = JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Title keyword or ID of the protocol to delete")
                    })
                },
                required = listOf("query")
            ))

            put(buildToolObj(
                name = "add_fuel",
                description = "Record a doubter incident or painful insult and a defiance vow into the 'Prove Them Wrong' fuel vault.",
                properties = JSONObject().apply {
                    put("personOrIncident", JSONObject().apply {
                        put("type", "string")
                        put("description", "Who doubted you or what happened (e.g. 'The teacher who embarrassed me in class')")
                    })
                    put("defianceVow", JSONObject().apply {
                        put("type", "string")
                        put("description", "Your fierce vow of defiance (e.g. 'I will be better than him. 1% every day.')")
                    })
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "Category tag (e.g. 'Teacher', 'Doubter', 'Competition', 'Personal')")
                    })
                },
                required = listOf("personOrIncident", "defianceVow")
            ))

            put(buildToolObj(
                name = "get_fuel",
                description = "Read the latest defiance fuel entries from the Prove Them Wrong vault."
            ))

            put(buildToolObj(
                name = "add_video",
                description = "Save an educational/high-value YouTube or study video to the Vault with an optional alert reminder.",
                properties = JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Video title")
                    })
                    put("url", JSONObject().apply {
                        put("type", "string")
                        put("description", "Video URL")
                    })
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "Category (Coding, Security, Mindset, etc.)")
                    })
                    put("reminderTime", JSONObject().apply {
                        put("type", "string")
                        put("description", "Friendly time for reminder (e.g. 'Tomorrow 07:30', 'Tonight 20:27')")
                    })
                },
                required = listOf("title", "url")
            ))

            put(buildToolObj(
                name = "get_history",
                description = "View recorded past days, completion percentages, and consistency audit."
            ))

            put(buildToolObj(
                name = "trigger_vibration",
                description = "Trigger a hardware silent rapid vibration pulse on the device immediately."
            ))

            put(buildToolObj(
                name = "reset_today",
                description = "Uncheck all completed protocols for today so the user can restart their daily routine."
            ))
        }
    }

    private fun buildToolObj(
        name: String,
        description: String,
        properties: JSONObject = JSONObject(),
        required: List<String> = emptyList()
    ): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", properties)
                    if (required.isNotEmpty()) {
                        put("required", JSONArray(required))
                    }
                })
            })
        }
    }

    /**
     * Executes the requested tool against the local Room SQLite Database and hardware engines.
     */
    suspend fun executeTool(
        context: Context,
        db: AppDatabase,
        toolName: String,
        args: JSONObject
    ): ToolExecutionResult {
        val taskDao = db.taskDao()
        val fuelDao = db.fuelDao()
        val videoDao = db.videoDao()
        val dailyLogDao = db.dailyLogDao()

        return try {
            when (toolName) {
                "get_protocols" -> {
                    val tasks = taskDao.getAllTasksSync()
                    val arr = JSONArray()
                    var completedCount = 0
                    for (t in tasks) {
                        if (t.isCompleted) completedCount++
                        arr.put(JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("time", if (t.scheduledTime.isNotBlank()) t.scheduledTime else "Anytime")
                            put("category", t.category)
                            put("priority", "P${t.priority}")
                            put("completed", t.isCompleted)
                        })
                    }
                    val pct = if (tasks.isNotEmpty()) (completedCount * 100 / tasks.size) else 0
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Retrieved ${tasks.size} protocols ($completedCount completed, $pct%)",
                        outputJson = JSONObject().apply {
                            put("total", tasks.size)
                            put("completedCount", completedCount)
                            put("percentage", pct)
                            put("protocols", arr)
                        }.toString()
                    )
                }

                "add_protocol" -> {
                    val title = args.getString("title")
                    val rawTime = args.optString("scheduledTime", "")
                    val finalTime = TimeHelper.normalizeTo24Hour(rawTime)
                    val category = args.optString("category", "Habit")
                    val priority = args.optInt("priority", 2).coerceIn(1, 3)
                    val ringSound = args.optBoolean("ringSound", false)

                    val newTask = Task(
                        title = title,
                        description = args.optString("description", ""),
                        category = category,
                        priority = priority,
                        scheduledTime = finalTime,
                        ringSound = ringSound,
                        estimatedMinutes = 60
                    )
                    val newId = taskDao.insertTask(newTask)
                    if (finalTime.isNotBlank()) {
                        AlarmScheduler.scheduleTaskAlarm(context, newTask.copy(id = newId))
                    }
                    VibrationHelper.triggerRapidVibration(context)

                    val timeDesc = if (finalTime.isNotBlank()) "at $finalTime" else "anytime"
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Added protocol: \"$title\" ($timeDesc, P$priority)",
                        outputJson = JSONObject().apply {
                            put("success", true)
                            put("id", newId)
                            put("title", title)
                            put("scheduledTime", finalTime)
                        }.toString()
                    )
                }

                "toggle_protocol" -> {
                    val query = args.getString("query").trim()
                    val completed = args.getBoolean("completed")
                    val allTasks = taskDao.getAllTasksSync()

                    val target = allTasks.find {
                        it.id.toString() == query || it.title.contains(query, ignoreCase = true)
                    }

                    if (target != null) {
                        taskDao.setTaskCompleted(target.id, completed)
                        if (completed) {
                            VibrationHelper.triggerRapidVibration(context)
                        }
                        val statusText = if (completed) "COMPLETED ✓" else "UNCOMPLETED ⬜"
                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Marked \"${target.title}\" as $statusText",
                            outputJson = JSONObject().apply {
                                put("success", true)
                                put("id", target.id)
                                put("title", target.title)
                                put("isCompleted", completed)
                            }.toString()
                        )
                    } else {
                        ToolExecutionResult(
                            toolName = toolName,
                            success = false,
                            summary = "Protocol \"$query\" not found in routine",
                            outputJson = "{\"error\": \"Protocol not found matching query: $query\"}"
                        )
                    }
                }

                "update_protocol_time" -> {
                    val query = args.getString("query").trim()
                    val newTime = TimeHelper.normalizeTo24Hour(args.getString("newTime"))
                    val allTasks = taskDao.getAllTasksSync()
                    val target = allTasks.find {
                        it.id.toString() == query || it.title.contains(query, ignoreCase = true)
                    }

                    if (target != null) {
                        val updated = target.copy(scheduledTime = newTime)
                        taskDao.updateTask(updated)
                        if (newTime.isNotBlank()) {
                            AlarmScheduler.scheduleTaskAlarm(context, updated)
                        } else {
                            AlarmScheduler.cancelTaskAlarm(context, updated)
                        }
                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Rescheduled \"${target.title}\" to ${if (newTime.isNotBlank()) newTime else "Anytime"}",
                            outputJson = JSONObject().apply {
                                put("success", true)
                                put("id", target.id)
                                put("title", target.title)
                                put("newTime", newTime)
                            }.toString()
                        )
                    } else {
                        ToolExecutionResult(
                            toolName = toolName,
                            success = false,
                            summary = "Protocol \"$query\" not found",
                            outputJson = "{\"error\": \"Protocol not found matching query: $query\"}"
                        )
                    }
                }

                "delete_protocol" -> {
                    val query = args.getString("query").trim()
                    val allTasks = taskDao.getAllTasksSync()
                    val target = allTasks.find {
                        it.id.toString() == query || it.title.contains(query, ignoreCase = true)
                    }

                    if (target != null) {
                        AlarmScheduler.cancelTaskAlarm(context, target)
                        taskDao.deleteTask(target)
                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Deleted protocol: \"${target.title}\"",
                            outputJson = JSONObject().apply {
                                put("success", true)
                                put("deletedId", target.id)
                                put("title", target.title)
                            }.toString()
                        )
                    } else {
                        ToolExecutionResult(
                            toolName = toolName,
                            success = false,
                            summary = "Could not find protocol \"$query\" to delete",
                            outputJson = "{\"error\": \"Protocol not found: $query\"}"
                        )
                    }
                }

                "add_fuel" -> {
                    val person = args.getString("personOrIncident")
                    val vow = args.getString("defianceVow")
                    val category = args.optString("category", "Doubter")
                    val entry = FuelEntry(
                        personOrIncident = person,
                        defianceVow = vow,
                        category = category
                    )
                    val id = fuelDao.insertFuel(entry)
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Recorded defiance fuel: \"$vow\"",
                        outputJson = JSONObject().apply {
                            put("success", true)
                            put("id", id)
                            put("vow", vow)
                        }.toString()
                    )
                }

                "get_fuel" -> {
                    val list = fuelDao.getAllFuelSync()
                    val arr = JSONArray()
                    for (f in list.take(5)) {
                        arr.put(JSONObject().apply {
                            put("personOrIncident", f.personOrIncident)
                            put("defianceVow", f.defianceVow)
                            put("category", f.category)
                        })
                    }
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Retrieved ${list.size} fuel vows",
                        outputJson = JSONObject().apply {
                            put("totalCount", list.size)
                            put("entries", arr)
                        }.toString()
                    )
                }

                "add_video" -> {
                    val title = args.getString("title")
                    val url = args.getString("url")
                    val category = args.optString("category", "Coding")
                    val reminderTime = args.optString("reminderTime", "")

                    val video = VideoEntry(
                        title = title,
                        url = url,
                        category = category,
                        reminderDelayText = reminderTime,
                        reminderType = "RAPID_VIBRATE"
                    )
                    val id = videoDao.insertVideo(video)
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Saved video to Vault: \"$title\"",
                        outputJson = JSONObject().apply {
                            put("success", true)
                            put("id", id)
                            put("title", title)
                        }.toString()
                    )
                }

                "get_history" -> {
                    val logs = dailyLogDao.getAllLogsSync()
                    val arr = JSONArray()
                    for (l in logs.take(7)) {
                        arr.put(JSONObject().apply {
                            put("date", l.date)
                            put("score", "${l.completedCount}/${l.totalCount}")
                            put("percentage", l.percentage)
                        })
                    }
                    val avg = if (logs.isNotEmpty()) logs.map { it.percentage }.average().toInt() else 0
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "History: ${logs.size} recorded days (Average $avg%)",
                        outputJson = JSONObject().apply {
                            put("totalDays", logs.size)
                            put("averagePercentage", avg)
                            put("recentDays", arr)
                        }.toString()
                    )
                }

                "trigger_vibration" -> {
                    VibrationHelper.triggerRapidVibration(context)
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Triggered rapid hardware vibration burst",
                        outputJson = "{\"success\": true, \"message\": \"Vibration triggered\"}"
                    )
                }

                "reset_today" -> {
                    taskDao.resetAllTasks()
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Reset all protocols for today",
                        outputJson = "{\"success\": true, \"message\": \"Protocols reset\"}"
                    )
                }

                else -> {
                    ToolExecutionResult(
                        toolName = toolName,
                        success = false,
                        summary = "Unknown tool: $toolName",
                        outputJson = "{\"error\": \"Unrecognized tool name: $toolName\"}"
                    )
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = toolName,
                success = false,
                summary = "Error executing $toolName: ${e.localizedMessage}",
                outputJson = "{\"error\": \"${e.message}\"}"
            )
        }
    }
}
