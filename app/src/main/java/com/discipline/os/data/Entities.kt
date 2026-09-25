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
    val category: String = "Doubter", // "Doubter", "Critic", "Skeptic", "Obstacle", "Goal"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val date: String, // "YYYY-MM-DD"
    val completedCount: Int,
    val totalCount: Int,
    val percentage: Float,
    val tasksSnapshotJson: String = "[]"
)

@Entity(tableName = "videos")
data class VideoEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String = "Coding", // "Coding", "Systems", "Security", "Language", "Mindset", "Other"
    val reminderEpochMs: Long = 0L, // 0 = no reminder scheduled
    val reminderType: String = "RAPID_VIBRATE", // "RAPID_VIBRATE", "ALARM_SOUND", "NOTIFICATION"
    val reminderDelayText: String = "", // e.g. "In 1 hour", "Tonight 8:00 PM"
    val isWatched: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "roadmaps")
data class Roadmap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "Fitness", // "Fitness", "Coding", "Security", "Life"
    val targetGoal: String = "", // e.g. "Clean Pull-up, Dip, Handstand, Muscle-Up"
    val currentLevel: String = "Beginner (Level 0 - Foundation)",
    val progressPercentage: Float = 0f,
    val isPinned: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "roadmap_nodes",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Roadmap::class,
            parentColumns = ["id"],
            childColumns = ["roadmapId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index(value = ["roadmapId"])]
)
data class RoadmapNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roadmapId: Long,
    val stage: String, // e.g. "Phase 1: Foundation & Mindset", "Phase 2: The 4 Pillars"
    val stepOrder: Int,
    val title: String,
    val description: String = "",
    val repsOrCriteria: String = "",
    val isCompleted: Boolean = false,
    val isCurrent: Boolean = false,
    val checklistJson: String = "[]" // JSON array: [{"text":"Wall Push-ups 3x15","done":false}]
)

