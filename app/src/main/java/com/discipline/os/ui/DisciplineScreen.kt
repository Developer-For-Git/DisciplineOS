package com.discipline.os.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.discipline.os.alarm.VibrationHelper
import com.discipline.os.data.Task
import com.discipline.os.util.TimeHelper
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DisciplineScreen(
    tasks: List<Task>,
    isDarkMode: Boolean = true,
    is24Hour: Boolean = false,
    onToggleTheme: () -> Unit = {},
    onToggleTask: (Task) -> Unit,
    onToggleSound: (Task) -> Unit,
    onToggleSubtask: (Task, Int) -> Unit,
    onUpdateTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onAddTask: (String, String, String, Int, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val categoryScrollState = rememberScrollState()

    var liveTime by remember { mutableStateOf(TimeHelper.getCurrentLiveTime(is24Hour)) }
    LaunchedEffect(is24Hour) {
        while (true) {
            liveTime = TimeHelper.getCurrentLiveTime(is24Hour)
            kotlinx.coroutines.delay(1000L)
        }
    }

    val categories = remember(tasks) {
        listOf("All") + tasks.map { it.category }.distinct()
    }

    val filteredTasks = remember(tasks, selectedCategory, searchQuery) {
        tasks.distinctBy { it.id }.filter { task ->
            val matchCat = selectedCategory == "All" || task.category.equals(selectedCategory, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                    task.title.contains(searchQuery, ignoreCase = true) ||
                    task.description.contains(searchQuery, ignoreCase = true) ||
                    task.category.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }.sortedWith(
            compareBy<Task> { it.isCompleted }
                .thenBy { TimeHelper.getMinutesFromMidnight(it.scheduledTime) }
                .thenBy { it.priority }
                .thenBy { it.sortOrder }
                .thenBy { it.id }
        )
    }

    val completedCount = remember(tasks) { tasks.distinctBy { it.id }.count { it.isCompleted } }
    val totalCount = remember(tasks) { tasks.distinctBy { it.id }.size }
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val urgentRemaining = remember(tasks) { tasks.distinctBy { it.id }.count { it.priority == 1 && !it.isCompleted } }

    val todayFormatted = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date())
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Top-level column: Header & Search are fixed at the top to eliminate IME detachment and scroll lag!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
    ) {
        // Pinned Header Section (Never recycled, zero scroll lag, always accessible)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Header (Greeting + Theme Toggle + Add Protocol)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, Warrior",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SuccessGreenSoft)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = liveTime,
                                    color = SuccessGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• DisciplineOS",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Theme Mode Toggle (Sun for dark, Moon for light)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CardWhite)
                            .border(1.5.dp, BorderSubtle, CircleShape)
                            .clickable { onToggleTheme() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightlightRound,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkMode) Color(0xFFFFB74D) else TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Add Protocol Circle Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CardWhite)
                            .border(1.5.dp, BorderSubtle, CircleShape)
                            .clickable { showAddDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Protocol",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 2. Search & Filter Bar (Fixed at top -> zero IME disconnect lag)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(CardWhite)
                    .border(1.dp, BorderSubtle, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search protocols, tags...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                // Filter / Add Button on Right
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PrimaryActionBg)
                        .clickable { showAddDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Filter",
                        tint = PrimaryActionFg,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Smooth Scrollable Feed (Buttery-smooth 60/120 FPS!)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = (if (navBarBottom > 48.dp) navBarBottom else 48.dp) + 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 3. Category Filter Selector (Lightweight horizontal scroll row)
            item(contentType = "category_selector") {
                Column {
                    Text(
                        text = "Select category",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(categoryScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) PrimaryActionBg else CardWhite)
                                    .border(
                                        1.dp,
                                        if (isSelected) PrimaryActionBg else BorderSubtle,
                                        CircleShape
                                    )
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = if (cat == "All") "All" else cat,
                                    color = if (isSelected) PrimaryActionFg else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 4. Hero Featured Card (Today's Execution)
            item(contentType = "hero_card") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(26.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TODAY'S EXECUTION",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )

                            // Heart / Pulse button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CanvasBg)
                                    .border(1.dp, BorderSubtle, CircleShape)
                                    .clickable { VibrationHelper.triggerRapidVibration(context) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Pulse",
                                    tint = AccentFlame,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${(progress * 100).toInt()}% Discipline Score",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "$completedCount of $totalCount protocols completed • $todayFormatted • $liveTime",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BorderSubtle)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (progress >= 1f) SuccessGreen else PrimaryActionBg)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom Pill Bar inside Hero Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(PrimaryActionBg)
                                .clickable { VibrationHelper.triggerRapidVibration(context) }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (urgentRemaining > 0) "🔥 $urgentRemaining critical tasks need action" else "All critical tasks completed",
                                    color = PrimaryActionFg,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryActionFg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Action",
                                    tint = PrimaryActionBg,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Protocols Section Header
            item(contentType = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming protocols",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "See all (${filteredTasks.size})",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 6. Fast & Smooth Protocol Cards
            items(
                items = filteredTasks,
                key = { it.id },
                contentType = { "protocol_card" }
            ) { task ->
                ModernProtocolCard(
                    task = task,
                    is24Hour = is24Hour,
                    onToggle = { onToggleTask(task) },
                    onToggleSound = { onToggleSound(task) },
                    onToggleSubtask = { idx -> onToggleSubtask(task, idx) },
                    onEdit = { taskToEdit = task }
                )
            }
        }
    }

    if (showAddDialog) {
        ModernTaskDialog(
            task = null,
            is24Hour = is24Hour,
            isDarkMode = isDarkMode,
            navBarBottom = navBarBottom,
            statusBarTop = statusBarTop,
            onDismiss = { showAddDialog = false },
            onSave = { updated ->
                onAddTask(
                    updated.title,
                    updated.description,
                    updated.category,
                    updated.priority,
                    updated.scheduledTime,
                    updated.ringSound
                )
                showAddDialog = false
            },
            onDelete = {}
        )
    }

    taskToEdit?.let { task ->
        ModernTaskDialog(
            task = task,
            is24Hour = is24Hour,
            isDarkMode = isDarkMode,
            navBarBottom = navBarBottom,
            statusBarTop = statusBarTop,
            onDismiss = { taskToEdit = null },
            onSave = { updated ->
                onUpdateTask(updated)
                taskToEdit = null
            },
            onDelete = {
                onDeleteTask(task)
                taskToEdit = null
            }
        )
    }
}

private val ProtocolCardShape = RoundedCornerShape(22.dp)
private val BadgeCornerShape = RoundedCornerShape(6.dp)

/**
 * Highly-Optimized Protocol Card
 * Zero heavy shadow allocations, lightweight expansion
 */
@Composable
fun ModernProtocolCard(
    task: Task,
    is24Hour: Boolean = false,
    onToggle: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleSubtask: (Int) -> Unit,
    onEdit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Lightweight parsing: only parse subtasks array when non-empty
    val subtaskSummary = remember(task.subtasksJson) {
        val json = task.subtasksJson
        if (json.length <= 4) {
            null
        } else {
            try {
                val arr = JSONArray(json)
                val len = arr.length()
                if (len == 0) return@remember null
                var done = 0
                for (i in 0 until len) {
                    if (arr.getJSONObject(i).optBoolean("done", false)) done++
                }
                "$done/$len"
            } catch (_: Exception) {
                null
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = ProtocolCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (task.isCompleted) SuccessGreen.copy(alpha = 0.4f) else BorderSubtle,
                ProtocolCardShape
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Checkbox
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (task.isCompleted) SuccessGreen else CanvasBg)
                        .border(
                            1.5.dp,
                            if (task.isCompleted) SuccessGreen else BorderDivider,
                            CircleShape
                        )
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title & Metadata
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                ) {
                    Text(
                        text = task.title,
                        color = if (task.isCompleted) TextSecondary else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (pBg, pFg, pText) = when (task.priority) {
                            1 -> Triple(AccentFlameSoft, AccentFlame, "P1 🔥")
                            2 -> Triple(AccentCyanSoft, AccentCyan, "P2")
                            else -> Triple(CanvasBg, TextSecondary, "P3")
                        }
                        Box(
                            modifier = Modifier
                                .clip(BadgeCornerShape)
                                .background(pBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = pText,
                                color = pFg,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (task.scheduledTime.isNotBlank()) {
                            val displayTime = TimeHelper.formatDisplayTime(task.scheduledTime, is24Hour)
                            val periodTag = TimeHelper.getPeriodTag(task.scheduledTime)
                            val periodEmoji = TimeHelper.getPeriodEmoji(task.scheduledTime)
                            val periodLabel = when {
                                periodTag == "Bedtime" -> "$displayTime • Bedtime"
                                periodTag == "College" -> "$displayTime • College"
                                periodTag == "Morning" -> "$displayTime • Morning"
                                periodTag == "Evening" -> "$displayTime • Evening"
                                else -> displayTime
                            }
                            Box(
                                modifier = Modifier
                                    .clip(BadgeCornerShape)
                                    .background(CanvasBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = periodEmoji,
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = periodLabel,
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(BadgeCornerShape)
                                    .background(CanvasBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🛡️",
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "All-Day Habit",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (subtaskSummary != null) {
                            Box(
                                modifier = Modifier
                                    .clip(BadgeCornerShape)
                                    .background(CanvasBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "☑ $subtaskSummary",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Expand Chevron Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CanvasBg)
                        .border(1.dp, BorderSubtle, CircleShape)
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Details (Only constructed when expanded)
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    HorizontalDivider(color = BorderDivider, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Parse full subtasks list on-demand
                    val fullSubtasks = remember(task.subtasksJson) {
                        val list = mutableListOf<Pair<String, Boolean>>()
                        try {
                            val arr = JSONArray(task.subtasksJson)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                list.add(obj.getString("text") to obj.optBoolean("done", false))
                            }
                        } catch (_: Exception) {}
                        list
                    }

                    if (fullSubtasks.isNotEmpty()) {
                        Text(
                            text = "CHECKLIST",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        fullSubtasks.forEachIndexed { idx, (text, done) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleSubtask(idx) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (done) SuccessGreen else CanvasBg)
                                        .border(1.dp, if (done) SuccessGreen else BorderDivider, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (done) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = text,
                                    color = if (done) TextMuted else TextPrimary,
                                    fontSize = 13.sp,
                                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onToggleSound() },
                            colors = ButtonDefaults.buttonColors(containerColor = CanvasBg),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (task.ringSound) Icons.Default.VolumeUp else Icons.Default.Vibration,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (task.ringSound) "Sound" else "Silent Pulse",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onEdit() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryActionBg),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryActionFg, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit", color = PrimaryActionFg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Clean Dialog for Add/Edit
 */
/**
 * Full-Screen Modern Protocol Editor with Luxury In-App Time & Bedtime Engine
 * Zero legacy Android dialogs, full-height scrollable, keyboard-safe layout
 */
@Composable
fun ModernTaskDialog(
    task: Task?,
    is24Hour: Boolean = false,
    isDarkMode: Boolean = true,
    navBarBottom: androidx.compose.ui.unit.Dp = 48.dp,
    statusBarTop: androidx.compose.ui.unit.Dp = 24.dp,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var category by remember { mutableStateOf(task?.category ?: "Coding") }
    var priority by remember { mutableIntStateOf(task?.priority ?: 1) }
    var scheduledTime by remember { mutableStateOf(task?.scheduledTime ?: "20:27") }
    var ringSound by remember { mutableStateOf(task?.ringSound ?: false) }

    // Normalize scheduledTime for internal digital display calculations
    val normalizedTime = remember(scheduledTime) {
        if (scheduledTime.isBlank()) "20:27" else TimeHelper.normalizeTo24Hour(scheduledTime)
    }
    val parts = remember(normalizedTime) { normalizedTime.split(":") }
    val hour24 = parts.getOrNull(0)?.toIntOrNull() ?: 20
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 27

    val isPm = hour24 >= 12
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12

    val displayFormattedTime = remember(scheduledTime, is24Hour) {
        if (scheduledTime.isNotBlank()) TimeHelper.formatDisplayTime(scheduledTime, is24Hour) else ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let { win ->
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                win.setWindowAnimations(0)
                WindowCompat.getInsetsController(win, win.decorView).apply {
                    isAppearanceLightStatusBars = !isDarkMode
                    isAppearanceLightNavigationBars = !isDarkMode
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasBg)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (statusBarTop > 16.dp) statusBarTop + 6.dp else 24.dp
                    )
            ) {
                // 1. Full-Screen Modern Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close Circle Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CardWhite)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Screen Title
                    Text(
                        text = if (task == null) "New Protocol" else "Edit Protocol",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    // Save Action Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = PrimaryActionBg,
                        modifier = Modifier.clickable {
                            if (title.isNotBlank()) {
                                val finalTime = if (scheduledTime.isNotBlank()) {
                                    TimeHelper.normalizeTo24Hour(scheduledTime)
                                } else ""
                                val updated = task?.copy(
                                    title = title,
                                    description = description,
                                    category = category,
                                    priority = priority,
                                    scheduledTime = finalTime,
                                    ringSound = ringSound
                                ) ?: Task(
                                    title = title,
                                    description = description,
                                    category = category,
                                    priority = priority,
                                    scheduledTime = finalTime,
                                    ringSound = ringSound
                                )
                                onSave(updated)
                            }
                        }
                    ) {
                        Text(
                            text = "Save",
                            color = PrimaryActionFg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // 2. Full-Screen Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title Input
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Protocol Title", fontSize = 13.sp) },
                        placeholder = { Text("e.g. Master C Assembly, Bedtime Reading") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite,
                            focusedBorderColor = PrimaryActionBg,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = TextPrimary,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = PrimaryActionBg
                        )
                    )

                    // Details / Purpose Input
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Details & Purpose", fontSize = 13.sp) },
                        placeholder = { Text("Why this protocol matters for compounding success") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite,
                            focusedBorderColor = PrimaryActionBg,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = TextPrimary,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = PrimaryActionBg
                        )
                    )

                    // Category Quick Selector Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Category",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Coding", "Health", "College", "Discipline", "Bedtime", "Mindset").forEach { cat ->
                                val isSelected = category.equals(cat, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) PrimaryActionBg else CardWhite,
                                    border = BorderStroke(1.dp, if (isSelected) PrimaryActionBg else BorderSubtle),
                                    modifier = Modifier.clickable { category = cat }
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) PrimaryActionFg else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Priority Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Priority Level",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(
                                Triple(1, "🔥 P1 Urgent", AccentFlame),
                                Triple(2, "⚡ P2 Focus", AccentCyan),
                                Triple(3, "🎯 P3 Routine", SuccessGreen)
                            ).forEach { (pVal, pLabel, pColor) ->
                                val isSelected = priority == pVal
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) pColor.copy(alpha = 0.15f) else CardWhite,
                                    border = BorderStroke(1.5.dp, if (isSelected) pColor else BorderSubtle),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { priority = pVal }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pLabel,
                                            color = if (isSelected) pColor else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. ULTRA-MODERN TIME & BEDTIME CARD (No Old View Dialogs!)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(AccentCyanSoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "SCHEDULED TIME",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = if (scheduledTime.isNotBlank()) displayFormattedTime else "Not scheduled",
                                            color = if (scheduledTime.isNotBlank()) SuccessGreen else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                if (scheduledTime.isNotBlank()) {
                                    Text(
                                        text = "Clear Time",
                                        color = AccentFlame,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { scheduledTime = "" }
                                    )
                                } else {
                                    Text(
                                        text = "+ Set Time",
                                        color = AccentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { scheduledTime = "20:27" }
                                    )
                                }
                            }

                            if (scheduledTime.isNotBlank()) {
                                // Routine & Bedtime Quick Presets
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Quick Routine Presets",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "🌅 7:15 AM (Push-ups)" to "07:15",
                                            "🌅 7:30 AM (Coding)" to "07:30",
                                            "🎓 8:45 AM (College)" to "08:45",
                                            "🌇 5:27 PM (Homework)" to "17:27",
                                            "🌙 8:27 PM (Security)" to "20:27",
                                            "💤 10:00 PM Bedtime" to "22:00"
                                        ).forEach { (label, timeVal) ->
                                            val isPresetSelected = TimeHelper.normalizeTo24Hour(scheduledTime) == timeVal
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isPresetSelected) PrimaryActionBg else CardElevated,
                                                border = BorderStroke(1.dp, if (isPresetSelected) PrimaryActionBg else BorderSubtle),
                                                modifier = Modifier.clickable {
                                                    scheduledTime = timeVal
                                                    when (timeVal) {
                                                        "07:15" -> if (category.isBlank() || category == "General") category = "Health"
                                                        "07:30" -> if (category.isBlank() || category == "General") category = "Coding"
                                                        "08:45", "17:27" -> if (category.isBlank() || category == "General") category = "College"
                                                        "20:27" -> if (category.isBlank() || category == "General") category = "Security"
                                                        "22:00" -> if (category.isBlank() || category == "General" || category == "Coding") category = "Bedtime"
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    color = if (isPresetSelected) PrimaryActionFg else TextPrimary,
                                                    fontWeight = if (isPresetSelected) FontWeight.Bold else FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Modern Digital Clock Control Display
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(CardElevated)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                                        .padding(vertical = 14.dp, horizontal = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // HOUR BLOCK with Steppers
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Hour Up
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(CardWhite)
                                                    .clickable {
                                                        val nextH12 = if (hour12 == 12) 1 else hour12 + 1
                                                        val newH24 = if (isPm) (nextH12 % 12) + 12 else (nextH12 % 12)
                                                        scheduledTime = String.format(Locale.US, "%02d:%02d", newH24, minute)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Hour +", tint = TextPrimary, modifier = Modifier.size(18.dp))
                                            }

                                            // Hour Value
                                            Text(
                                                text = String.format(Locale.US, "%02d", hour12),
                                                color = TextPrimary,
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 1.sp
                                            )

                                            // Hour Down
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(CardWhite)
                                                    .clickable {
                                                        val prevH12 = if (hour12 == 1) 12 else hour12 - 1
                                                        val newH24 = if (isPm) (prevH12 % 12) + 12 else (prevH12 % 12)
                                                        scheduledTime = String.format(Locale.US, "%02d:%02d", newH24, minute)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Hour -", tint = TextPrimary, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        // COLON
                                        Text(
                                            text = ":",
                                            color = AccentCyan,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        // MINUTE BLOCK with Steppers
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Minute Up
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(CardWhite)
                                                    .clickable {
                                                        val nextM = (minute + 1) % 60
                                                        scheduledTime = String.format(Locale.US, "%02d:%02d", hour24, nextM)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Minute +", tint = TextPrimary, modifier = Modifier.size(18.dp))
                                            }

                                            // Minute Value
                                            Text(
                                                text = String.format(Locale.US, "%02d", minute),
                                                color = TextPrimary,
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 1.sp
                                            )

                                            // Minute Down
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(CardWhite)
                                                    .clickable {
                                                        val prevM = if (minute == 0) 59 else minute - 1
                                                        scheduledTime = String.format(Locale.US, "%02d:%02d", hour24, prevM)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minute -", tint = TextPrimary, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        // AM / PM TOGGLE PILL
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = CardWhite,
                                            border = BorderStroke(1.dp, BorderSubtle)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // AM Button
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (!isPm) PrimaryActionBg else Color.Transparent,
                                                    modifier = Modifier.clickable {
                                                        if (isPm) {
                                                            val newH = hour12 % 12
                                                            scheduledTime = String.format(Locale.US, "%02d:%02d", newH, minute)
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = "AM",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (!isPm) PrimaryActionFg else TextMuted,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                    )
                                                }

                                                // PM Button
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isPm) PrimaryActionBg else Color.Transparent,
                                                    modifier = Modifier.clickable {
                                                        if (!isPm) {
                                                            val newH = (hour12 % 12) + 12
                                                            scheduledTime = String.format(Locale.US, "%02d:%02d", newH, minute)
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = "PM",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isPm) PrimaryActionFg else TextMuted,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Quick Hour Picker Bar
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Select Hour",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        (1..12).forEach { h ->
                                            val isSelectedHour = hour12 == h
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSelectedHour) PrimaryActionBg else CardElevated,
                                                border = BorderStroke(1.dp, if (isSelectedHour) PrimaryActionBg else BorderSubtle),
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clickable {
                                                        val newH = if (isPm) (h % 12) + 12 else (h % 12)
                                                        scheduledTime = String.format(Locale.US, "%02d:%02d", newH, minute)
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "$h",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelectedHour) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelectedHour) PrimaryActionFg else TextPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Quick Minute Picker Bar (includes :27!)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Select Minute",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(0, 15, 27, 30, 45, 55).forEach { m ->
                                            val isSelectedMin = minute == m
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelectedMin) PrimaryActionBg else CardElevated,
                                                border = BorderStroke(1.dp, if (isSelectedMin) PrimaryActionBg else BorderSubtle),
                                                modifier = Modifier.clickable {
                                                    scheduledTime = String.format(Locale.US, "%02d:%02d", hour24, m)
                                                }
                                            ) {
                                                Text(
                                                    text = String.format(Locale.US, ":%02d", m),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelectedMin) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelectedMin) PrimaryActionFg else TextPrimary,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Shorthand Smart Text Input
                                OutlinedTextField(
                                    value = scheduledTime,
                                    onValueChange = { input ->
                                        val parsed = TimeHelper.normalizeTo24Hour(input)
                                        scheduledTime = if (parsed.isNotBlank() && parsed.contains(":")) parsed else input
                                    },
                                    label = { Text("Shorthand Time (e.g. 527, 827, 10 PM, bedtime)", fontSize = 11.sp) },
                                    placeholder = { Text("527 or 827") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CardElevated,
                                        unfocusedContainerColor = CardElevated,
                                        focusedBorderColor = PrimaryActionBg,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedLabelColor = TextSecondary,
                                        unfocusedLabelColor = TextMuted,
                                        cursorColor = PrimaryActionBg
                                    )
                                )
                            }
                        }
                    }

                    // 4. Silent Vibration Toggle Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Silent Rapid Vibration", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("Awakens via haptic pulse without noisy ringtones", fontSize = 11.sp, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = !ringSound,
                                onCheckedChange = { ringSound = !it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PrimaryActionFg,
                                    checkedTrackColor = PrimaryActionBg
                                )
                            )
                        }
                    }

                    // 5. Destructive Delete Action (if editing existing protocol)
                    if (task != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AccentFlameSoft,
                            border = BorderStroke(1.dp, AccentFlame.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDelete() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = AccentFlame,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Delete This Protocol",
                                    color = AccentFlame,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Dedicated bottom clearance guaranteed to stay strictly above 3-button navigation bar
                    val bottomClearance = (if (navBarBottom > 48.dp) navBarBottom else 48.dp) + 40.dp
                    Spacer(modifier = Modifier.height(bottomClearance))
                }
            }
        }
    }
}
