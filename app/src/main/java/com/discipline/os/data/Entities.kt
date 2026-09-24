package com.discipline.os.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "", // Rich markdown / notes (like Notion)
    val category: String = "Habit", // e.g. "Coding", "Health", "College", "Security"
    val priority: Int = 2, // 1 = P1 Urgent/Flame, 2 = P2 High, 3 = P3 Medium, 4 = P4 Low
    val estimatedMinutes: Int = 60,
    val isCompleted: Boolean = false,
    val scheduledTime: String = "", // e.g. "07:30", "18:15"
    val ringSound: Boolean = false, // false = rapid vibration only, true = sound + vibration
    val subtasksJson: String = "[]", // JSON array of subtasks: [{"text":"...", "done":false}]
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "fuel_entries")
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personOrIncident: String,
    val defianceVow: String,
    val category: String = "Doubter", // "Doubter", "Teacher", "Obstacle", "Goal"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val date: String, // "YYYY-MM-DD"
    val completedCount: Int,
    val totalCount: Int,
    val percentage: Float
)

@Entity(tableName = "videos")
data class VideoEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String = "Coding", // "Coding", "Security", "Mandarin", "College", "Mindset", "Other"
    val reminderEpochMs: Long = 0L, // 0 = no reminder scheduled
    val reminderType: String = "RAPID_VIBRATE", // "RAPID_VIBRATE", "ALARM_SOUND", "NOTIFICATION"
    val reminderDelayText: String = "", // e.g. "In 1 hour", "Tonight 8:00 PM"
    val isWatched: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
