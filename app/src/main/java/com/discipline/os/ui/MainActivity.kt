package com.discipline.os.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.discipline.os.alarm.AlarmScheduler
import com.discipline.os.alarm.VibrationHelper
import com.discipline.os.agent.AiAgentEngine
import com.discipline.os.data.AppDatabase
import com.discipline.os.data.FuelEntry
import com.discipline.os.data.Task
import com.discipline.os.data.VideoEntry
import com.discipline.os.update.UpdateDialog
import com.discipline.os.update.UpdateInfo
import com.discipline.os.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private var sharedVideoUrl by mutableStateOf("")
    private var sharedVideoTitle by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleSendIntent(intent)

        val db = AppDatabase.getDatabase(this)
        val taskDao = db.taskDao()
        val fuelDao = db.fuelDao()
        val videoDao = db.videoDao()
        val dailyLogDao = db.dailyLogDao()
        val prefs = getSharedPreferences("discipline_prefs", Context.MODE_PRIVATE)

        setContent {
            // Default to Dark Mode for eye strain relief (persisted in SharedPreferences)
            var isDarkMode by remember {
                mutableStateOf(prefs.getBoolean("is_dark_mode", true))
            }
            var is24Hour by remember {
                mutableStateOf(prefs.getBoolean("is_24_hour_format", false))
            }

            // Dynamically update Android System Bars according to theme
            DisposableEffect(isDarkMode) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDarkMode
                    isAppearanceLightNavigationBars = !isDarkMode
                }
                onDispose {}
            }

            DisciplineTheme(isDarkMode = isDarkMode) {
                var selectedTab by remember { mutableStateOf(0) }
                val agentEngine = remember { AiAgentEngine(this@MainActivity, db) }
                val tasks by taskDao.getAllTasks().collectAsState(initial = emptyList())
                val fuelList by fuelDao.getAllFuel().collectAsState(initial = emptyList())
                val videoList by videoDao.getAllVideos().collectAsState(initial = emptyList())
                val dailyLogs by dailyLogDao.getAllLogs().collectAsState(initial = emptyList())

                // Past Days History Dialog state
                var showHistoryDialog by remember { mutableStateOf(false) }

                // OTA Wi-Fi Update state
                var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }

                // 1. Reactive live update flow: Pops up on screen instantly when update is published from PC
                LaunchedEffect(Unit) {
                    UpdateManager.liveUpdateNotificationFlow.collect { info ->
                        if (info.versionCode > UpdateManager.getCurrentVersionCode(this@MainActivity)) {
                            withContext(Dispatchers.Main) {
                                pendingUpdateInfo = info
                                showUpdateDialog = true
                            }
                        }
                    }
                }

                // 2. On launch and on resume: check offline saved update first, then Wi-Fi server
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                // First check if an offline update is already downloaded and saved on device
                                val offlineSaved = UpdateManager.getSavedOfflineUpdate(this@MainActivity)
                                if (offlineSaved != null) {
                                    withContext(Dispatchers.Main) {
                                        pendingUpdateInfo = offlineSaved
                                        showUpdateDialog = true
                                    }
                                    return@launch
                                }

                                // Otherwise check Wi-Fi update server
                                try {
                                    val result = UpdateManager.checkForUpdate(this@MainActivity)
                                    result.onSuccess { info ->
                                        if (info != null) {
                                            withContext(Dispatchers.Main) {
                                                pendingUpdateInfo = info
                                                showUpdateDialog = true
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Automatically switch to Videos tab if a shared link was detected
                LaunchedEffect(sharedVideoUrl) {
                    if (sharedVideoUrl.isNotBlank()) {
                        selectedTab = 1
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CanvasBg)
                        .statusBarsPadding()
                ) {
                    if (showHistoryDialog) {
                        androidx.activity.compose.BackHandler {
                            showHistoryDialog = false
                        }
                        HistoryScreen(
                            dailyLogs = dailyLogs,
                            onDismiss = { showHistoryDialog = false }
                        )
                    } else {
                        // Screen Content Area
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (selectedTab) {
                            0 -> DisciplineScreen(
                                tasks = tasks,
                                isDarkMode = isDarkMode,
                                is24Hour = is24Hour,
                                onToggleTheme = {
                                    isDarkMode = !isDarkMode
                                    prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply()
                                },
                                onToggleTask = { task ->
                                    val newStatus = !task.isCompleted
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        taskDao.setTaskCompleted(task.id, newStatus)
                                    }
                                    if (newStatus) {
                                        VibrationHelper.triggerRapidVibration(this@MainActivity)
                                    }
                                },
                                onToggleSound = { task ->
                                    val updated = task.copy(ringSound = !task.ringSound)
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        taskDao.updateTask(updated)
                                        if (updated.scheduledTime.isNotBlank()) {
                                            AlarmScheduler.scheduleTaskAlarm(this@MainActivity, updated)
                                        }
                                    }
                                },
                                onToggleSubtask = { task, idx ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            val arr = org.json.JSONArray(task.subtasksJson)
                                            if (idx in 0 until arr.length()) {
                                                val obj = arr.getJSONObject(idx)
                                                obj.put("done", !obj.optBoolean("done", false))
                                                val updated = task.copy(subtasksJson = arr.toString())
                                                taskDao.updateTask(updated)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                },
                                onUpdateTask = { task ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        taskDao.updateTask(task)
                                        if (task.scheduledTime.isNotBlank()) {
                                            AlarmScheduler.scheduleTaskAlarm(this@MainActivity, task)
                                        } else {
                                            AlarmScheduler.cancelTaskAlarm(this@MainActivity, task)
                                        }
                                    }
                                },
                                onDeleteTask = { task ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        AlarmScheduler.cancelTaskAlarm(this@MainActivity, task)
                                        taskDao.deleteTask(task)
                                    }
                                },
                                onAddTask = { title, desc, cat, priority, time, sound ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val finalTime = com.discipline.os.util.TimeHelper.normalizeTo24Hour(time)
                                        val newTask = Task(
                                            title = title,
                                            description = desc,
                                            category = cat,
                                            priority = priority,
                                            scheduledTime = finalTime,
                                            ringSound = sound,
                                            estimatedMinutes = 60
                                        )
                                        val newId = taskDao.insertTask(newTask)
                                        if (finalTime.isNotBlank()) {
                                            AlarmScheduler.scheduleTaskAlarm(this@MainActivity, newTask.copy(id = newId))
                                        }
                                    }
                                },
                                onShowHistory = {
                                    showHistoryDialog = true
                                },
                                onOpenAi = {
                                    selectedTab = 2
                                }
                            )
                            1 -> VideoScreen(
                                videos = videoList,
                                onAddVideo = { title, url, cat, reminderEpoch, reminderDelayText, reminderType, notes ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val newVideo = VideoEntry(
                                            title = title,
                                            url = url,
                                            category = cat,
                                            reminderEpochMs = reminderEpoch,
                                            reminderDelayText = reminderDelayText,
                                            reminderType = reminderType,
                                            notes = notes
                                        )
                                        val vid = videoDao.insertVideo(newVideo)
                                        if (reminderEpoch > 0) {
                                            AlarmScheduler.scheduleVideoReminder(this@MainActivity, newVideo.copy(id = vid))
                                        }
                                    }
                                },
                                onToggleWatched = { video ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        videoDao.setVideoWatched(video.id, !video.isWatched)
                                    }
                                },
                                onDeleteVideo = { video ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        videoDao.deleteVideo(video)
                                    }
                                },
                                initialUrl = sharedVideoUrl,
                                initialTitle = sharedVideoTitle,
                                onClearInitial = {
                                    sharedVideoUrl = ""
                                    sharedVideoTitle = ""
                                }
                            )
                            2 -> AgentScreen(
                                engine = agentEngine,
                                onBack = { selectedTab = 0 },
                                onShowHistory = { showHistoryDialog = true }
                            )
                            3 -> FuelScreen(
                                fuelList = fuelList,
                                onAddFuel = { person, vow, cat ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        fuelDao.insertFuel(
                                            FuelEntry(
                                                personOrIncident = person,
                                                defianceVow = vow,
                                                category = cat
                                            )
                                        )
                                    }
                                },
                                onDeleteFuel = { entry ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        fuelDao.deleteFuel(entry)
                                    }
                                }
                            )
                            4 -> SystemScreen(
                                totalTasks = tasks.size,
                                completedTasks = tasks.count { it.isCompleted },
                                totalVideos = videoList.size,
                                totalFuel = fuelList.size,
                                onManualResetToday = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        tasks.forEach { task ->
                                            if (task.isCompleted) {
                                                taskDao.setTaskCompleted(task.id, false)
                                            }
                                        }
                                    }
                                },
                                isDarkMode = isDarkMode,
                                onToggleTheme = {
                                    isDarkMode = !isDarkMode
                                    prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply()
                                },
                                is24Hour = is24Hour,
                                onToggleTimeFormat = {
                                    is24Hour = !is24Hour
                                    prefs.edit().putBoolean("is_24_hour_format", is24Hour).apply()
                                },
                                onDeduplicateData = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val count = AppDatabase.deduplicateDatabase(db)
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(
                                                this@MainActivity,
                                                if (count > 0) "Purged $count duplicate entries!" else "Database is clean! 0 duplicates found.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onShowHistory = {
                                    showHistoryDialog = true
                                },
                                onClearHistory = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        dailyLogDao.clearAllLogs()
                                    }
                                }
                            )
                        }
                    }

                    // Floating Capsule Dock (Apple / TripGlide Style with 5 First-Class Tabs)
                    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                    if (imeBottom == 0.dp && selectedTab != 2) {
                        FloatingBottomDock(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 16.dp)
                        )
                    }
                }

                // OTA Update Pop-up Dialog
                if (showUpdateDialog && pendingUpdateInfo != null) {
                    UpdateDialog(
                        updateInfo = pendingUpdateInfo!!,
                        onDismiss = { showUpdateDialog = false }
                    )
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val title = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
            if (text.isNotBlank()) {
                sharedVideoUrl = text.trim()
                sharedVideoTitle = title.trim()
            }
        }
    }
}

/**
 * Floating Stadium Capsule Navigation Dock
 * 5 Tabs: Protocols (0), Vault (1), Discipline AI (2), Fuel (3), System/Control (4)
 */
@Composable
fun FloatingBottomDock(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .shadow(elevation = 16.dp, shape = CircleShape, spotColor = Color(0x66000000))
                .clip(CircleShape)
                .background(colors.dockBg)
                .border(if (colors.isDark) 1.dp else 0.dp, colors.dockBorder, CircleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tab 0: Home / Protocols
            DockItem(
                isSelected = selectedTab == 0,
                icon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
                contentDescription = "Protocols",
                onClick = { onTabSelected(0) }
            )

            // Tab 1: Vault / Study
            DockItem(
                isSelected = selectedTab == 1,
                icon = Icons.Outlined.PlayCircle,
                selectedIcon = Icons.Filled.PlayArrow,
                contentDescription = "Vault",
                onClick = { onTabSelected(1) }
            )

            // Tab 2: Discipline AI (Autonomous Agent)
            DockItem(
                isSelected = selectedTab == 2,
                icon = Icons.Outlined.AutoAwesome,
                selectedIcon = Icons.Filled.AutoAwesome,
                contentDescription = "Discipline AI",
                onClick = { onTabSelected(2) }
            )

            // Tab 3: Fuel / Prove Them Wrong
            DockItem(
                isSelected = selectedTab == 3,
                icon = Icons.Outlined.FavoriteBorder,
                selectedIcon = Icons.Filled.Favorite,
                contentDescription = "Fuel",
                onClick = { onTabSelected(3) }
            )

            // Tab 4: System / Sync & Control Center
            DockItem(
                isSelected = selectedTab == 4,
                icon = Icons.Outlined.GridView,
                selectedIcon = Icons.Filled.GridView,
                contentDescription = "System",
                onClick = { onTabSelected(4) }
            )
        }
    }
}

@Composable
fun DockItem(
    isSelected: Boolean,
    icon: ImageVector,
    selectedIcon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    val size = 46.dp
    if (isSelected) {
        // Active Tab: High-Contrast Circle with Dark Icon
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.dockActiveCircle)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = selectedIcon,
                contentDescription = contentDescription,
                tint = colors.dockActiveIcon,
                modifier = Modifier.size(22.dp)
            )
        }
    } else {
        // Inactive Tab
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.dockInactiveIcon,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
