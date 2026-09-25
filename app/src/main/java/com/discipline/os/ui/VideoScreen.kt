package com.discipline.os.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.discipline.os.data.VideoEntry
import java.util.*

@Composable
fun VideoScreen(
    videos: List<VideoEntry>,
    initialUrl: String = "",
    initialTitle: String = "",
    onClearInitial: () -> Unit = {},
    onAddVideo: (title: String, url: String, category: String, reminderMs: Long, delayText: String, reminderType: String, notes: String) -> Unit,
    onToggleWatched: (VideoEntry) -> Unit,
    onDeleteVideo: (VideoEntry) -> Unit,
    onOpenRoadmap: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTabFilter by remember { mutableIntStateOf(0) } // 0 = Pending Queue, 1 = Watched History

    // If an external share came in, trigger the add dialog automatically
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            showAddDialog = true
        }
    }

    val categories = listOf("All", "Calisthenics", "Coding", "Security", "Mandarin", "College", "Mindset")

    val filteredVideos = remember(videos, selectedCategoryFilter, selectedTabFilter) {
        videos.filter { video ->
            val matchesCategory = (selectedCategoryFilter == "All" || video.category.equals(selectedCategoryFilter, ignoreCase = true))
            val matchesTab = if (selectedTabFilter == 0) !video.isWatched else video.isWatched
            matchesCategory && matchesTab
        }
    }

    val pendingCount = remember(videos) { videos.count { !it.isWatched } }
    val watchedCount = remember(videos) { videos.count { it.isWatched } }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = (if (navBarBottom > 48.dp) navBarBottom else 48.dp) + 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Banner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "YT Study Vault",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Curate & conquer learning queue",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Add Video Pill Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PrimaryActionBg)
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = PrimaryActionFg,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Video",
                            color = PrimaryActionFg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Segmented Pill Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(CardWhite)
                    .border(1.dp, BorderSubtle, CircleShape)
                    .padding(4.dp)
            ) {
                // Tab 0: Pending Queue
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selectedTabFilter == 0) PrimaryActionBg else Color.Transparent)
                        .clickable { selectedTabFilter = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Pending Queue ($pendingCount)",
                        fontSize = 13.sp,
                        fontWeight = if (selectedTabFilter == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTabFilter == 0) PrimaryActionFg else TextSecondary
                    )
                }

                // Tab 1: Watched Archive
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selectedTabFilter == 1) PrimaryActionBg else Color.Transparent)
                        .clickable { selectedTabFilter = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Watched Vault ($watchedCount)",
                        fontSize = 13.sp,
                        fontWeight = if (selectedTabFilter == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTabFilter == 1) PrimaryActionFg else TextSecondary
                    )
                }
            }
        }

        // 3. Category Filter Pills
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val isSelected = cat == selectedCategoryFilter
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) PrimaryActionBg else CardWhite)
                            .border(1.dp, if (isSelected) PrimaryActionBg else BorderSubtle, CircleShape)
                            .clickable { selectedCategoryFilter = cat }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (cat == "All") "All" else cat,
                            color = if (isSelected) PrimaryActionFg else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 4. Video Cards List
        if (filteredVideos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTabFilter == 0) "No pending videos in queue" else "No watched videos yet",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(filteredVideos, key = { it.id }) { video ->
                ModernVideoCard(
                    video = video,
                    onWatchClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    onToggleWatched = { onToggleWatched(video) },
                    onDelete = { onDeleteVideo(video) },
                    onOpenRoadmap = onOpenRoadmap
                )
            }
        }
    }

    if (showAddDialog) {
        ModernAddVideoDialog(
            initialUrl = initialUrl,
            initialTitle = initialTitle,
            onDismiss = {
                showAddDialog = false
                onClearInitial()
            },
            onSave = { title, url, cat, reminderMs, delayText, remType, notes ->
                onAddVideo(title, url, cat, reminderMs, delayText, remType, notes)
                showAddDialog = false
                onClearInitial()
            }
        )
    }
}

@Composable
fun ModernVideoCard(
    video: VideoEntry,
    onWatchClick: () -> Unit,
    onToggleWatched: () -> Unit,
    onDelete: () -> Unit,
    onOpenRoadmap: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0x15000000))
            .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentCyanSoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = video.category,
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Reminder Tag
                if (video.reminderDelayText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentFlameSoft)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (video.reminderType == "RAPID_VIBRATE") Icons.Default.Vibration else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = AccentFlame,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = video.reminderDelayText,
                                color = AccentFlame,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = video.title,
                color = if (video.isWatched) TextSecondary else TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = if (video.isWatched) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (video.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = video.notes,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val isCalisthenics = video.title.contains("Calisthenics", ignoreCase = true) ||
                video.category.equals("Calisthenics", ignoreCase = true) ||
                video.url.contains("7qvOgQqeeYc")
            if (isCalisthenics) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenRoadmap() },
                    color = CardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryActionBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.AltRoute,
                                    contentDescription = null,
                                    tint = PrimaryActionFg,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "OPEN ROADMAP PROTOCOL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "4 Pillars, 5-12 Reps & 30-Day Plan",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Full Pill "Watch on YouTube" button (Matching Image 1)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(PrimaryActionBg)
                        .clickable { onWatchClick() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Watch on YouTube",
                        color = PrimaryActionFg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Arrow Circle Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryActionFg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Open",
                            tint = PrimaryActionBg,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Watched Checkbox Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (video.isWatched) SuccessGreen else CanvasBg)
                        .border(1.dp, if (video.isWatched) SuccessGreen else BorderSubtle, CircleShape)
                        .clickable { onToggleWatched() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = if (video.isWatched) Color.White else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Delete Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CanvasBg)
                        .border(1.dp, BorderSubtle, CircleShape)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModernAddVideoDialog(
    initialUrl: String,
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, category: String, reminderMs: Long, delayText: String, reminderType: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle.ifBlank { "" }) }
    var url by remember { mutableStateOf(initialUrl.ifBlank { "" }) }
    var category by remember { mutableStateOf("Coding") }
    var notes by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableIntStateOf(60) }
    var reminderType by remember { mutableStateOf("RAPID_VIBRATE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        titleContentColor = TextPrimary,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(text = "Save Video to Vault", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("YouTube URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryActionBg,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = PrimaryActionBg
                    )
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Video Title / Concept") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryActionBg,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = PrimaryActionBg
                    )
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Study Goal / Notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryActionBg,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = PrimaryActionBg
                    )
                )

                Text("Category:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Calisthenics", "Coding", "Security", "Mandarin", "College", "Mindset")) { cat ->
                        val isSel = category.equals(cat, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSel) PrimaryActionBg else CanvasBg)
                                .border(1.dp, if (isSel) PrimaryActionBg else BorderSubtle, CircleShape)
                                .clickable { category = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSel) PrimaryActionFg else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Text("Remind me in:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (mins, label) ->
                        val isSel = selectedMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) PrimaryActionBg else CanvasBg)
                                .clickable { selectedMinutes = mins }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) PrimaryActionFg else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        val epoch = System.currentTimeMillis() + (selectedMinutes * 60 * 1000L)
                        val formattedTime = com.discipline.os.util.TimeHelper.formatEpochToTime(epoch, false)
                        val delayText = "At $formattedTime (${selectedMinutes}m)"
                        val finalTitle = title.ifBlank { "YouTube Study Session" }
                        onSave(finalTitle, url, category, epoch, delayText, reminderType, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryActionBg),
                shape = CircleShape
            ) {
                Text("Save Video", color = PrimaryActionFg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
