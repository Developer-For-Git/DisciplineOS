package com.discipline.os.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.discipline.os.data.DailyLog
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

data class PastDayTaskItem(
    val title: String,
    val category: String,
    val priority: Int,
    val isCompleted: Boolean,
    val scheduledTime: String
)

@Composable
fun HistoryDialog(
    dailyLogs: List<DailyLog>,
    onDismiss: () -> Unit
) {
    // Keep track of which date cards are expanded (default to expanding the first/latest one)
    var expandedDate by remember(dailyLogs) {
        mutableStateOf(dailyLogs.firstOrNull()?.date ?: "")
    }

    val avgScore = remember(dailyLogs) {
        if (dailyLogs.isNotEmpty()) {
            dailyLogs.map { it.percentage }.average().toInt()
        } else 0
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 1. Header with Badge & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AccentCyanSoft)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📈", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EXECUTION HISTORY",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CanvasBg)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title & Subtitle
                Text(
                    text = "Past Days & Progress",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Audit your daily compounding consistency & discipline",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Summary Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CanvasBg)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "TOTAL LOGGED", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${dailyLogs.size} Days", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderSubtle))
                    Column {
                        Text(text = "AVERAGE SCORE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "$avgScore%", color = AccentCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderSubtle))
                    Column {
                        Text(text = "STATUS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (avgScore >= 70) "Disciplined 🔥" else "In Progress ⚡",
                            color = if (avgScore >= 70) SuccessGreen else Color(0xFFFFB74D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Scrollable List of Past Days
                if (dailyLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🗓️", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No past day history logged yet",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Complete habits today. At midnight or upon daily reset, your progress will be preserved here permanently!",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(dailyLogs) { index, log ->
                            PastDayCard(
                                log = log,
                                isExpanded = expandedDate == log.date,
                                onToggleExpand = {
                                    expandedDate = if (expandedDate == log.date) "" else log.date
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Done Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryActionBg,
                        contentColor = PrimaryActionFg
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close History", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PastDayCard(
    log: DailyLog,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val tasks = remember(log.tasksSnapshotJson) {
        parseTasksSnapshot(log.tasksSnapshotJson)
    }

    val completedTasks = remember(tasks) { tasks.filter { it.isCompleted } }
    val missedTasks = remember(tasks) { tasks.filter { !it.isCompleted } }

    val formattedDate = remember(log.date) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = sdf.parse(log.date)
            val today = sdf.format(Date())
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = sdf.format(cal.time)

            val displayFormat = SimpleDateFormat("EEEE, MMM d", Locale.US).format(parsed ?: Date())
            when (log.date) {
                today -> "Today • $displayFormat"
                yesterday -> "Yesterday • $displayFormat"
                else -> displayFormat
            }
        } catch (_: Exception) {
            log.date
        }
    }

    val scoreColor = when {
        log.percentage >= 75f -> SuccessGreen
        log.percentage >= 50f -> AccentCyan
        else -> AccentFlame
    }

    val scoreSoftColor = when {
        log.percentage >= 75f -> SuccessGreenSoft
        log.percentage >= 50f -> AccentCyanSoft
        else -> AccentFlameSoft
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isExpanded) scoreColor.copy(alpha = 0.5f) else BorderSubtle, RoundedCornerShape(20.dp))
            .clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Date & Score Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formattedDate,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${log.completedCount} of ${log.totalCount} habits executed",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Score Pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scoreSoftColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${log.percentage.toInt()}%",
                        color = scoreColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { (log.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = scoreColor,
                trackColor = CanvasBg
            )

            // Expanded Breakdown: Completed & Missed Habits
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tasks.isEmpty()) {
                        Text(
                            text = "Score recorded: ${log.completedCount}/${log.totalCount} habits completed.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        // 1. Completed Habits Header
                        if (completedTasks.isNotEmpty()) {
                            Text(
                                text = "COMPLETED (${completedTasks.size})",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )

                            completedTasks.forEach { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SuccessGreenSoft.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (task.scheduledTime.isNotBlank()) {
                                            Text(
                                                text = "${task.category} • ${task.scheduledTime}",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Missed Habits Header
                        if (missedTasks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "MISSED (${missedTasks.size})",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )

                            missedTasks.forEach { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CanvasBg)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Cancel,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (task.scheduledTime.isNotBlank()) {
                                            Text(
                                                text = "${task.category} • ${task.scheduledTime}",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseTasksSnapshot(json: String?): List<PastDayTaskItem> {
    if (json.isNullOrBlank() || json == "[]") return emptyList()
    val list = mutableListOf<PastDayTaskItem>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                PastDayTaskItem(
                    title = obj.optString("title", "Habit"),
                    category = obj.optString("category", "Habit"),
                    priority = obj.optInt("priority", 2),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    scheduledTime = obj.optString("scheduledTime", "")
                )
            )
        }
    } catch (_: Exception) {}
    return list
}
