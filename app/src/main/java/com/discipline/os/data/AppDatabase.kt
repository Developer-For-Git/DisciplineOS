package com.discipline.os.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [Task::class, FuelEntry::class, DailyLog::class, VideoEntry::class, Roadmap::class, RoadmapNode::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun fuelDao(): FuelDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun videoDao(): VideoDao
    abstract fun roadmapDao(): RoadmapDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val seedMutex = Mutex()

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN tasksSnapshotJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `roadmaps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `targetGoal` TEXT NOT NULL,
                        `currentLevel` TEXT NOT NULL,
                        `progressPercentage` REAL NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `roadmap_nodes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `roadmapId` INTEGER NOT NULL,
                        `stage` TEXT NOT NULL,
                        `stepOrder` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `repsOrCriteria` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `isCurrent` INTEGER NOT NULL,
                        `checklistJson` TEXT NOT NULL,
                        FOREIGN KEY(`roadmapId`) REFERENCES `roadmaps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_roadmap_nodes_roadmapId` ON `roadmap_nodes` (`roadmapId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "discipline_os.db"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
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

            // 4. Deduplicate Roadmaps by normalized title
            val roadmapDao = db.roadmapDao()
            val allRoadmaps = roadmapDao.getAllRoadmapsSync()
            val seenRoadmaps = mutableMapOf<String, Roadmap>()
            for (roadmap in allRoadmaps) {
                val key = roadmap.title.trim().lowercase()
                val existing = seenRoadmaps[key]
                if (existing == null) {
                    seenRoadmaps[key] = roadmap
                } else {
                    val toDelete = if (existing.progressPercentage >= roadmap.progressPercentage) roadmap else existing
                    val toKeep = if (existing.progressPercentage >= roadmap.progressPercentage) existing else roadmap
                    roadmapDao.deleteRoadmapById(toDelete.id)
                    seenRoadmaps[key] = toKeep
                    deletedCount++
                }
            }

            return deletedCount
        }

        suspend fun seedDefaultData(db: AppDatabase) = seedMutex.withLock {
            val taskDao = db.taskDao()
            val fuelDao = db.fuelDao()
            val videoDao = db.videoDao()
            val roadmapDao = db.roadmapDao()

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
                    title = "Your CALISTHENICS Journey - Beginner to Mastery",
                    url = "https://youtu.be/7qvOgQqeeYc",
                    category = "Calisthenics",
                    reminderType = "RAPID_VIBRATE",
                    reminderDelayText = "Morning 07:15",
                    notes = "Zero equipment, 4 fundamental pillars (Push, Pull, Legs, Core), progressive overload ladder, and 30-day foundation blueprint."
                ),
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
                } else if (titleLower.contains("calisthenics") && v.reminderDelayText.isBlank()) {
                    updated = updated.copy(reminderDelayText = "Morning 07:15")
                }
                if (updated != v) {
                    videoDao.updateVideo(updated)
                }
            }

            // Roadmap default seeding (Your CALISTHENICS Journey)
            val existingRoadmaps = roadmapDao.getAllRoadmapsSync()
            val hasCalisthenics = existingRoadmaps.any { it.title.contains("Calisthenics", ignoreCase = true) }
            if (!hasCalisthenics) {
                val calisthenicsId = roadmapDao.insertRoadmap(
                    Roadmap(
                        title = "Your CALISTHENICS Journey",
                        description = "Complete progressive bodyweight strength system: from zero pull-ups to clean muscle-up. Built on 4 fundamental pillars (Push, Pull, Legs, Core), 5-12 rep sweet spot, and 30-day foundation blueprint.",
                        category = "Fitness",
                        targetGoal = "Clean Pull-up, Dip, Handstand & Muscle-Up",
                        currentLevel = "Beginner (Level 0 - Foundation)",
                        progressPercentage = 7.1f,
                        isPinned = true
                    )
                )

                val calisthenicsNodes = listOf(
                    // Phase 1: Mindset & Equipment
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 1: Mindset & Equipment",
                        stepOrder = 1,
                        title = "Rule Zero: Zero Excuses & Ego-Free Start",
                        description = "You don't need to be fit to start; calisthenics is how you get fit. Every movement scales from zero to extreme advanced. Being weak is the reason to start, not an excuse to delay.",
                        repsOrCriteria = "Commitment: 'I Will Achieve This'",
                        isCompleted = true,
                        isCurrent = false,
                        checklistJson = """[{"text":"Discard 'I am too skinny / heavy' excuse","done":true},{"text":"Accept that being weak is the reason to begin","done":true},{"text":"Commit to ego-free training (form over reps)","done":true}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 1: Mindset & Equipment",
                        stepOrder = 2,
                        title = "Equipment Setup (Level 0 to Level 2)",
                        description = "Level 0 (Zero Cost): Just the ground. Covers push-ups, squats, lunges, and core. Level 1 (Minimal): Pull-up bar (doorway or park) + resistance band. Level 2 (Advanced): Gymnastic rings / dip bars (not needed as beginner).",
                        repsOrCriteria = "Level 0 & 1 Setup Ready",
                        isCompleted = false,
                        isCurrent = true,
                        checklistJson = """[{"text":"Establish workout ground space at home","done":false},{"text":"Locate park pull-up bar or doorway bar","done":false},{"text":"(Optional) Procure resistance band for assisted pull-ups","done":false}]"""
                    ),

                    // Phase 2: The 4 Fundamental Pillars
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 2: The 4 Pillars",
                        stepOrder = 3,
                        title = "Pillar 1: PUSH Mastery (Wall to Regular Push-ups)",
                        description = "Builds Chest, Shoulders, and Triceps. Progress strictly through each variation: Wall Push-ups -> Incline Push-ups (bed/table) -> Knee Push-ups -> Regular Floor Push-ups -> Diamond & Decline Push-ups.",
                        repsOrCriteria = "3 sets of 8-12 reps per progression",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Wall Push-ups (3 sets of 15 strict)","done":false},{"text":"Incline Push-ups (Hands on table/bed, 3x12)","done":false},{"text":"Knee Push-ups (Reduced bodyweight on knees, 3x12)","done":false},{"text":"Regular Floor Push-ups (First strict clean push-up)","done":false},{"text":"Diamond & Decline Push-ups (Triceps & Upper Chest)","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 2: The 4 Pillars",
                        stepOrder = 4,
                        title = "Pillar 2: PULL Mastery (Inverted Rows to First Pull-up)",
                        description = "Builds Back, Biceps, and creates the V-Taper aesthetic. Never skip pulling. Progress: Inverted Rows (under sturdy table) -> Negative Pull-ups (jump up, 3-5s slow descent) -> Resistance Band Pull-ups -> First Clean Unassisted Pull-up.",
                        repsOrCriteria = "3 sets of 5-10 reps per progression",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Inverted Rows under sturdy table or low bar (3x10)","done":false},{"text":"Negative Pull-ups (Jump to top, 3-5 sec slow descent, 3x5)","done":false},{"text":"Resistance Band Assisted Pull-ups (3x8)","done":false},{"text":"First Clean Unassisted Pull-up (The Holy Grail)","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 2: The 4 Pillars",
                        stepOrder = 5,
                        title = "Pillar 3: LEGS Mastery (No Chicken Legs, Pure Power)",
                        description = "A powerful V-taper physique requires a strong foundation: Quads, Hamstrings, and Calves. Progress: Assisted Squats (holding doorframe) -> Bodyweight Deep Squats (below parallel) -> Reverse Lunges -> Bulgarian Split Squats.",
                        repsOrCriteria = "3 sets of 10-15 reps per progression",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Assisted Squats holding doorframe for balance (3x15)","done":false},{"text":"Bodyweight Deep Squats below parallel (3x15)","done":false},{"text":"Reverse Lunges for single-leg stability (3x10 each leg)","done":false},{"text":"Bulgarian Split Squats (Ultimate leg builder, 3x8 each leg)","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 2: The 4 Pillars",
                        stepOrder = 6,
                        title = "Pillar 4: CORE Mastery (Solid Steel Anti-Extension Rod)",
                        description = "Stop doing endless crunches that strain your lower back. Build anti-extension spinal stability. Progress: Dead Bugs (lower back pressed firmly to floor) -> Forearm Plank (straight rod line) -> Lying Leg Raises -> Hanging Knee/Leg Raises.",
                        repsOrCriteria = "3 sets of 8-12 reps / 45s hold",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Dead Bugs (Lower back glued flat to floor, 3x10 each side)","done":false},{"text":"Strict Forearm Plank (Hips level, rod line, 3x45 sec)","done":false},{"text":"Lying Leg Raises (Slow controlled descent, 3x10)","done":false},{"text":"Hanging Knee / Straight Leg Raises on bar (3x8)","done":false}]"""
                    ),

                    // Phase 3: The Golden Protocol & Routine
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 3: Routine & Overload",
                        stepOrder = 7,
                        title = "Full Body Structure (3-4 Days / Week)",
                        description = "Avoid complex bodybuilder splits. Train full body 3 to 4 days per week with rest days in between. Routine order: 1. Pull (3 sets) -> 2. Push (3 sets) -> 3. Legs (3 sets) -> 4. Core (3 sets). Rest exactly 2 minutes between sets.",
                        repsOrCriteria = "3-4 days/week • 2 min rest between sets",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Schedule 3-4 days weekly workout slots (e.g. Mon, Wed, Fri)","done":false},{"text":"Follow exact order: Pull -> Push -> Legs -> Core","done":false},{"text":"Enforce strict 2-minute rest timer between sets","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 3: Routine & Overload",
                        stepOrder = 8,
                        title = "The 5-12 Rep Sweet Spot & Progressive Overload",
                        description = "If 15+ reps feel easy -> upgrade variation. If <5 reps with broken form -> step down one variation. Overload methods: 1. More Reps -> 2. Better Range of Motion -> 3. Slower Tempo (3-sec down) -> 4. Harder Variation.",
                        repsOrCriteria = "5 to 12 rep struggle zone with strict form",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Stay in 5-12 rep sweet spot (fatigue with clean form)","done":false},{"text":"Apply 3-second slow eccentric descent on every rep","done":false},{"text":"Earn each progression (12-15 clean reps before moving up)","done":false}]"""
                    ),

                    // Phase 4: Fuel & Sleep
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 4: Fuel & Sleep",
                        stepOrder = 9,
                        title = "Nutrition & Deep Sleep Recovery",
                        description = "Muscles are torn in the workout; they repair and grow only during deep sleep. Muscle gain: Caloric surplus + high protein. Fat loss: Caloric deficit + high protein. 7-8 hours deep sleep is non-negotiable.",
                        repsOrCriteria = "7-8 hours deep sleep • High protein",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Hit daily protein goal (0.8-1g per lb bodyweight)","done":false},{"text":"Sleep 7-8 hours uninterrupted deep sleep every night","done":false},{"text":"Remember: Soreness != Progress (Progressive overload is progress)","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 4: Fuel & Sleep",
                        stepOrder = 10,
                        title = "Daily Logging & Performance Audit",
                        description = "If you do not track your workouts, you are shooting arrows in the dark. Open the app notes or notepad after each session and record exact sets, reps, and variations. Always strive to beat last session.",
                        repsOrCriteria = "Log every session's reps & sets",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Log sets and reps after every session","done":false},{"text":"Review progressive overload weekly","done":false}]"""
                    ),

                    // Phase 5: 30-Day Execution Game Plan
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 5: 30-Day Game Plan",
                        stepOrder = 11,
                        title = "Week 1: Form Baseline & 3-Day Habit",
                        description = "Discover your baseline variation for Push, Pull, Legs, and Core. Focus purely on clean technique and establish a 3-day workout habit.",
                        repsOrCriteria = "3 completed sessions • Form mastery",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Session 1: Identify baseline variations for all 4 pillars","done":false},{"text":"Session 2: Form check and strict 2-min rest enforcement","done":false},{"text":"Session 3: First week completed without missing a workout","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 5: 30-Day Game Plan",
                        stepOrder = 12,
                        title = "Week 2: Controlled Tempo & Rep Expansion",
                        description = "Apply a 3-second negative eccentric descent on push-ups and inverted rows. Push reps towards 8-10 with controlled breathing.",
                        repsOrCriteria = "3-sec descent tempo • +1-2 reps per set",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Enforce 3-second descent on push-ups and rows","done":false},{"text":"Increase rep counts by 1-2 reps over Week 1","done":false},{"text":"Zero missed sessions (3 sessions logged)","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 5: 30-Day Game Plan",
                        stepOrder = 13,
                        title = "Week 3: Challenge Elevation",
                        description = "For any exercise where you comfortably reach 12 reps, graduate to the next harder variation (e.g. Wall -> Incline, Knee -> Regular, Inverted Rows -> Negative Pull-ups).",
                        repsOrCriteria = "Promote to next variation on strongest pillar",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Attempt harder progression on at least one pillar","done":false},{"text":"Maintain pristine form under increased resistance","done":false},{"text":"Hit all 3 sessions with high intensity","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 5: 30-Day Game Plan",
                        stepOrder = 14,
                        title = "Week 4: 30-Day Benchmark Audit",
                        description = "Audit your 30-day foundation. Test your maximum clean push-ups, negative pull-up hold time, and unassisted pull-up attempt. Celebrate an unbreakable habit.",
                        repsOrCriteria = "Benchmark audit • Unbreakable foundation set",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"Test maximum strict push-up reps","done":false},{"text":"Test 5-second negative pull-up or unassisted pull-up","done":false},{"text":"Review 30-day consistency score & set Month 2 goals","done":false}]"""
                    ),

                    // Phase 6: Skill Unlock Path (Elevate Legacy Guide Page 12)
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 6: Skill Unlock Path",
                        stepOrder = 15,
                        title = "Skill 1: Freestanding Handstand",
                        description = "The premier bodyweight balance feat. Built from Push L4+ and Core L4+. Prerequisite: 60-second stomach-to-wall plank hold + 8 clean pike push-ups. Progress: Wall Walk -> Chest-to-Wall Handstand -> Toe Taps -> Freestanding Handstand.",
                        repsOrCriteria = "60s wall hold • 8 pike push-ups",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"60-sec stomach-to-wall hold","done":false},{"text":"8 clean pike push-ups","done":false},{"text":"Wall toe-taps for balance","done":false},{"text":"5-second freestanding hold","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 6: Skill Unlock Path",
                        stepOrder = 16,
                        title = "Skill 2: Clean Bar Muscle-Up",
                        description = "The ultimate display of upper body power. Built from Pull L5 + Push L5. Prerequisite: 8-10 strict chest-to-bar pull-ups + 10-12 deep straight bar dips. Never chicken-wing or use extreme kipping.",
                        repsOrCriteria = "8-10 chest-to-bar pull-ups • 10-12 straight bar dips",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"8-10 strict chest-to-bar pull-ups","done":false},{"text":"10-12 deep straight-bar dips","done":false},{"text":"Explosive high pull-up to sternum","done":false},{"text":"First clean simultaneous two-arm transition","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 6: Skill Unlock Path",
                        stepOrder = 17,
                        title = "Skill 3: Static Front Lever",
                        description = "Extreme horizontal lat and straight-arm pulling power. Built from Pull L5 + Core L5. Prerequisite: 15-20 second tuck front-lever hold + 12+ strict hanging straight-leg raises.",
                        repsOrCriteria = "15-20s tuck lever • 12 hanging leg raises",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"15-20 sec tuck front lever hold","done":false},{"text":"12 strict hanging toes-to-bar leg raises","done":false},{"text":"Advanced tuck / single-leg extension","done":false},{"text":"5-second full straddle/straight front lever","done":false}]"""
                    ),
                    RoadmapNode(
                        roadmapId = calisthenicsId,
                        stage = "Phase 6: Skill Unlock Path",
                        stepOrder = 18,
                        title = "Skill 4: Strict Full-Depth Pistol Squat",
                        description = "Unilateral leg dominance, ankle mobility, and quad strength. Built from Legs L5. Prerequisite: 12 reps/leg Bulgarian split squat at full depth with zero wobble.",
                        repsOrCriteria = "12 reps/leg Bulgarian split squat",
                        isCompleted = false,
                        isCurrent = false,
                        checklistJson = """[{"text":"12 clean Bulgarian split squats per leg","done":false},{"text":"Box / bench pistol squat to parallel","done":false},{"text":"Counter-balance assisted pistol squat","done":false},{"text":"Full-depth unassisted pistol squat (both legs)","done":false}]"""
                    )
                )
                roadmapDao.insertNodes(calisthenicsNodes)
            } else {
                // Ensure existing calisthenics roadmap gets the 4 new skill unlock nodes if missing
                val calisthenicsMap = existingRoadmaps.firstOrNull { it.title.contains("Calisthenics", ignoreCase = true) }
                if (calisthenicsMap != null) {
                    val existingNodes = roadmapDao.getNodesForRoadmapSync(calisthenicsMap.id)
                    val existingTitles = existingNodes.map { it.title.lowercase() }.toSet()
                    val missingSkillNodes = listOf(
                        RoadmapNode(
                            roadmapId = calisthenicsMap.id,
                            stage = "Phase 6: Skill Unlock Path",
                            stepOrder = 15,
                            title = "Skill 1: Freestanding Handstand",
                            description = "The premier bodyweight balance feat. Built from Push L4+ and Core L4+. Prerequisite: 60-second stomach-to-wall plank hold + 8 clean pike push-ups. Progress: Wall Walk -> Chest-to-Wall Handstand -> Toe Taps -> Freestanding Handstand.",
                            repsOrCriteria = "60s wall hold • 8 pike push-ups",
                            isCompleted = false,
                            isCurrent = false,
                            checklistJson = """[{"text":"60-sec stomach-to-wall hold","done":false},{"text":"8 clean pike push-ups","done":false},{"text":"Wall toe-taps for balance","done":false},{"text":"5-second freestanding hold","done":false}]"""
                        ),
                        RoadmapNode(
                            roadmapId = calisthenicsMap.id,
                            stage = "Phase 6: Skill Unlock Path",
                            stepOrder = 16,
                            title = "Skill 2: Clean Bar Muscle-Up",
                            description = "The ultimate display of upper body power. Built from Pull L5 + Push L5. Prerequisite: 8-10 strict chest-to-bar pull-ups + 10-12 deep straight bar dips. Never chicken-wing or use extreme kipping.",
                            repsOrCriteria = "8-10 chest-to-bar pull-ups • 10-12 straight bar dips",
                            isCompleted = false,
                            isCurrent = false,
                            checklistJson = """[{"text":"8-10 strict chest-to-bar pull-ups","done":false},{"text":"10-12 deep straight-bar dips","done":false},{"text":"Explosive high pull-up to sternum","done":false},{"text":"First clean simultaneous two-arm transition","done":false}]"""
                        ),
                        RoadmapNode(
                            roadmapId = calisthenicsMap.id,
                            stage = "Phase 6: Skill Unlock Path",
                            stepOrder = 17,
                            title = "Skill 3: Static Front Lever",
                            description = "Extreme horizontal lat and straight-arm pulling power. Built from Pull L5 + Core L5. Prerequisite: 15-20 second tuck front-lever hold + 12+ strict hanging straight-leg raises.",
                            repsOrCriteria = "15-20s tuck lever • 12 hanging leg raises",
                            isCompleted = false,
                            isCurrent = false,
                            checklistJson = """[{"text":"15-20 sec tuck front lever hold","done":false},{"text":"12 strict hanging toes-to-bar leg raises","done":false},{"text":"Advanced tuck / single-leg extension","done":false},{"text":"5-second full straddle/straight front lever","done":false}]"""
                        ),
                        RoadmapNode(
                            roadmapId = calisthenicsMap.id,
                            stage = "Phase 6: Skill Unlock Path",
                            stepOrder = 18,
                            title = "Skill 4: Strict Full-Depth Pistol Squat",
                            description = "Unilateral leg dominance, ankle mobility, and quad strength. Built from Legs L5. Prerequisite: 12 reps/leg Bulgarian split squat at full depth with zero wobble.",
                            repsOrCriteria = "12 reps/leg Bulgarian split squat",
                            isCompleted = false,
                            isCurrent = false,
                            checklistJson = """[{"text":"12 clean Bulgarian split squats per leg","done":false},{"text":"Box / bench pistol squat to parallel","done":false},{"text":"Counter-balance assisted pistol squat","done":false},{"text":"Full-depth unassisted pistol squat (both legs)","done":false}]"""
                        )
                    ).filter { it.title.lowercase() !in existingTitles }

                    if (missingSkillNodes.isNotEmpty()) {
                        roadmapDao.insertNodes(missingSkillNodes)
                    }
                }
            }
        }
    }
}
