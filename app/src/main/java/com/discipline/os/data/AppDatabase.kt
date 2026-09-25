package com.discipline.os.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [Task::class, FuelEntry::class, DailyLog::class, VideoEntry::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun fuelDao(): FuelDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val seedMutex = Mutex()

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN tasksSnapshotJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "discipline_os.db"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Scans the database and purges duplicate tasks, fuel entries, and videos.
         * Returns the total number of duplicate records removed.
         */
        suspend fun deduplicateDatabase(db: AppDatabase): Int {
            var deletedCount = 0
            val taskDao = db.taskDao()
            val fuelDao = db.fuelDao()
            val videoDao = db.videoDao()

            // 1. Deduplicate Tasks by normalized title
            val allTasks = taskDao.getAllTasksSync()
            val seenTasks = mutableMapOf<String, Task>()
            for (task in allTasks) {
                val key = task.title.trim().lowercase()
                val existing = seenTasks[key]
                if (existing == null) {
                    seenTasks[key] = task
                } else {
                    // Decide which task instance to keep: prefer completed / modified over untouched
                    val keepExisting = existing.isCompleted || (!task.isCompleted && existing.id < task.id)
                    val toDelete = if (keepExisting) task else existing
                    val toKeep = if (keepExisting) existing else task
                    taskDao.deleteTaskById(toDelete.id)
                    seenTasks[key] = toKeep
                    deletedCount++
                }
            }

            // 2. Deduplicate Fuel Entries by normalized person/incident
            val allFuel = fuelDao.getAllFuelSync()
            val seenFuel = mutableMapOf<String, FuelEntry>()
            for (fuel in allFuel) {
                val key = fuel.personOrIncident.trim().lowercase()
                val existing = seenFuel[key]
                if (existing == null) {
                    seenFuel[key] = fuel
                } else {
                    fuelDao.deleteFuelById(fuel.id)
                    deletedCount++
                }
            }

            // 3. Deduplicate Videos by normalized URL or title
            val allVideos = videoDao.getAllVideosSync()
            val seenVideos = mutableMapOf<String, VideoEntry>()
            for (video in allVideos) {
                val key = if (video.url.isNotBlank()) video.url.trim().lowercase() else video.title.trim().lowercase()
                val existing = seenVideos[key]
                if (existing == null) {
                    seenVideos[key] = video
                } else {
                    val keepExisting = video.isWatched || (!video.isWatched && existing.id < video.id)
                    val toDelete = if (keepExisting) video else existing
                    val toKeep = if (keepExisting) existing else video
                    videoDao.deleteVideoById(toDelete.id)
                    seenVideos[key] = toKeep
                    deletedCount++
                }
            }

            return deletedCount
        }

        suspend fun seedDefaultData(db: AppDatabase) = seedMutex.withLock {
            val taskDao = db.taskDao()
            val fuelDao = db.fuelDao()
            val videoDao = db.videoDao()

            // Step 1: Run deduplication immediately to sanitize any prior duplicate insertions
            deduplicateDatabase(db)

            // Step 2: Fetch existing task titles (case-insensitive & trimmed)
            val existingTitles = taskDao.getAllTasksSync().map { it.title.trim().lowercase() }.toSet()

            val defaults = listOf(
                Task(
                    title = "Do 10 push-ups (Strength & energy)",
                    description = "Build physical strength and mental alertness first thing in the morning.",
                    category = "Health",
                    priority = 1,
                    scheduledTime = "07:15",
                    ringSound = false,
                    subtasksJson = """[{"text":"10 clean-form push-ups","done":false}]""",
                    sortOrder = 1
                ),
                Task(
                    title = "Coding for 1 hour / Code with Harry",
                    description = "Daily technical growth. Never passively watch tutorials; always compile and debug code yourself in GCC.",
                    category = "Coding",
                    priority = 1,
                    scheduledTime = "07:30",
                    ringSound = false,
                    subtasksJson = """[{"text":"Watch 1 lesson / chapter","done":false},{"text":"Type and compile code in VS Code","done":false},{"text":"Debug errors independently","done":false}]""",
                    sortOrder = 2
                ),
                Task(
                    title = "Attend College Lectures & Lab Practice",
                    description = "Diploma CSE core attendance: Data Structures, Algorithms, and Operating Systems. Zero backlogs.",
                    category = "College",
                    priority = 1,
                    scheduledTime = "08:45",
                    ringSound = false,
                    subtasksJson = """[{"text":"Attend core morning lectures","done":false},{"text":"Hands-on programming lab practice","done":false}]""",
                    sortOrder = 3
                ),
                Task(
                    title = "College homework & subject catch-up",
                    description = "Stay ahead in Diploma CSE subjects (Data Structures, Algorithms, COA). Zero backlog.",
                    category = "College",
                    priority = 1,
                    scheduledTime = "17:27",
                    ringSound = false,
                    subtasksJson = """[{"text":"Review today's lecture notes","done":false},{"text":"Complete pending lab/homework","done":false}]""",
                    sortOrder = 4
                ),
                Task(
                    title = "Learn Mandarin (Vocab & Pinyin)",
                    description = "Language acquisition for competitive edge. 15-20 mins Pinyin, Hanzi flashcards, and tones.",
                    category = "Language",
                    priority = 2,
                    scheduledTime = "18:30",
                    ringSound = false,
                    subtasksJson = """[{"text":"10 new Hanzi flashcards","done":false},{"text":"Practice 4 tones aloud","done":false}]""",
                    sortOrder = 5
                ),
                Task(
                    title = "Cyber Security (TryHackMe / Practice)",
                    description = "Hands-on offensive/defensive security labs and CTF fundamentals.",
                    category = "Security",
                    priority = 2,
                    scheduledTime = "20:27",
                    ringSound = false,
                    subtasksJson = """[{"text":"Complete 1 TryHackMe room","done":false},{"text":"Document findings in notes","done":false}]""",
                    sortOrder = 6
                ),
                Task(
                    title = "Be better than yesterday (1% improvement)",
                    description = "Daily evening reflection. Audit what went well, what slipped, and commit to tomorrow.",
                    category = "Bedtime",
                    priority = 1,
                    scheduledTime = "22:00",
                    ringSound = false,
                    subtasksJson = """[{"text":"Audit today's completions","done":false},{"text":"Beat yesterday's version of self","done":false}]""",
                    sortOrder = 7
                ),
                Task(
                    title = "Zero porn & addictive shorts (Focus shield)",
                    description = "Dopamine receptor protection. No short-form doomscrolling, no adult content. Pure deep focus.",
                    category = "Discipline",
                    priority = 1,
                    scheduledTime = "",
                    ringSound = false,
                    subtasksJson = """[{"text":"Zero short-form scrolling (Reels/Shorts)","done":false},{"text":"Zero adult content","done":false}]""",
                    sortOrder = 8
                )
            )

            // Only insert tasks that don't already exist by title
            val toInsert = defaults.filter { it.title.trim().lowercase() !in existingTitles }
            if (toInsert.isNotEmpty()) {
                taskDao.insertTasks(toInsert)
            }

            // Step 3: Update existing tasks to ensure chronological times and order are strictly synced
            val allCurrentTasks = taskDao.getAllTasksSync()
            for (task in allCurrentTasks) {
                val titleLower = task.title.lowercase()
                var updated = task
                if (titleLower.contains("push-up") && (task.scheduledTime != "07:15" || task.sortOrder != 1)) {
                    updated = updated.copy(scheduledTime = "07:15", sortOrder = 1)
                } else if (titleLower.contains("coding") && (task.scheduledTime != "07:30" || task.sortOrder != 2)) {
                    updated = updated.copy(scheduledTime = "07:30", sortOrder = 2)
                } else if (titleLower.contains("college lecture") && (task.scheduledTime != "08:45" || task.sortOrder != 3)) {
                    updated = updated.copy(scheduledTime = "08:45", sortOrder = 3)
                } else if (titleLower.contains("college homework") && (task.scheduledTime != "17:27" || task.sortOrder != 4)) {
                    updated = updated.copy(scheduledTime = "17:27", sortOrder = 4)
                } else if (titleLower.contains("mandarin") && (task.scheduledTime != "18:30" || task.sortOrder != 5)) {
                    updated = updated.copy(scheduledTime = "18:30", sortOrder = 5)
                } else if (titleLower.contains("cyber security") && (task.scheduledTime != "20:27" || task.sortOrder != 6)) {
                    updated = updated.copy(scheduledTime = "20:27", sortOrder = 6)
                } else if ((titleLower.contains("better than yesterday") || task.category.equals("bedtime", true)) && (task.scheduledTime != "22:00" || task.sortOrder != 7)) {
                    updated = updated.copy(scheduledTime = "22:00", sortOrder = 7)
                }
                if (updated != task) {
                    taskDao.updateTask(updated)
                }
            }

            // Fuel default seeding (only if not already present)
            val existingFuel = fuelDao.getAllFuelSync().map { it.personOrIncident.trim().lowercase() }.toSet()
            val defaultPerson = "The teacher who embarrassed me in class"
            if (defaultPerson.trim().lowercase() !in existingFuel) {
                fuelDao.insertFuel(
                    FuelEntry(
                        personOrIncident = defaultPerson,
                        defianceVow = "I will be better than him. 1% every single day until my results speak for themselves.",
                        category = "Teacher"
                    )
                )
            }

            // Video default seeding (only if not already present)
            val existingVideos = videoDao.getAllVideosSync().map { it.url.trim().lowercase() }.toSet()
            val defaultVideos = listOf(
                VideoEntry(
                    title = "C Language Full Tutorial For Beginners - CodeWithHarry",
                    url = "https://youtu.be/irqbmMNs2Bo",
                    category = "Coding",
                    reminderType = "RAPID_VIBRATE",
                    reminderDelayText = "Tomorrow 07:30",
                    notes = "Crucial pointer and memory management lectures. Code along in GCC."
                ),
                VideoEntry(
                    title = "TryHackMe Complete Beginner Walkthrough & CTF Basics",
                    url = "https://youtu.be/2NX09qFm01M",
                    category = "Security",
                    reminderType = "RAPID_VIBRATE",
                    reminderDelayText = "Tonight 20:27",
                    notes = "Core networking, Linux terminal commands, and basic privilege escalation."
                )
            )
            val videosToInsert = defaultVideos.filter { it.url.trim().lowercase() !in existingVideos }
            if (videosToInsert.isNotEmpty()) {
                videoDao.insertVideos(videosToInsert)
            }

            // Auto-normalize any existing videos to correct routine schedule
            val allVideos = videoDao.getAllVideosSync()
            for (v in allVideos) {
                var updated = v
                val titleLower = v.title.lowercase()
                if (titleLower.contains("c language") && (v.reminderDelayText.contains("20:00") || v.reminderDelayText.isBlank())) {
                    updated = updated.copy(reminderDelayText = "Tomorrow 07:30")
                } else if (titleLower.contains("tryhackme") && (v.reminderDelayText.contains("07:30") || v.reminderDelayText.isBlank())) {
                    updated = updated.copy(reminderDelayText = "Tonight 20:27")
                }
                if (updated != v) {
                    videoDao.updateVideo(updated)
                }
            }

            // Daily Log seeding (ensure yesterday 2026-09-23 exists with exact snapshot)
            val logDao = db.dailyLogDao()
            val existingLogs = logDao.getAllLogsSync().map { it.date }.toSet()
            if ("2026-09-23" !in existingLogs) {
                val yesterdayTasksJson = """[
                    {"title":"Master GCC Assembly","category":"Coding","priority":1,"isCompleted":true,"scheduledTime":"09:00"},
                    {"title":"Coding for 1 hour / Code with Harry","category":"Coding","priority":1,"isCompleted":true,"scheduledTime":"07:30"},
                    {"title":"Do 10 push-ups (Strength & energy)","category":"Health","priority":1,"isCompleted":true,"scheduledTime":"07:15"},
                    {"title":"College homework & subject catch-up","category":"College","priority":1,"isCompleted":true,"scheduledTime":"17:27"},
                    {"title":"Zero porn & addictive shorts (Focus shield)","category":"Discipline","priority":1,"isCompleted":true,"scheduledTime":""},
                    {"title":"Be better than yesterday (1% improvement)","category":"Mindset","priority":1,"isCompleted":true,"scheduledTime":"22:00"},
                    {"title":"Cyber Security (TryHackMe / Practice)","category":"Security","priority":2,"isCompleted":false,"scheduledTime":"20:27"},
                    {"title":"Learn Mandarin (Vocab & Pinyin)","category":"Language","priority":2,"isCompleted":false,"scheduledTime":"18:30"}
                ]""".trimIndent()

                logDao.insertLog(
                    DailyLog(
                        date = "2026-09-23",
                        completedCount = 6,
                        totalCount = 8,
                        percentage = 75.0f,
                        tasksSnapshotJson = yesterdayTasksJson
                    )
                )
            }
        }
    }
}
