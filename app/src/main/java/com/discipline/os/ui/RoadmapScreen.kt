package com.discipline.os.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import android.view.ViewGroup
import com.discipline.os.alarm.VibrationHelper
import com.discipline.os.data.Roadmap
import com.discipline.os.data.RoadmapNode
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun RoadmapScreen(
    roadmaps: List<Roadmap>,
    nodes: List<RoadmapNode>,
    onToggleNodeCompleted: (RoadmapNode) -> Unit,
    onSetCurrentNode: (RoadmapNode) -> Unit,
    onToggleChecklistItem: (RoadmapNode, Int) -> Unit,
    onAddRoadmap: (title: String, category: String, desc: String, targetGoal: String) -> Unit,
    onAddNode: (roadmapId: Long, stage: String, title: String, desc: String, criteria: String, checklistJson: String) -> Unit,
    onDeleteNode: (RoadmapNode) -> Unit,
    onDeleteRoadmap: (Roadmap) -> Unit,
    onOpenAi: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = AppTheme.colors

    var selectedRoadmapId by rememberSaveable {
        mutableStateOf(roadmaps.firstOrNull()?.id ?: 0L)
    }

    LaunchedEffect(roadmaps) {
        if (selectedRoadmapId == 0L || roadmaps.none { it.id == selectedRoadmapId }) {
            selectedRoadmapId = roadmaps.firstOrNull()?.id ?: 0L
        }
    }

    val currentRoadmap = remember(roadmaps, selectedRoadmapId) {
        roadmaps.find { it.id == selectedRoadmapId } ?: roadmaps.firstOrNull()
    }

    val roadmapNodes = remember(nodes, currentRoadmap) {
        if (currentRoadmap != null) {
            nodes.filter { it.roadmapId == currentRoadmap.id }.sortedBy { it.stepOrder }
        } else {
            emptyList()
        }
    }

    val stages = remember(roadmapNodes) {
        listOf("All") + roadmapNodes.map { it.stage }.distinct()
    }

    var selectedStageFilter by remember { mutableStateOf("All") }
    val filteredNodes = remember(roadmapNodes, selectedStageFilter) {
        if (selectedStageFilter == "All") {
            roadmapNodes
        } else {
            roadmapNodes.filter { it.stage == selectedStageFilter }
        }
    }

    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showAddRoadmapDialog by remember { mutableStateOf(false) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBg)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = (if (navBarBottom > 48.dp) navBarBottom else 48.dp) + 110.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Bar: Title, Category Selector, New Roadmap Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ROADMAP PROTOCOLS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "Progressive Mastery & Skill Architecture",
                        fontSize = 12.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // AI Quick Assist Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.cardBg)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                            .clickable { onOpenAi() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Ask AI about Roadmap",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // New Roadmap Action
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.primaryActionBg)
                            .clickable { showAddRoadmapDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Roadmap",
                            tint = colors.primaryActionFg,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Divided Roadmap Tracks Showcase (Divided Roadmaps with Explicit Purpose & Description)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIVIDED ROADMAP TRACKS (${roadmaps.size})",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted,
                        letterSpacing = 1.sp
                    )
                    if (roadmaps.size > 1) {
                        Text(
                            text = "Swipe to explore tracks →",
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    roadmaps.forEachIndexed { index, rm ->
                        val isSelected = rm.id == currentRoadmap?.id
                        val rmNodes = nodes.filter { it.roadmapId == rm.id }
                        val rmCompleted = rmNodes.count { it.isCompleted }
                        val rmTotal = rmNodes.size
                        val rmProgress = if (rmTotal > 0) rmCompleted.toFloat() / rmTotal else 0f

                        Box(
                            modifier = Modifier
                                .width(285.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) colors.cardElevated else colors.cardBg)
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) colors.textPrimary else colors.borderSubtle,
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable { selectedRoadmapId = rm.id }
                                .padding(14.dp)
                        ) {
                            Column {
                                // Top row: Track # & Category & Status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) colors.primaryActionBg else colors.cardElevated)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "TRACK 0${index + 1}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) colors.primaryActionFg else colors.textSecondary
                                            )
                                        }

                                        Text(
                                            text = rm.category.uppercase(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textMuted
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.primaryActionBg)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = colors.primaryActionFg
                                                )
                                            }
                                        }
                                        if (roadmaps.size > 1 && !rm.title.contains("Calisthenics", ignoreCase = true)) {
                                            IconButton(
                                                onClick = { onDeleteRoadmap(rm) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Roadmap",
                                                    tint = colors.textMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Roadmap Title
                                Text(
                                    text = rm.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // What this roadmap is for (Showcase Description)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.canvasBg.copy(alpha = 0.5f))
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "WHAT THIS ROADMAP IS FOR:",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textMuted
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (rm.description.isNotBlank()) rm.description else "Custom progression architecture.",
                                            fontSize = 11.5.sp,
                                            color = colors.textSecondary,
                                            lineHeight = 15.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (rm.targetGoal.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Flag,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Target: ${rm.targetGoal}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Progress & Action Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$rmCompleted/$rmTotal Milestones (${(rmProgress * 100).toInt()}%)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = colors.textMuted
                                    )

                                    if (!isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.primaryActionBg)
                                                .clickable { selectedRoadmapId = rm.id }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "Select Track →",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.primaryActionFg
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { rmProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = if (isSelected) colors.textPrimary else colors.textMuted,
                                    trackColor = colors.cardBg
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Hero Card: Current Roadmap Status, Progress, and YouTube video link
        if (currentRoadmap != null) {
            item {
                val completedCount = roadmapNodes.count { it.isCompleted }
                val totalNodes = roadmapNodes.size
                val calculatedProgress = if (totalNodes > 0) completedCount.toFloat() / totalNodes else 0f
                val activeNode = roadmapNodes.find { it.isCurrent }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.cardBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.cardElevated)
                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = currentRoadmap.category.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = colors.textPrimary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "$completedCount/$totalNodes Completed",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = currentRoadmap.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            letterSpacing = (-0.5).sp
                        )

                        if (currentRoadmap.description.isNotBlank()) {
                            Text(
                                text = currentRoadmap.description,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                            )
                        }

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { calculatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = colors.textPrimary,
                            trackColor = colors.cardElevated
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Current Focus / Active Milestone
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.cardElevated)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "CURRENT ACTIVE FOCUS",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textMuted
                                    )
                                    Text(
                                        text = activeNode?.title ?: "Select a milestone below",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (activeNode != null && activeNode.repsOrCriteria.isNotBlank()) {
                                        Text(
                                            text = activeNode.repsOrCriteria,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Link to Calisthenics Video if this is the calisthenics roadmap
                                if (currentRoadmap.title.contains("Calisthenics", ignoreCase = true)) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/7qvOgQqeeYc"))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Could not open video", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = colors.primaryActionFg,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Video",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primaryActionFg
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Add Milestone in Current Roadmap
                        OutlinedButton(
                            onClick = { showAddNodeDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Milestone to Roadmap",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 4. Phase Filter Tabs
        if (stages.size > 2) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stages.forEach { stageName ->
                        val isSelected = stageName == selectedStageFilter
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) colors.cardElevated else colors.cardBg)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.textPrimary else colors.borderSubtle,
                                    CircleShape
                                )
                                .clickable { selectedStageFilter = stageName }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stageName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.textPrimary else colors.textMuted
                            )
                        }
                    }
                }
            }
        }

        // 5. Milestones & Progressions Vertical Timeline Feed
        if (filteredNodes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No milestones found in this phase",
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(filteredNodes, key = { it.id }) { node ->
                RoadmapNodeCard(
                    node = node,
                    onToggleCompleted = {
                        onToggleNodeCompleted(node)
                        if (!node.isCompleted) {
                            VibrationHelper.triggerRapidVibration(context)
                        }
                    },
                    onSetCurrent = {
                        onSetCurrentNode(node)
                        VibrationHelper.triggerRapidVibration(context)
                    },
                    onToggleChecklistItem = { idx ->
                        onToggleChecklistItem(node, idx)
                        VibrationHelper.triggerRapidVibration(context)
                    },
                    onDelete = { onDeleteNode(node) }
                )
            }
        }
    }

    // Add Milestone Dialog
    if (showAddNodeDialog && currentRoadmap != null) {
        AddRoadmapNodeDialog(
            roadmapId = currentRoadmap.id,
            onDismiss = { showAddNodeDialog = false },
            onConfirm = { stage, title, desc, criteria, checklistJson ->
                onAddNode(currentRoadmap.id, stage, title, desc, criteria, checklistJson)
                showAddNodeDialog = false
            }
        )
    }

    // Add Roadmap Dialog
    if (showAddRoadmapDialog) {
        AddRoadmapDialog(
            onDismiss = { showAddRoadmapDialog = false },
            onConfirm = { title, category, desc, targetGoal ->
                onAddRoadmap(title, category, desc, targetGoal)
                showAddRoadmapDialog = false
            }
        )
    }
}

/**
 * Individual Node / Milestone Timeline Card
 */
@Composable
fun RoadmapNodeCard(
    node: RoadmapNode,
    onToggleCompleted: () -> Unit,
    onSetCurrent: () -> Unit,
    onToggleChecklistItem: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val colors = AppTheme.colors
    val checklist = remember(node.checklistJson) {
        val list = mutableListOf<Pair<String, Boolean>>()
        try {
            val arr = JSONArray(node.checklistJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Pair(obj.optString("text", ""), obj.optBoolean("done", false)))
            }
        } catch (_: Exception) {}
        list
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (node.isCurrent) colors.cardBg else colors.cardBg)
            .border(
                1.dp,
                if (node.isCurrent) colors.textPrimary.copy(alpha = 0.6f) else if (node.isCompleted) colors.borderSubtle else colors.borderSubtle,
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            // Header Row: Stage, Order, Current Badge, Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (node.isCompleted) colors.textPrimary else colors.cardElevated)
                            .clickable { onToggleCompleted() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (node.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = colors.canvasBg,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = "${node.stepOrder}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = node.stage.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.isCurrent) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.primaryActionBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CURRENT FOCUS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = colors.primaryActionFg
                            )
                        }
                    } else if (node.isCompleted) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.cardElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "MASTERED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete Milestone",
                            tint = colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = node.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textDecoration = if (node.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )

            // Criteria / Reps badge
            if (node.repsOrCriteria.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.cardElevated)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Target: ${node.repsOrCriteria}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // Description
            if (node.description.isNotBlank()) {
                Text(
                    text = node.description,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
            }

            // Checklist Items
            if (checklist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                checklist.forEachIndexed { idx, item ->
                    val (text, done) = item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onToggleChecklistItem(idx) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (done) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (done) colors.textPrimary else colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = text,
                            fontSize = 11.5.sp,
                            color = if (done) colors.textMuted else colors.textPrimary,
                            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row: Set as current & Mark complete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!node.isCurrent) {
                    OutlinedButton(
                        onClick = onSetCurrent,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Set Current",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }
                }

                Button(
                    onClick = onToggleCompleted,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (node.isCompleted) colors.cardElevated else colors.primaryActionBg
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (node.isCompleted) "Completed" else "Mark Done",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (node.isCompleted) colors.textSecondary else colors.primaryActionFg
                    )
                }
            }
        }
    }
}

/**
 * Add Milestone / Node Dialog with Full 3-Button Navigation Clearance
 */
@Composable
fun AddRoadmapNodeDialog(
    roadmapId: Long,
    onDismiss: () -> Unit,
    onConfirm: (stage: String, title: String, desc: String, criteria: String, checklistJson: String) -> Unit
) {
    val colors = AppTheme.colors
    var stage by remember { mutableStateOf("Phase 1: Foundation") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var criteria by remember { mutableStateOf("") }
    var checklistText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(win, false)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 14.dp)
                .padding(top = 36.dp, bottom = 96.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                color = colors.cardBg,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ADD MILESTONE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = stage,
                                onValueChange = { stage = it },
                                label = { Text("Phase / Stage", fontSize = 11.sp) },
                                placeholder = { Text("e.g. Phase 2: The 4 Pillars", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Milestone Title", fontSize = 11.sp) },
                                placeholder = { Text("e.g. Pillar 1: PUSH Mastery", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = criteria,
                                onValueChange = { criteria = it },
                                label = { Text("Target Reps / Criteria", fontSize = 11.sp) },
                                placeholder = { Text("e.g. 3 sets of 12 clean reps", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = desc,
                                onValueChange = { desc = it },
                                label = { Text("Description & Cues", fontSize = 11.sp) },
                                placeholder = { Text("Technical cues, form rules, mistakes to avoid...", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = checklistText,
                                onValueChange = { checklistText = it },
                                label = { Text("Checklist Items (one per line)", fontSize = 11.sp) },
                                placeholder = { Text("Wall Push-ups 3x15\nIncline Push-ups 3x12\nFirst strict push-up", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Safe Bottom Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    val lines = checklistText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                                    val jsonArr = JSONArray()
                                    lines.forEach { line ->
                                        jsonArr.put(JSONObject().apply {
                                            put("text", line)
                                            put("done", false)
                                        })
                                    }
                                    onConfirm(stage.trim(), title.trim(), desc.trim(), criteria.trim(), jsonArr.toString())
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add Milestone", color = colors.primaryActionFg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Add Roadmap Dialog with Full 3-Button Navigation Clearance
 */
@Composable
fun AddRoadmapDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, desc: String, targetGoal: String) -> Unit
) {
    val colors = AppTheme.colors
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Fitness") }
    var desc by remember { mutableStateOf("") }
    var targetGoal by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(win, false)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 14.dp)
                .padding(top = 36.dp, bottom = 96.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                color = colors.cardBg,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CREATE NEW ROADMAP",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Roadmap Title", fontSize = 11.sp) },
                                placeholder = { Text("e.g. C Programming Zero to Linux Kernel", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("Category", fontSize = 11.sp) },
                                placeholder = { Text("e.g. Coding, Fitness, Security", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = targetGoal,
                                onValueChange = { targetGoal = it },
                                label = { Text("Target Goal", fontSize = 11.sp) },
                                placeholder = { Text("e.g. Build Custom Memory Allocator & Kernel Module", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = desc,
                                onValueChange = { desc = it },
                                label = { Text("Roadmap Summary", fontSize = 11.sp) },
                                placeholder = { Text("Overall progression plan and milestones...", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Safe Bottom Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onConfirm(title.trim(), category.trim(), desc.trim(), targetGoal.trim())
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Create Roadmap", color = colors.primaryActionFg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
