package com.discipline.os.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.discipline.os.alarm.VibrationHelper
import com.discipline.os.telemetry.AppCategory
import com.discipline.os.telemetry.AppUsageTracker
import com.discipline.os.telemetry.DeviceControlManager
import com.discipline.os.telemetry.StepTracker
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryScreen(
    protocolCompletionRate: Float = 0f,
    onOpenAi: () -> Unit = {},
    showHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { DeviceControlManager.getInstance(context) }
    val stepTracker = remember { StepTracker.getInstance(context) }

    val telemetry by manager.telemetry.collectAsState()
    val stepStats by stepTracker.stepStats.collectAsState()

    var showGoalDialog by remember { mutableStateOf(false) }

    // Auto-refresh telemetry periodically and on resume
    LaunchedEffect(Unit) {
        while (true) {
            manager.refreshTelemetry(protocolCompletionRate)
            delay(5000)
        }
    }

    val todayFormatted = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date())
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = if (showHeader) 16.dp else 6.dp, bottom = (if (navBarBottom > 48.dp) navBarBottom else 48.dp) + 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header (Optional)
        if (showHeader) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Column {
                    Text(
                        text = "Life Telemetry & Control",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Physical movement, screen time & data audit",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Quick Refresh Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CardWhite)
                        .border(1.5.dp, BorderSubtle, CircleShape)
                        .clickable {
                            manager.refreshTelemetry(protocolCompletionRate)
                            VibrationHelper.triggerRapidVibration(context)
                            Toast.makeText(context, "Telemetry Radar Refreshed", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        }

        // 2. Discipline Index Hero Card
        item {
            val scoreProgress by animateFloatAsState(
                targetValue = (telemetry.disciplineScore / 100f).coerceIn(0f, 1f),
                label = "scoreAnim"
            )

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
                            text = "DISCIPLINE RADAR INDEX",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SuccessGreenSoft)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = telemetry.focusStatus,
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${telemetry.disciplineScore}",
                                    color = TextPrimary,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1.5).sp
                                )
                                Text(
                                    text = " / 100",
                                    color = TextMuted,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Text(
                                text = "$todayFormatted • Live Telemetry",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        // Circular Mini Indicator
                        Box(
                            modifier = Modifier.size(68.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = BorderSubtle,
                                strokeWidth = 7.dp,
                                trackColor = Color.Transparent
                            )
                            CircularProgressIndicator(
                                progress = { scoreProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = if (telemetry.disciplineScore >= 70) SuccessGreen else AccentFlame,
                                strokeWidth = 7.dp,
                                trackColor = Color.Transparent
                            )
                            Text(
                                text = "${telemetry.disciplineScore}%",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3-Pillar Breakdown Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Protocol pillar
                        TelemetryPillarPill(
                            title = "Protocols",
                            value = "${(protocolCompletionRate * 100).toInt()}%",
                            weight = "40 pts",
                            modifier = Modifier.weight(1f)
                        )

                        // Walking pillar
                        TelemetryPillarPill(
                            title = "Steps",
                            value = "${stepStats.todaySteps}",
                            weight = "30 pts",
                            modifier = Modifier.weight(1f)
                        )

                        // Screen Time pillar
                        TelemetryPillarPill(
                            title = "Focus",
                            value = if (telemetry.screenTime.hasPermission) "${telemetry.screenTime.productivePercentage.toInt()}%" else "N/A",
                            weight = "30 pts",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. Physical Walking Activity Card (Hardware Sensor Engine)
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AccentFlameSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsWalk,
                                    contentDescription = "Walking",
                                    tint = AccentFlame,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Physical Walking Activity",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (stepStats.hasHardwareSensor) "Hardware Pedometer Active" else "Step Sensor Active",
                                    color = if (stepStats.hasHardwareSensor) SuccessGreen else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Goal Adjuster button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SecondaryActionBg)
                                .border(1.dp, BorderSubtle, CircleShape)
                                .clickable { showGoalDialog = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Goal: ${stepStats.stepGoal}",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step Big Numbers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "%,d".format(stepStats.todaySteps),
                                    color = TextPrimary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1.0).sp
                                )
                                Text(
                                    text = " steps",
                                    color = TextSecondary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                )
                            }
                            Text(
                                text = "${stepStats.progressPercent}% of ${stepStats.stepGoal} daily goal",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BorderSubtle)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((stepStats.progressFraction).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (stepStats.progressFraction >= 1f) SuccessGreen else AccentFlame)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3-Metric Row: Distance, Calories, Active Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryMetricItem(
                            icon = Icons.Outlined.NearMe,
                            label = "Distance",
                            value = String.format(Locale.US, "%.2f km", stepStats.distanceKm),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricItem(
                            icon = Icons.Outlined.LocalFireDepartment,
                            label = "Calories",
                            value = "${stepStats.caloriesKcal} kcal",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricItem(
                            icon = Icons.Outlined.Timer,
                            label = "Active Time",
                            value = "${stepStats.activeMinutes} min",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Emulator / Live Testing Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // +500 steps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(SecondaryActionBg)
                                .border(1.dp, BorderSubtle, CircleShape)
                                .clickable {
                                    stepTracker.addManualSteps(500)
                                    manager.refreshTelemetry(protocolCompletionRate)
                                    VibrationHelper.triggerRapidVibration(context)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+500 Steps",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // +1000 steps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(PrimaryActionBg)
                                .clickable {
                                    stepTracker.addManualSteps(1000)
                                    manager.refreshTelemetry(protocolCompletionRate)
                                    VibrationHelper.triggerRapidVibration(context)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+1,000 Steps",
                                color = PrimaryActionFg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Digital Life & Screen Time Audit Card
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AccentCyanSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = "Screen Time",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "App Screen Time Radar",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Productive vs Distraction Ratio",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Permission indicator
                        if (telemetry.screenTime.hasPermission) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SuccessGreenSoft)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Active",
                                    color = SuccessGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!telemetry.screenTime.hasPermission) {
                        // Permission Request Obsidian Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardElevated)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LockClock,
                                    contentDescription = "Permission",
                                    tint = AccentFlame,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Special Access Required",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Grant Android Usage Stats access so DisciplineOS can audit which apps you use and calculate your productive ratio.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { AppUsageTracker.openUsageAccessSettings(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryActionBg,
                                    contentColor = PrimaryActionFg
                                ),
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Grant Usage Stats Access ⚙️",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Full Screen Time Stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = telemetry.screenTime.formattedTotalTime,
                                    color = TextPrimary,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.8).sp
                                )
                                Text(
                                    text = "Total Screen Time Today",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${telemetry.screenTime.productivePercentage.toInt()}%",
                                    color = SuccessGreen,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Productive Ratio",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Visual Split Bar (Productive vs Distraction)
                        val prodRatio = (telemetry.screenTime.productivePercentage / 100f).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(BorderSubtle)
                        ) {
                            if (prodRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(prodRatio)
                                        .fillMaxHeight()
                                        .background(SuccessGreen)
                                )
                            }
                            if (1f - prodRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f - prodRatio)
                                        .fillMaxHeight()
                                        .background(AccentFlame)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Productive vs Distraction breakdown chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Productive: ${telemetry.screenTime.formattedProductiveTime}",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentFlame)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Distraction: ${telemetry.screenTime.formattedDistractionTime}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Top Apps List
                        if (telemetry.screenTime.topApps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "TOP APPS USED TODAY",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            telemetry.screenTime.topApps.take(6).forEach { app ->
                                AppUsageRow(app = app)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }

        // 5. Network & Data Consumption Card
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreenSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = "Data",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Network & Data Usage",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hardware TrafficStats engine",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Connection Type Badge
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SecondaryActionBg)
                                .border(1.dp, BorderSubtle, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = telemetry.network.connectionType,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = telemetry.network.formattedTotal,
                                color = TextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Total Network Traffic (Boot)",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryMetricItem(
                            icon = Icons.Outlined.Wifi,
                            label = "Wi-Fi Data",
                            value = telemetry.network.formattedWifi,
                            modifier = Modifier.weight(1f)
                        )

                        TelemetryMetricItem(
                            icon = Icons.Outlined.SignalCellularAlt,
                            label = "Cellular Data",
                            value = telemetry.network.formattedMobile,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 6. Time Investment Daily Audit Summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardElevated),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(26.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Audit",
                            tint = PrimaryActionBg,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily Time Investment Audit",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "You have walked ${String.format(Locale.US, "%.2f", stepStats.distanceKm)} km (${stepStats.todaySteps} steps), logged ${telemetry.screenTime.formattedTotalTime} screen time (${telemetry.screenTime.productivePercentage.toInt()}% productive focus), and maintained a ${telemetry.disciplineScore}/100 Discipline Index.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onOpenAi,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryActionBg,
                            contentColor = PrimaryActionFg
                        ),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Coach",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ask AI Coach About My Day",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Step Goal Dialog
    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            containerColor = CardWhite,
            title = {
                Text(
                    text = "Select Daily Step Goal",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5000, 8000, 10000, 12000, 15000, 20000).forEach { goal ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (stepStats.stepGoal == goal) PrimaryActionBg else SecondaryActionBg)
                                .clickable {
                                    stepTracker.setStepGoal(goal)
                                    showGoalDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = "%,d steps".format(goal),
                                color = if (stepStats.stepGoal == goal) PrimaryActionFg else TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Close", color = TextPrimary)
                }
            }
        )
    }
}

@Composable
fun TelemetryPillarPill(
    title: String,
    value: String,
    weight: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = weight,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
fun TelemetryMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun AppUsageRow(app: com.discipline.os.telemetry.AppUsageItem) {
    val categoryColor = when (app.category) {
        AppCategory.PRODUCTIVE -> SuccessGreen
        AppCategory.DISTRACTION -> AccentFlame
        AppCategory.UTILITY -> AccentCyan
    }

    val categoryBg = when (app.category) {
        AppCategory.PRODUCTIVE -> SuccessGreenSoft
        AppCategory.DISTRACTION -> AccentFlameSoft
        AppCategory.UTILITY -> AccentCyanSoft
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(categoryBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.appName.take(1).uppercase(),
                    color = categoryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = app.appName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = categoryColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = app.formattedTime,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
