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
                        put("description", "Who doubted you or what happened (e.g. 'A skeptic who doubted my ability')")
                    })
                    put("defianceVow", JSONObject().apply {
                        put("type", "string")
                        put("description", "Your fierce vow of defiance (e.g. 'The best revenge is massive compounding execution.')")
                    })
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "Category tag (e.g. 'Critic', 'Skeptic', 'Competition', 'Personal')")
                    })
                },
                required = listOf("personOrIncident", "defianceVow")
            ))

            put(buildToolObj(
                name = "get_fuel",
                description = "Read the latest defiance fuel entries from the Prove Them Wrong vault."
            ))

            put(buildToolObj(
                name = "delete_fuel",
                description = "Delete a specific fuel/vow entry or the last logged fuel from the Prove Them Wrong vault.",
                properties = JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Person/incident keyword, vow text, ID, or 'last' / 'it' to delete the most recent fuel")
                    })
                },
                required = listOf("query")
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

            put(buildToolObj(
                name = "get_roadmaps",
                description = "Get all active roadmap journeys (e.g. Calisthenics Journey, Coding Roadmap), their progress percentages, active stages, and current levels."
            ))

            put(buildToolObj(
                name = "get_roadmap_detail",
                description = "Get detailed stages, exercises/milestones, checklists, and active focus for a specific roadmap journey.",
                properties = JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Roadmap title keyword or ID (e.g. 'Calisthenics')")
                    })
                },
                required = listOf("query")
            ))

            put(buildToolObj(
                name = "update_roadmap_step",
                description = "Mark a roadmap milestone/exercise as completed or set it as the current active training level.",
                properties = JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Milestone title keyword or node ID (e.g. 'Pillar 1: PUSH', 'Wall Push-ups')")
                    })
                    put("completed", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "Whether the milestone is completed")
                    })
                    put("isCurrent", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "Set as the user's current active level")
                    })
                    put("repsOrCriteria", JSONObject().apply {
                        put("type", "string")
                        put("description", "Updated criteria or personal record (e.g. '3 sets of 15 clean reps')")
                    })
                },
                required = listOf("query")
            ))

            put(buildToolObj(
                name = "toggle_roadmap_checklist",
                description = "Toggle a specific checklist item in a roadmap step (e.g. check off Wall Push-ups 3x15).",
                properties = JSONObject().apply {
                    put("nodeQuery", JSONObject().apply {
                        put("type", "string")
                        put("description", "Node title keyword or ID")
                    })
                    put("checklistItemIndex", JSONObject().apply {
                        put("type", "integer")
                        put("description", "0-based index of the checklist item")
                    })
                    put("done", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "True if completed, false if pending")
                    })
                },
                required = listOf("nodeQuery", "checklistItemIndex", "done")
            ))

            put(buildToolObj(
                name = "add_roadmap",
                description = "Create a new custom roadmap journey in the database.",
                properties = JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Roadmap title (e.g. 'Cybersecurity Hacker Path')")
                    })
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "Category (e.g. 'Fitness', 'Coding', 'Security', 'Life')")
                    })
                    put("description", JSONObject().apply {
                        put("type", "string")
                        put("description", "Description and target outcome")
                    })
                    put("targetGoal", JSONObject().apply {
                        put("type", "string")
                        put("description", "Ultimate end goal to achieve")
                    })
                },
                required = listOf("title")
            ))

            put(buildToolObj(
                name = "add_roadmap_step",
                description = "Add a new milestone, phase, or exercise progression to an existing roadmap.",
                properties = JSONObject().apply {
                    put("roadmapQuery", JSONObject().apply {
                        put("type", "string")
                        put("description", "Target roadmap title keyword or ID")
                    })
                    put("stage", JSONObject().apply {
                        put("type", "string")
                        put("description", "Phase name (e.g. 'Phase 1: Foundation', 'Phase 2: Intermediate')")
                    })
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Step title (e.g. 'Bar Muscle-Up Transition')")
                    })
                    put("description", JSONObject().apply {
                        put("type", "string")
                        put("description", "Instructions, cues, and requirements")
                    })
                    put("repsOrCriteria", JSONObject().apply {
                        put("type", "string")
                        put("description", "Target reps or qualification criteria")
                    })
                },
                required = listOf("roadmapQuery", "title")
            ))

            put(buildToolObj(
                name = "delete_roadmap_step",
                description = "Delete a milestone, exercise, or progression step from a roadmap.",
                properties = JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Milestone title, step keyword, or ID to delete")
                    })
                },
                required = listOf("query")
            ))

            put(buildToolObj(
                name = "delete_roadmap",
                description = "Delete an entire custom roadmap journey and all its steps.",
                properties = JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Roadmap title keyword or ID to delete")
                    })
                },
                required = listOf("query")
            ))

            put(buildToolObj(
                name = "create_full_roadmap",
                description = "Design, generate, and store a complete multi-step roadmap journey into the database.",
                properties = JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Roadmap title (e.g. 'Python Backend Architecture')")
                    })
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "Category (Coding, Fitness, Mindset, etc.)")
                    })
                    put("description", JSONObject().apply {
                        put("type", "string")
                        put("description", "Clear purpose describing what this roadmap is for")
                    })
                    put("targetGoal", JSONObject().apply {
                        put("type", "string")
                        put("description", "Target mastery goal")
                    })
                    put("stepsJson", JSONObject().apply {
                        put("type", "string")
                        put("description", "JSON array of steps, each with stage, title, description, repsOrCriteria")
                    })
                },
                required = listOf("title", "stepsJson")
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
        val roadmapDao = db.roadmapDao()

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
                    val query = args.optString("query", "").trim()
                    val allTasks = taskDao.getAllTasksSync()

                    val isRelativeOrPronoun = query.isBlank() ||
                        query.equals("it", ignoreCase = true) ||
                        query.equals("that", ignoreCase = true) ||
                        query.equals("this", ignoreCase = true) ||
                        query.equals("last", ignoreCase = true) ||
                        query.equals("now it", ignoreCase = true) ||
                        query.equals("now", ignoreCase = true) ||
                        query.contains("just added", ignoreCase = true) ||
                        query.contains("writ now", ignoreCase = true) ||
                        query.contains("right now", ignoreCase = true) ||
                        query.contains("the task", ignoreCase = true) ||
                        query.contains("added", ignoreCase = true)

                    val target = if (isRelativeOrPronoun) {
                        allTasks.maxByOrNull { it.id } ?: allTasks.lastOrNull()
                    } else {
                        allTasks.find {
                            it.id.toString() == query || it.title.contains(query, ignoreCase = true)
                        } ?: allTasks.find { t ->
                            val queryWords = query.lowercase().split("\\s+".toRegex())
                                .filter { it.length > 2 && it !in listOf("the", "task", "protocol", "now", "delete", "remove", "cancel", "just", "writ", "right", "added") }
                            queryWords.isNotEmpty() && queryWords.any { w -> t.title.contains(w, ignoreCase = true) }
                        } ?: if (query.contains("task", ignoreCase = true) || query.contains("last", ignoreCase = true)) {
                            allTasks.maxByOrNull { it.id }
                        } else null
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
                            outputJson = JSONObject().apply {
                                put("success", false)
                                put("error", "Protocol not found matching: $query")
                            }.toString()
                        )
                    }
                }

                "delete_fuel" -> {
                    val query = args.optString("query", "").trim()
                    val allFuel = fuelDao.getAllFuelSync()

                    val isRelative = query.isBlank() ||
                        query.equals("last", ignoreCase = true) ||
                        query.equals("it", ignoreCase = true) ||
                        query.equals("that", ignoreCase = true) ||
                        query.equals("this", ignoreCase = true) ||
                        query.contains("just added", ignoreCase = true)

                    val target = if (isRelative) {
                        allFuel.firstOrNull() // Ordered by timestamp DESC
                    } else {
                        allFuel.find {
                            it.id.toString() == query ||
                            it.personOrIncident.contains(query, ignoreCase = true) ||
                            it.defianceVow.contains(query, ignoreCase = true)
                        } ?: allFuel.find { f ->
                            val queryWords = query.lowercase().split("\\s+".toRegex())
                                .filter { it.length > 2 && it !in listOf("the", "fuel", "delete", "remove", "entry", "vow", "now") }
                            queryWords.isNotEmpty() && queryWords.any { w -> f.personOrIncident.contains(w, ignoreCase = true) }
                        }
                    }

                    if (target != null) {
                        fuelDao.deleteFuel(target)
                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Deleted fuel entry: \"${target.personOrIncident}\"",
                            outputJson = JSONObject().apply {
                                put("success", true)
                                put("deletedId", target.id)
                                put("personOrIncident", target.personOrIncident)
                            }.toString()
                        )
                    } else {
                        ToolExecutionResult(
                            toolName = toolName,
                            success = false,
                            summary = "Fuel entry matching \"$query\" not found in vault",
                            outputJson = JSONObject().apply {
                                put("success", false)
                                put("error", "Fuel entry not found matching: $query")
                            }.toString()
                        )
                    }
                }

                "add_fuel" -> {
                    val person = (args.optString("personOrIncident").takeIf { it.isNotBlank() }
                        ?: args.optString("description").takeIf { it.isNotBlank() }
                        ?: args.optString("incident").takeIf { it.isNotBlank() }
                        ?: "Doubter / Critic").trim()
                    val vow = (args.optString("defianceVow").takeIf { it.isNotBlank() }
                        ?: args.optString("vow").takeIf { it.isNotBlank() }
                        ?: "Transmute doubts into fuel. Let results shatter all doubts.").trim()
                    val category = args.optString("category", "Doubter").trim()
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
                            put("personOrIncident", person)
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

                "get_roadmaps" -> {
                    val roadmaps = roadmapDao.getAllRoadmapsSync()
                    val arr = JSONArray()
                    for (r in roadmaps) {
                        val nodes = roadmapDao.getNodesForRoadmapSync(r.id)
                        val completedCount = nodes.count { it.isCompleted }
                        val currentNode = nodes.find { it.isCurrent }
                        arr.put(JSONObject().apply {
                            put("id", r.id)
                            put("title", r.title)
                            put("category", r.category)
                            put("currentLevel", r.currentLevel)
                            put("activeFocus", currentNode?.title ?: "None")
                            put("progressPercentage", r.progressPercentage)
                            put("completedNodes", completedCount)
                            put("totalNodes", nodes.size)
                        })
                    }
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Retrieved ${roadmaps.size} roadmap journeys",
                        outputJson = JSONObject().apply {
                            put("totalRoadmaps", roadmaps.size)
                            put("roadmaps", arr)
                        }.toString()
                    )
                }

                "get_roadmap_detail" -> {
                    val query = args.optString("query", "").trim()
                    val allRoadmaps = roadmapDao.getAllRoadmapsSync()
                    val target = allRoadmaps.find {
                        it.id.toString() == query || it.title.contains(query, ignoreCase = true)
                    } ?: allRoadmaps.firstOrNull()

                    if (target == null) {
                        ToolExecutionResult(
                            toolName = toolName,
                            success = false,
                            summary = "No roadmap found matching '$query'",
                            outputJson = "{\"error\": \"Roadmap not found\"}"
                        )
                    } else {
                        val nodes = roadmapDao.getNodesForRoadmapSync(target.id)
                        val nodesArr = JSONArray()
                        for (n in nodes) {
                            nodesArr.put(JSONObject().apply {
                                put("id", n.id)
                                put("stage", n.stage)
                                put("stepOrder", n.stepOrder)
                                put("title", n.title)
                                put("repsOrCriteria", n.repsOrCriteria)
                                put("isCompleted", n.isCompleted)
                                put("isCurrent", n.isCurrent)
                                put("description", n.description)
                                put("checklist", try { JSONArray(n.checklistJson) } catch (_: Exception) { JSONArray() })
                            })
                        }
                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Roadmap '${target.title}': ${nodes.count { it.isCompleted }}/${nodes.size} completed (${target.progressPercentage.toInt()}%)",
                            outputJson = JSONObject().apply {
                                put("id", target.id)
                                put("title", target.title)
                                put("category", target.category)
                                put("targetGoal", target.targetGoal)
                                put("currentLevel", target.currentLevel)
                                put("progressPercentage", target.progressPercentage)
                                put("nodes", nodesArr)
                            }.toString()
                        )
                    }
                }

                "update_roadmap_step" -> {
                    val query = args.optString("query", "").trim()
                    val allNodes = roadmapDao.getAllNodesSync()
                    val target = allNodes.find {
                        it.id.toString() == query || it.title.contains(query, ignoreCase = true)
                    }

                    if (target == null) {
                        ToolExecutionResult(
                            toolName = toolName,
                            success = false,
                            summary = "Roadmap step not found matching '$query'",
                            outputJson = "{\"error\": \"Step not found\"}"
                        )
                    } else {
                        var updated = target
                        if (args.has("completed")) {
                            val comp = args.getBoolean("completed")
                            updated = updated.copy(isCompleted = comp)
                        }
                        if (args.has("isCurrent")) {
                            val curr = args.getBoolean("isCurrent")
                            if (curr) {
                                roadmapDao.clearCurrentForRoadmap(target.roadmapId)
                                updated = updated.copy(isCurrent = true)
                                val roadmap = roadmapDao.getRoadmapById(target.roadmapId)
                                if (roadmap != null) {
                                    roadmapDao.updateRoadmap(roadmap.copy(currentLevel = "${target.stage}: ${target.title}", updatedAt = System.currentTimeMillis()))
                                }
                            } else {
                                updated = updated.copy(isCurrent = false)
                            }
                        }
                        if (args.has("repsOrCriteria") && args.getString("repsOrCriteria").isNotBlank()) {
                            updated = updated.copy(repsOrCriteria = args.getString("repsOrCriteria").trim())
                        }
                        roadmapDao.updateNode(updated)

                        // Recalculate roadmap progress
                        val nodes = roadmapDao.getNodesForRoadmapSync(target.roadmapId)
                        val compCount = nodes.count { it.isCompleted }
                        val prog = if (nodes.isNotEmpty()) (compCount.toFloat() / nodes.size) * 100f else 0f
                        val roadmap = roadmapDao.getRoadmapById(target.roadmapId)
                        if (roadmap != null) {
                            roadmapDao.updateRoadmapProgress(roadmap.id, roadmap.currentLevel, prog)
                        }

                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Updated milestone '${updated.title}': completed=${updated.isCompleted}, current=${updated.isCurrent} (Roadmap progress: ${prog.toInt()}%)",
                            outputJson = JSONObject().apply {
                                put("stepId", updated.id)
                                put("title", updated.title)
                                put("isCompleted", updated.isCompleted)
                                put("isCurrent", updated.isCurrent)
                                put("repsOrCriteria", updated.repsOrCriteria)
                                put("roadmapProgress", prog)
                            }.toString()
                        )
                    }
                }

                "toggle_roadmap_checklist" -> {
                    val nodeQuery = args.optString("nodeQuery", "").trim()
                    val idx = args.optInt("checklistItemIndex", -1)
                    val done = args.optBoolean("done", false)
                    val allNodes = roadmapDao.getAllNodesSync()
                    val target = allNodes.find {
                        it.id.toString() == nodeQuery || it.title.contains(nodeQuery, ignoreCase = true)
                    }

                    if (target == null || idx < 0) {
                        ToolExecutionResult(
                            toolName = toolName,
                            success = false,
                            summary = "Could not find node or invalid index for checklist",
                            outputJson = "{\"error\": \"Invalid node or index\"}"
                        )
                    } else {
                        val arr = try { JSONArray(target.checklistJson) } catch (_: Exception) { JSONArray() }
                        if (idx < arr.length()) {
                            val obj = arr.getJSONObject(idx)
                            obj.put("done", done)
                            val itemText = obj.optString("text", "item")
                            val allDone = (0 until arr.length()).all { arr.getJSONObject(it).optBoolean("done", false) }
                            val updatedNode = target.copy(checklistJson = arr.toString(), isCompleted = if (allDone) true else target.isCompleted)
                            roadmapDao.updateNode(updatedNode)

                            // Recalculate progress
                            val nodes = roadmapDao.getNodesForRoadmapSync(target.roadmapId)
                            val compCount = nodes.count { it.isCompleted }
                            val prog = if (nodes.isNotEmpty()) (compCount.toFloat() / nodes.size) * 100f else 0f
                            val roadmap = roadmapDao.getRoadmapById(target.roadmapId)
                            if (roadmap != null) {
                                roadmapDao.updateRoadmapProgress(roadmap.id, roadmap.currentLevel, prog)
                            }

                            ToolExecutionResult(
                                toolName = toolName,
                                success = true,
                                summary = "Toggled '$itemText' to ${if (done) "done" else "pending"} in '${target.title}'",
                                outputJson = JSONObject().apply {
                                    put("itemText", itemText)
                                    put("done", done)
                                    put("nodeCompleted", updatedNode.isCompleted)
                                    put("roadmapProgress", prog)
                                }.toString()
                            )
                        } else {
                            ToolExecutionResult(
                                toolName = toolName,
                                success = false,
                                summary = "Index $idx out of bounds for node checklist",
                                outputJson = "{\"error\": \"Index out of bounds\"}"
                            )
                        }
                    }
                }

                "add_roadmap" -> {
                    val title = args.optString("title", "").trim()
                    val category = args.optString("category", "Fitness").trim()
                    val desc = args.optString("description", "").trim()
                    val targetGoal = args.optString("targetGoal", "").trim()
                    val newId = roadmapDao.insertRoadmap(
                        Roadmap(
                            title = title,
                            category = category,
                            description = desc,
                            targetGoal = targetGoal
                        )
                    )
                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Created new roadmap '$title' (ID $newId)",
                        outputJson = JSONObject().apply {
                            put("id", newId)
                            put("title", title)
                            put("category", category)
                        }.toString()
                    )
                }

                "add_roadmap_step" -> {
                    val rQuery = args.optString("roadmapQuery", "").trim()
                    val stage = args.optString("stage", "Phase 1").trim()
                    val title = args.optString("title", "").trim()
                    val desc = args.optString("description", "").trim()
                    val criteria = args.optString("repsOrCriteria", "").trim()
                    val allRoadmaps = roadmapDao.getAllRoadmapsSync()
                    val target = allRoadmaps.find {
                        it.id.toString() == rQuery || it.title.contains(rQuery, ignoreCase = true)
                    } ?: allRoadmaps.firstOrNull()

                    if (target == null) {
                        ToolExecutionResult(
                            toolName = toolName,
                            success = false,
                            summary = "Target roadmap '$rQuery' not found",
                            outputJson = "{\"error\": \"Roadmap not found\"}"
                        )
                    } else {
                        val existingNodes = roadmapDao.getNodesForRoadmapSync(target.id)
                        val nextOrder = existingNodes.size + 1
                        val nodeId = roadmapDao.insertNode(
                            RoadmapNode(
                                roadmapId = target.id,
                                stage = stage,
                                stepOrder = nextOrder,
                                title = title,
                                description = desc,
                                repsOrCriteria = criteria,
                                isCompleted = false,
                                isCurrent = false
                            )
                        )
                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Added milestone '$title' to roadmap '${target.title}'",
                            outputJson = JSONObject().apply {
                                put("id", nodeId)
                                put("roadmapId", target.id)
                                put("stage", stage)
                                put("title", title)
                            }.toString()
                        )
                    }
                }

                "delete_roadmap_step" -> {
                    val query = args.optString("query", "").trim()
                    val allNodes = roadmapDao.getAllNodesSync()

                    val isRelative = query.isBlank() ||
                        query.equals("last", ignoreCase = true) ||
                        query.equals("it", ignoreCase = true) ||
                        query.equals("that", ignoreCase = true)

                    val target = if (isRelative) {
                        allNodes.maxByOrNull { it.id }
                    } else {
                        allNodes.find {
                            it.id.toString() == query || it.title.contains(query, ignoreCase = true)
                        } ?: allNodes.find { n ->
                            val queryWords = query.lowercase().split("\\s+".toRegex())
                                .filter { it.length > 2 && it !in listOf("the", "step", "delete", "remove", "node", "milestone", "roadmap") }
                            queryWords.isNotEmpty() && queryWords.any { w -> n.title.contains(w, ignoreCase = true) }
                        }
                    }

                    if (target != null) {
                        roadmapDao.deleteNode(target)
                        val nodes = roadmapDao.getNodesForRoadmapSync(target.roadmapId)
                        val compCount = nodes.count { it.isCompleted }
                        val prog = if (nodes.isNotEmpty()) (compCount.toFloat() / nodes.size) * 100f else 0f
                        val roadmap = roadmapDao.getRoadmapById(target.roadmapId)
                        if (roadmap != null) {
                            roadmapDao.updateRoadmapProgress(roadmap.id, roadmap.currentLevel, prog)
                        }

                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Deleted milestone: \"${target.title}\"",
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
                            summary = "Milestone matching \"$query\" not found in roadmap",
                            outputJson = JSONObject().apply {
                                put("success", false)
                                put("error", "Milestone not found matching: $query")
                            }.toString()
                        )
                    }
                }

                "delete_roadmap" -> {
                    val query = args.optString("query", "").trim()
                    val allRoadmaps = roadmapDao.getAllRoadmapsSync()
                    val target = allRoadmaps.find {
                        it.id.toString() == query || it.title.contains(query, ignoreCase = true)
                    }

                    if (target != null) {
                        roadmapDao.deleteRoadmap(target)
                        ToolExecutionResult(
                            toolName = toolName,
                            success = true,
                            summary = "Deleted roadmap: \"${target.title}\"",
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
                            summary = "Roadmap matching \"$query\" not found",
                            outputJson = JSONObject().apply {
                                put("success", false)
                                put("error", "Roadmap not found matching: $query")
                            }.toString()
                        )
                    }
                }

                "create_full_roadmap" -> {
                    val title = args.optString("title", "Custom Roadmap").trim()
                    val category = args.optString("category", "General").trim()
                    val desc = args.optString("description", "").trim()
                    val targetGoal = args.optString("targetGoal", "").trim()
                    val stepsJson = args.optString("stepsJson", "[]").trim()

                    val newRoadmapId = roadmapDao.insertRoadmap(
                        Roadmap(
                            title = title,
                            category = category,
                            description = desc,
                            targetGoal = targetGoal
                        )
                    )

                    val stepsArray = try { JSONArray(stepsJson) } catch (_: Exception) { JSONArray() }
                    val nodesToInsert = mutableListOf<RoadmapNode>()
                    for (i in 0 until stepsArray.length()) {
                        val s = stepsArray.getJSONObject(i)
                        nodesToInsert.add(
                            RoadmapNode(
                                roadmapId = newRoadmapId,
                                stage = s.optString("stage", "Phase 1"),
                                stepOrder = i + 1,
                                title = s.optString("title", "Step ${i + 1}"),
                                description = s.optString("description", ""),
                                repsOrCriteria = s.optString("repsOrCriteria", s.optString("criteria", "")),
                                isCompleted = false,
                                isCurrent = (i == 0)
                            )
                        )
                    }

                    if (nodesToInsert.isNotEmpty()) {
                        roadmapDao.insertNodes(nodesToInsert)
                        roadmapDao.updateRoadmapProgress(
                            id = newRoadmapId,
                            currentLevel = "${nodesToInsert[0].stage}: ${nodesToInsert[0].title}",
                            progress = 0f
                        )
                    }

                    ToolExecutionResult(
                        toolName = toolName,
                        success = true,
                        summary = "Designed roadmap '$title' with ${nodesToInsert.size} progression steps",
                        outputJson = JSONObject().apply {
                            put("success", true)
                            put("roadmapId", newRoadmapId)
                            put("title", title)
                            put("category", category)
                            put("totalSteps", nodesToInsert.size)
                        }.toString()
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
