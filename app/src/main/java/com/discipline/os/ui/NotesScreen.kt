package com.discipline.os.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.discipline.os.data.Note
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Google Keep-Style Pastel Color Palette
 * Adaptive values for Dark and Light theme
 */
data class NoteColorDef(
    val name: String,
    val darkBg: Color,
    val lightBg: Color,
    val darkBorder: Color,
    val lightBorder: Color,
    val swatch: Color
)

val NoteColorPalette = listOf(
    NoteColorDef("Default", Color(0xFF141922), Color(0xFFFFFFFF), Color(0xFF262E3B), Color(0xFFE2E8F0), Color(0xFF94A3B8)),
    NoteColorDef("Coral", Color(0xFF331A18), Color(0xFFFFEBEE), Color(0xFF5C2B29), Color(0xFFFFCDD2), Color(0xFFEF5350)),
    NoteColorDef("Peach", Color(0xFF352414), Color(0xFFFFF3E0), Color(0xFF5E3F1A), Color(0xFFFFE0B2), Color(0xFFFFA726)),
    NoteColorDef("Sand", Color(0xFF322E14), Color(0xFFFFFDE7), Color(0xFF58511A), Color(0xFFFFF9C4), Color(0xFFFFEE58)),
    NoteColorDef("Sage", Color(0xFF172E1E), Color(0xFFE8F5E9), Color(0xFF275235), Color(0xFFC8E6C9), Color(0xFF66BB6A)),
    NoteColorDef("Mint", Color(0xFF122E2B), Color(0xFFE0F2F1), Color(0xFF1E524D), Color(0xFFB2DFDB), Color(0xFF26A69A)),
    NoteColorDef("Sky", Color(0xFF142738), Color(0xFFE3F2FD), Color(0xFF20425E), Color(0xFFBBDEFB), Color(0xFF42A5F5)),
    NoteColorDef("Violet", Color(0xFF271738), Color(0xFFF3E5F5), Color(0xFF432860), Color(0xFFE1BEE7), Color(0xFFAB47BC)),
    NoteColorDef("Rose", Color(0xFF321627), Color(0xFFFCE4EC), Color(0xFF572543), Color(0xFFF8BBD0), Color(0xFFEC407A))
)

data class ChecklistItem(
    val text: String,
    val done: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notes: List<Note>,
    onAddNote: (title: String, content: String, colorIndex: Int, isPinned: Boolean, tags: String, author: String, checklistJson: String) -> Unit,
    onUpdateNote: (note: Note) -> Unit,
    onDeleteNote: (note: Note) -> Unit,
    onTogglePin: (note: Note) -> Unit,
    onToggleChecklistItem: (note: Note, index: Int) -> Unit,
    onOpenAi: () -> Unit
) {
    val colors = AppTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isGridView by remember { mutableStateOf(true) }

    // Dialog state
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var isCreatingNote by remember { mutableStateOf(false) }
    var startInChecklistMode by remember { mutableStateOf(false) }

    // Filter notes
    val filteredNotes = remember(notes, searchQuery, selectedFilter) {
        notes.filter { note ->
            val matchesSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.content.contains(searchQuery, ignoreCase = true) ||
                note.tags.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "📌 Pinned" -> note.isPinned
                "🤖 Agent" -> note.author.equals("Agent", ignoreCase = true)
                "👤 My Notes" -> note.author.equals("User", ignoreCase = true) || note.author.isBlank()
                "💻 PC" -> note.author.contains("PC", ignoreCase = true)
                "☑️ Checklists" -> note.checklistJson.isNotBlank() && note.checklistJson != "[]"
                else -> note.tags.split(",").any { it.trim().equals(selectedFilter.removePrefix("#"), ignoreCase = true) }
            }
            matchesSearch && matchesFilter
        }
    }

    val pinnedNotes = remember(filteredNotes) { filteredNotes.filter { it.isPinned } }
    val otherNotes = remember(filteredNotes) { filteredNotes.filter { !it.isPinned } }

    // Unique tags
    val allTags = remember(notes) {
        notes.flatMap { it.tags.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Top Bar: Title & View Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Notes",
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.cardBg)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${notes.size}",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Layout Toggle (Grid vs List)
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.cardBg)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = "Toggle View",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // AI Quick Ask
                    IconButton(
                        onClick = onOpenAi,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.accentFlameSoft)
                            .border(1.dp, colors.accentFlame.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Notes",
                            tint = colors.accentFlame,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Google Keep Style Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search notes, tags, or directives...",
                                color = colors.textMuted,
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("All", "📌 Pinned", "🤖 Agent", "👤 My Notes", "☑️ Checklists", "💻 PC") +
                    allTags.map { "#$it" }

                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) colors.primaryActionBg else colors.cardBg)
                            .border(1.dp, if (isSelected) Color.Transparent else colors.borderSubtle, RoundedCornerShape(20.dp))
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) colors.primaryActionFg else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Notes List / Grid
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.cardBg)
                                .border(1.dp, colors.borderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditNote,
                                contentDescription = "Empty Notes",
                                tint = colors.textMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No notes match '$searchQuery'" else "No notes here yet",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the bar below to take a note, list, or ask Agent.",
                            color = colors.textMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pinned Notes Section
                    if (pinnedNotes.isNotEmpty()) {
                        item {
                            Text(
                                text = "PINNED",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }

                        if (isGridView) {
                            items(pinnedNotes.chunked(2)) { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    for (note in pair) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            NoteCard(
                                                note = note,
                                                onCardClick = { editingNote = note },
                                                onTogglePin = { onTogglePin(note) },
                                                onToggleChecklistItem = { idx -> onToggleChecklistItem(note, idx) }
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(pinnedNotes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    onCardClick = { editingNote = note },
                                    onTogglePin = { onTogglePin(note) },
                                    onToggleChecklistItem = { idx -> onToggleChecklistItem(note, idx) }
                                )
                            }
                        }
                    }

                    // Other Notes Section
                    if (otherNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            item {
                                Text(
                                    text = "OTHERS",
                                    color = colors.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp)
                                )
                            }
                        }

                        if (isGridView) {
                            items(otherNotes.chunked(2)) { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    for (note in pair) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            NoteCard(
                                                note = note,
                                                onCardClick = { editingNote = note },
                                                onTogglePin = { onTogglePin(note) },
                                                onToggleChecklistItem = { idx -> onToggleChecklistItem(note, idx) }
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(otherNotes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    onCardClick = { editingNote = note },
                                    onTogglePin = { onTogglePin(note) },
                                    onToggleChecklistItem = { idx -> onToggleChecklistItem(note, idx) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Floating Bottom Bar: Google Keep "Take a note..." Capsule
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val dockBottomClearance = (if (navBottom > 48.dp) navBottom else 48.dp) + 42.dp

        if (imeBottom == 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = dockBottomClearance)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.cardBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(28.dp))
                        .clickable {
                            startInChecklistMode = false
                            isCreatingNote = true
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Take a note...",
                        color = colors.textMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick checklist button
                        IconButton(
                            onClick = {
                                startInChecklistMode = true
                                isCreatingNote = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckBox,
                                contentDescription = "New Checklist",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Quick Agent directive button
                        IconButton(
                            onClick = {
                                startInChecklistMode = false
                                isCreatingNote = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditNote,
                                contentDescription = "New Note",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 6. Note Editor Modal Dialog (Create or Edit)
        if (isCreatingNote) {
            NoteEditorDialog(
                note = null,
                initialChecklistMode = startInChecklistMode,
                onDismiss = { isCreatingNote = false },
                onSave = { title, content, colorIdx, isPinned, tags, author, checklistJson ->
                    onAddNote(title, content, colorIdx, isPinned, tags, author, checklistJson)
                    isCreatingNote = false
                },
                onDelete = {}
            )
        }

        if (editingNote != null) {
            NoteEditorDialog(
                note = editingNote,
                initialChecklistMode = editingNote!!.checklistJson.isNotBlank() && editingNote!!.checklistJson != "[]",
                onDismiss = { editingNote = null },
                onSave = { title, content, colorIdx, isPinned, tags, author, checklistJson ->
                    val updated = editingNote!!.copy(
                        title = title,
                        content = content,
                        colorIndex = colorIdx,
                        isPinned = isPinned,
                        tags = tags,
                        author = author,
                        checklistJson = checklistJson,
                        updatedAt = System.currentTimeMillis()
                    )
                    onUpdateNote(updated)
                    editingNote = null
                },
                onDelete = {
                    onDeleteNote(editingNote!!)
                    editingNote = null
                }
            )
        }
    }
}

/**
 * Individual Note Card (Google Keep Aesthetic)
 */
@Composable
fun NoteCard(
    note: Note,
    onCardClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleChecklistItem: (Int) -> Unit
) {
    val colors = AppTheme.colors
    val colorDef = NoteColorPalette.getOrElse(note.colorIndex.coerceIn(0, NoteColorPalette.size - 1)) { NoteColorPalette[0] }
    val cardBg = if (colors.isDark) colorDef.darkBg else colorDef.lightBg
    val cardBorder = if (colors.isDark) colorDef.darkBorder else colorDef.lightBorder

    val checklistItems = remember(note.checklistJson) {
        parseChecklist(note.checklistJson)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
            .clickable { onCardClick() }
            .padding(12.dp)
    ) {
        Column {
            // Header Row: Title & Pin Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin Note",
                        tint = if (note.isPinned) colors.accentFlame else colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Body Content Text (if present)
            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Checklist Preview Items (interactive checkboxes directly on card!)
            if (checklistItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    checklistItems.take(4).forEachIndexed { idx, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleChecklistItem(idx) }
                        ) {
                            Icon(
                                imageVector = if (item.done) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                contentDescription = "Toggle Checklist",
                                tint = if (item.done) colors.successGreen else colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.text,
                                color = if (item.done) colors.textMuted else colors.textPrimary,
                                fontSize = 12.sp,
                                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (checklistItems.size > 4) {
                        Text(
                            text = "+ ${checklistItems.size - 4} more items",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges Row: Author & Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Author badge
                val author = note.author.ifBlank { "User" }
                val (authorIcon, authorBg, authorFg) = when {
                    author.contains("Agent", ignoreCase = true) -> Triple("🤖 AI", colors.accentCyanSoft, colors.accentCyan)
                    author.contains("PC", ignoreCase = true) -> Triple("💻 PC", colors.accentFlameSoft, colors.accentFlame)
                    else -> Triple("👤 Me", colors.secondaryActionBg, colors.textSecondary)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(authorBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = authorIcon,
                        color = authorFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Date stamp
                val dateStr = remember(note.updatedAt) {
                    formatTimestamp(note.updatedAt)
                }
                Text(
                    text = dateStr,
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
            }

            // Tags pills if any
            if (note.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    note.tags.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .take(3)
                        .forEach { tag ->
                            Text(
                                text = "#$tag",
                                color = colors.textMuted,
                                fontSize = 10.sp
                            )
                        }
                }
            }
        }
    }
}

/**
 * Note Editor Dialog (Create & Edit)
 */
@Composable
fun NoteEditorDialog(
    note: Note?,
    initialChecklistMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, colorIdx: Int, isPinned: Boolean, tags: String, author: String, checklistJson: String) -> Unit,
    onDelete: () -> Unit
) {
    val colors = AppTheme.colors

    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var colorIndex by remember { mutableStateOf(note?.colorIndex ?: 0) }
    var isPinned by remember { mutableStateOf(note?.isPinned ?: false) }
    var tags by remember { mutableStateOf(note?.tags ?: "") }
    var author by remember { mutableStateOf(note?.author ?: "User") }
    var isChecklistMode by remember { mutableStateOf(initialChecklistMode) }

    val checklistItems = remember {
        mutableStateListOf<ChecklistItem>().apply {
            addAll(parseChecklist(note?.checklistJson ?: "[]"))
        }
    }

    var newItemText by remember { mutableStateOf("") }
    var showColorPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            // Auto-save on dismiss if title or content or items not empty
            val json = serializeChecklist(checklistItems)
            if (title.isNotBlank() || content.isNotBlank() || checklistItems.isNotEmpty()) {
                onSave(title.trim(), content.trim(), colorIndex, isPinned, tags.trim(), author, json)
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val colorDef = NoteColorPalette.getOrElse(colorIndex.coerceIn(0, NoteColorPalette.size - 1)) { NoteColorPalette[0] }
        val sheetBg = if (colors.isDark) colorDef.darkBg else colorDef.lightBg

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(sheetBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val json = serializeChecklist(checklistItems)
                            if (title.isNotBlank() || content.isNotBlank() || checklistItems.isNotEmpty()) {
                                onSave(title.trim(), content.trim(), colorIndex, isPinned, tags.trim(), author, json)
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pin Toggle
                        IconButton(onClick = { isPinned = !isPinned }) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin",
                                tint = if (isPinned) colors.accentFlame else colors.textPrimary
                            )
                        }

                        // Checklist Mode Toggle
                        IconButton(onClick = { isChecklistMode = !isChecklistMode }) {
                            Icon(
                                imageVector = if (isChecklistMode) Icons.Filled.CheckBox else Icons.Outlined.CheckBox,
                                contentDescription = "Toggle Checklist",
                                tint = if (isChecklistMode) colors.successGreen else colors.textPrimary
                            )
                        }

                        // Palette Toggle
                        IconButton(onClick = { showColorPicker = !showColorPicker }) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = "Colors",
                                tint = colors.textPrimary
                            )
                        }

                        // Delete button (if editing existing note)
                        if (note != null) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = colors.accentFlame
                                )
                            }
                        }

                        // Done / Save Button
                        Button(
                            onClick = {
                                val json = serializeChecklist(checklistItems)
                                onSave(title.trim(), content.trim(), colorIndex, isPinned, tags.trim(), author, json)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryActionBg,
                                contentColor = colors.primaryActionFg
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Color Picker Row (Animated)
                AnimatedVisibility(visible = showColorPicker) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        itemsIndexed(NoteColorPalette) { idx, cDef ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(cDef.swatch)
                                    .border(
                                        width = if (colorIndex == idx) 3.dp else 1.dp,
                                        color = if (colorIndex == idx) colors.textPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { colorIndex = idx },
                                contentAlignment = Alignment.Center
                            ) {
                                if (colorIndex == idx) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Author & Tag Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val authorOptions = listOf("User" to "👤 User", "Agent" to "🤖 Agent", "PC" to "💻 PC")
                    authorOptions.forEach { (key, label) ->
                        val isSel = author.equals(key, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) colors.secondaryActionBg else Color.Transparent)
                                .border(1.dp, if (isSel) colors.accentCyan else colors.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable { author = key }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) colors.textPrimary else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title Input Field
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            "Title",
                            color = colors.textMuted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Tags Input Field
                TextField(
                    value = tags,
                    onValueChange = { tags = it },
                    placeholder = {
                        Text(
                            "Tags (comma separated, e.g. Work, Ideas, Coding)",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = colors.textSecondary,
                        unfocusedTextColor = colors.textSecondary
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Divider(color = colors.borderDivider, thickness = 0.5.dp)

                // Body Content / Checklist Area
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    // Regular Note Content
                    item {
                        TextField(
                            value = content,
                            onValueChange = { content = it },
                            placeholder = {
                                Text(
                                    "Note...",
                                    color = colors.textMuted,
                                    fontSize = 15.sp
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Checklist Section if active
                    if (isChecklistMode || checklistItems.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "CHECKLIST",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        itemsIndexed(checklistItems) { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        checklistItems[idx] = item.copy(done = !item.done)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.done) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                        contentDescription = "Toggle",
                                        tint = if (item.done) colors.successGreen else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                TextField(
                                    value = item.text,
                                    onValueChange = { newTxt ->
                                        checklistItems[idx] = item.copy(text = newTxt)
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = if (item.done) colors.textMuted else colors.textPrimary,
                                        unfocusedTextColor = if (item.done) colors.textMuted else colors.textPrimary
                                    ),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 14.sp,
                                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(
                                    onClick = { checklistItems.removeAt(idx) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Add new checklist item row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add item",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = newItemText,
                                    onValueChange = { newItemText = it },
                                    placeholder = {
                                        Text("List item", color = colors.textMuted, fontSize = 14.sp)
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                if (newItemText.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            checklistItems.add(ChecklistItem(newItemText.trim(), false))
                                            newItemText = ""
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Add",
                                            tint = colors.successGreen,
                                            modifier = Modifier.size(18.dp)
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

// ==================== CHECKLIST & DATE HELPERS ====================

fun parseChecklist(jsonStr: String): List<ChecklistItem> {
    if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
    val list = mutableListOf<ChecklistItem>()
    try {
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            list.add(ChecklistItem(
                text = item.optString("text", ""),
                done = item.optBoolean("done", false)
            ))
        }
    } catch (_: Exception) {}
    return list
}

fun serializeChecklist(items: List<ChecklistItem>): String {
    if (items.isEmpty()) return "[]"
    val arr = JSONArray()
    for (it in items) {
        if (it.text.isNotBlank()) {
            arr.put(JSONObject().apply {
                put("text", it.text)
                put("done", it.done)
            })
        }
    }
    return arr.toString()
}

fun formatTimestamp(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    return when {
        diff < 60_000L -> "Just now"
        diff < 3600_000L -> "${diff / 60_000L}m ago"
        diff < 86400_000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
}
