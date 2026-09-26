package com.discipline.os.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.discipline.os.agent.*
import kotlinx.coroutines.launch

@Composable
fun AgentScreen(
    engine: AiAgentEngine,
    onBack: () -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = AppTheme.colors

    val messages by engine.messages.collectAsState()
    val status by engine.status.collectAsState()
    val downloadStatus by ModelDownloadManager.downloadStatus.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showSlashMenu by remember { mutableStateOf(false) }
    var isConfiguringModel by remember { mutableStateOf(false) }
    var currentSettings by remember { mutableStateOf(AiSettings.load(context)) }

    val listState = rememberLazyListState()

    // When full screen configuration is open, delegate to dedicated full-screen view
    if (isConfiguringModel) {
        AgentModelConfigScreen(
            currentSettings = currentSettings,
            engine = engine,
            onSave = { updated ->
                AiSettings.save(context, updated)
                currentSettings = updated
                isConfiguringModel = false
                Toast.makeText(context, "AI settings saved and activated", Toast.LENGTH_SHORT).show()
            },
            onBack = { isConfiguringModel = false }
        )
        return
    }

    // Handle system back gesture
    BackHandler {
        onBack()
    }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size, status) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBg)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        // Top Header Row: Back button, Title, History, Settings, Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.cardBg)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DISCIPLINE AI",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (currentSettings.isConfigured()) colors.successGreen else Color(0xFFEF4444))
                        )
                    }

                    Text(
                        text = if (currentSettings.isConfigured()) {
                            "${currentSettings.provider.displayName} • ${currentSettings.modelName}"
                        } else {
                            "NOT CONFIGURED • TAP SETTINGS TO ACTIVATE"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (currentSettings.isConfigured()) colors.textMuted else Color(0xFFEF4444),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Top Actions: History, Settings, Clear
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // View History Button
                IconButton(
                    onClick = onShowHistory,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.cardBg)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BarChart,
                        contentDescription = "Discipline History",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // AI Settings Button (Full Screen)
                IconButton(
                    onClick = {
                        engine.resetStatus()
                        isConfiguringModel = true
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.cardBg)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "AI Settings",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear Chat Button
                IconButton(
                    onClick = { engine.clearChat() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.cardBg)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Clear Chat",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Live In-Screen Download Progress Card (if model download is running)
        if (downloadStatus is DownloadStatus.Downloading || downloadStatus is DownloadStatus.Connecting) {
            LiveDownloadProgressCard(
                status = downloadStatus,
                onCancel = { ModelDownloadManager.cancelDownload() },
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        // Top Model Switcher Bar (Quick switching between on-device & cloud models)
        val downloadedModelFiles = remember(downloadStatus, isConfiguringModel, currentSettings) {
            ModelDownloadManager.getAllDownloadedModelFiles(context)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (downloadedModelFiles.isNotEmpty()) {
                downloadedModelFiles.forEach { file ->
                    val cleanFileName = file.nameWithoutExtension
                    val displayName = when {
                        cleanFileName.contains("gemma", ignoreCase = true) -> "Google Gemma 2 2B"
                        cleanFileName.contains("llama", ignoreCase = true) && cleanFileName.contains("1b", ignoreCase = true) -> "Meta Llama 3.2 1B"
                        cleanFileName.contains("llama", ignoreCase = true) && cleanFileName.contains("3b", ignoreCase = true) -> "Meta Llama 3.2 3B"
                        cleanFileName.contains("qwen", ignoreCase = true) -> "Qwen 2.5 3B"
                        cleanFileName.contains("phi", ignoreCase = true) -> "Phi-3.5 Mini"
                        else -> cleanFileName.take(18)
                    }

                    val isThisActive = currentSettings.provider == AiProvider.TINY_LOCAL &&
                            (currentSettings.customBaseUrl == file.absolutePath || currentSettings.modelName.contains(cleanFileName.take(8), ignoreCase = true) || currentSettings.modelName.contains(displayName.take(8), ignoreCase = true))

                    Surface(
                        shape = CircleShape,
                        color = if (isThisActive) colors.primaryActionBg else colors.cardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isThisActive) colors.primaryActionBg else colors.borderSubtle),
                        modifier = Modifier.clickable {
                            if (!isThisActive) {
                                val updated = currentSettings.copy(
                                    provider = AiProvider.TINY_LOCAL,
                                    modelName = displayName,
                                    customBaseUrl = file.absolutePath
                                )
                                AiSettings.save(context, updated)
                                currentSettings = updated
                                Toast.makeText(context, "Active: $displayName", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isThisActive) Icons.Default.Bolt else Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (isThisActive) colors.primaryActionFg else colors.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isThisActive) "$displayName • Active" else displayName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isThisActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isThisActive) colors.primaryActionFg else colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Cloud API / Settings shortcut pill
            Surface(
                shape = CircleShape,
                color = if (currentSettings.provider != AiProvider.TINY_LOCAL && currentSettings.isConfigured()) colors.primaryActionBg else colors.cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (currentSettings.provider != AiProvider.TINY_LOCAL && currentSettings.isConfigured()) colors.primaryActionBg else colors.borderSubtle),
                modifier = Modifier.clickable {
                    engine.resetStatus()
                    isConfiguringModel = true
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (currentSettings.provider != AiProvider.TINY_LOCAL && currentSettings.isConfigured()) Icons.Default.CloudQueue else Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (currentSettings.provider != AiProvider.TINY_LOCAL && currentSettings.isConfigured()) colors.primaryActionFg else colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (currentSettings.provider != AiProvider.TINY_LOCAL && currentSettings.isConfigured()) "${currentSettings.provider.displayName} • Active" else "Switch / Add Model",
                        fontSize = 11.5.sp,
                        fontWeight = if (currentSettings.provider != AiProvider.TINY_LOCAL && currentSettings.isConfigured()) FontWeight.Bold else FontWeight.Medium,
                        color = if (currentSettings.provider != AiProvider.TINY_LOCAL && currentSettings.isConfigured()) colors.primaryActionFg else colors.textSecondary
                    )
                }
            }
        }

        // Chat Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
        ) {
            // If not configured and no messages yet, show red configure prompt directly at top
            if (!currentSettings.isConfigured() && messages.isEmpty()) {
                item {
                    RedConfigureAiBanner(
                        onClickConfigure = { isConfiguringModel = true }
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                when (msg.role) {
                    "user" -> UserMessageBubble(msg)
                    "assistant" -> AssistantMessageBubble(msg)
                    "tool" -> ToolResultBubble(msg)
                    "unconfigured_alert" -> RedConfigureAiBanner(
                        onClickConfigure = { isConfiguringModel = true }
                    )
                }
            }

            // Status indicator when processing
            item {
                when (val s = status) {
                    is AgentStatus.Thinking -> {
                        StatusCard(
                            text = s.step,
                            color = colors.textPrimary
                        )
                    }
                    is AgentStatus.ExecutingTool -> {
                        StatusCard(
                            text = "Executing: ${s.toolName}...",
                            color = colors.accentFlame
                        )
                    }
                    is AgentStatus.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentFlameSoft)
                                .border(1.dp, colors.accentFlame.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = colors.accentFlame,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Agent Error",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = colors.accentFlame
                                )
                            }
                            Text(
                                text = s.message,
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                            Button(
                                onClick = {
                                    engine.resetStatus()
                                    isConfiguringModel = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentFlame),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Configure AI & Models", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                    AgentStatus.Idle -> {}
                }
            }
        }

        // Slash Commands Floating Menu (Triggered by '/' or the command button)
        AnimatedVisibility(
            visible = showSlashMenu || (inputText.startsWith("/") && inputText.length <= 4),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SlashCommandMenu(
                onSelectCommand = { cmd ->
                    inputText = cmd
                    showSlashMenu = false
                },
                onDirectExecute = { directMsg ->
                    showSlashMenu = false
                    inputText = ""
                    coroutineScope.launch {
                        engine.sendMessage(directMsg)
                    }
                },
                onDismiss = { showSlashMenu = false },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Contextual Section Pills Bar (Appears when user starts typing /roadmap, /protocol, /fuel, /delete)
        if (inputText.startsWith("/roadmap")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Push", "Pull", "Legs", "Core", "Skills", "Wall Push-ups", "Pull-ups", "L-sit").forEach { chip ->
                    Surface(
                        shape = CircleShape,
                        color = colors.cardElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                        modifier = Modifier.clickable {
                            inputText = if (chip in listOf("Push", "Pull", "Legs", "Core", "Skills")) {
                                "/roadmap $chip: "
                            } else {
                                "/roadmap $chip "
                            }
                        }
                    ) {
                        Text(
                            text = chip,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        } else if (inputText.startsWith("/protocol") || inputText.startsWith("/task")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Health", "Coding", "College", "Bedtime", "7:15 PM", "Morning", "10 Push-ups").forEach { chip ->
                    Surface(
                        shape = CircleShape,
                        color = colors.cardElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                        modifier = Modifier.clickable {
                            inputText = if (chip.contains("PM") || chip == "Morning") {
                                if (inputText.endsWith(" ")) "${inputText}at $chip " else "$inputText at $chip "
                            } else if (chip.contains("Push-ups")) {
                                "/protocol $chip at 7:15 PM"
                            } else {
                                if (inputText.endsWith(" ")) "$inputText$chip: " else "$inputText $chip: "
                            }
                        }
                    ) {
                        Text(
                            text = chip,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        } else if (inputText.startsWith("/fuel")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Critic", "Skeptic", "Rival", "Doubter", "Personal Vow").forEach { chip ->
                    Surface(
                        shape = CircleShape,
                        color = colors.cardElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                        modifier = Modifier.clickable {
                            inputText = "/fuel $chip: "
                        }
                    ) {
                        Text(
                            text = chip,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        } else if (inputText.startsWith("/delete")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            engine.sendMessage("delete the task you just added")
                        }
                        inputText = ""
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Tap to Delete Last Added Task",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }

        // Bottom Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.cardElevated)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slash Command Trigger Button
            IconButton(
                onClick = {
                    showSlashMenu = !showSlashMenu
                    if (showSlashMenu && inputText.isBlank()) {
                        inputText = "/"
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (showSlashMenu || inputText.startsWith("/")) colors.primaryActionBg.copy(alpha = 0.2f) else colors.cardBg)
            ) {
                Text(
                    text = "/",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = if (showSlashMenu || inputText.startsWith("/")) colors.primaryActionBg else colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        if (currentSettings.isConfigured()) "Type message or / for sections..." else "Type message or tap Configure above...",
                        fontSize = 13.sp,
                        color = colors.textMuted
                    )
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank() && status == AgentStatus.Idle) {
                        val textToSend = inputText
                        inputText = ""
                        showSlashMenu = false
                        coroutineScope.launch {
                            engine.sendMessage(textToSend)
                        }
                    }
                })
            )

            val canSend = inputText.isNotBlank() && status == AgentStatus.Idle
            IconButton(
                onClick = {
                    if (canSend) {
                        val textToSend = inputText
                        inputText = ""
                        showSlashMenu = false
                        coroutineScope.launch {
                            engine.sendMessage(textToSend)
                        }
                    }
                },
                enabled = canSend,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (canSend) colors.primaryActionBg else colors.cardBg)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) colors.primaryActionFg else colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Interactive Slash Commands & Sections Menu
 */
@Composable
fun SlashCommandMenu(
    onSelectCommand: (String) -> Unit,
    onDirectExecute: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardElevated)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = colors.primaryActionBg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "COMMANDS & SECTIONS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1. Protocol / Routine
                SlashCommandItem(
                    icon = Icons.Default.CheckCircle,
                    iconColor = colors.successGreen,
                    title = "/protocol",
                    subtitle = "Daily Protocols • Tasks, routines & scheduled alarms",
                    onClick = { onSelectCommand("/protocol ") }
                )

                // 2. Calisthenics Roadmap
                SlashCommandItem(
                    icon = Icons.Default.FitnessCenter,
                    iconColor = Color(0xFF38BDF8),
                    title = "/roadmap",
                    subtitle = "Roadmap Section • Push, Pull, Legs, Core & Skills",
                    onClick = { onSelectCommand("/roadmap ") }
                )

                // 3. Doubter Fuel
                SlashCommandItem(
                    icon = Icons.Default.Whatshot,
                    iconColor = colors.accentFlame,
                    title = "/fuel",
                    subtitle = "Fuel Vault • Log critic, doubter & defiance vow",
                    onClick = { onSelectCommand("/fuel ") }
                )

                // 4. Delete / Undo
                SlashCommandItem(
                    icon = Icons.Default.DeleteOutline,
                    iconColor = Color(0xFFEF4444),
                    title = "/delete",
                    subtitle = "Delete Task • Remove last added or specific protocol",
                    onClick = { onDirectExecute("delete the task you just added") }
                )

                // 5. Status / Audit
                SlashCommandItem(
                    icon = Icons.Default.BarChart,
                    iconColor = colors.primaryActionBg,
                    title = "/status",
                    subtitle = "Discipline Audit • View today's streak & completed tasks",
                    onClick = { onDirectExecute("show today's routine") }
                )
            }
        }
    }
}

@Composable
fun SlashCommandItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBg)
            .border(0.8.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Prominent Red Alert Card shown when AI model is unconfigured
 */
@Composable
fun RedConfigureAiBanner(
    onClickConfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val redColor = Color(0xFFEF4444)
    val redBg = Color(0xFFEF4444).copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(redBg)
            .border(1.5.dp, redColor, RoundedCornerShape(14.dp))
            .clickable { onClickConfigure() }
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert",
                    tint = redColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONFIGURE AI MODEL",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = redColor,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No AI model is currently active. Connect a Cloud API (OpenRouter, Claude, GPT) or download an On-Device tiny model (Google Gemma, Llama, Qwen) to chat and control DisciplineOS.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onClickConfigure,
                colors = ButtonDefaults.buttonColors(
                    containerColor = redColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Click here to configure AI model",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Live In-Screen Download Progress Card
 * Renders real-time download status, speed, percentage, ETA and a cancel button directly on screen.
 */
@Composable
fun LiveDownloadProgressCard(
    status: DownloadStatus,
    onCancel: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    when (status) {
        is DownloadStatus.Connecting -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.cardElevated)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONNECTING STREAM...",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Initiating fast download for ${status.modelName}",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.accentFlame, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        is DownloadStatus.Downloading -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.cardElevated)
                    .border(1.dp, colors.primaryActionBg.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            CircularProgressIndicator(
                                progress = { status.progressFloat },
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp,
                                color = colors.primaryActionBg
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DOWNLOADING: ${status.modelName.uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.5.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentFlame.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentFlame.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Cancel", fontSize = 10.sp, color = colors.accentFlame, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { status.progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = colors.primaryActionBg,
                        trackColor = colors.borderSubtle
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val downloadedMb = status.bytesDownloaded / (1024f * 1024f)
                    val totalMb = status.totalBytes / (1024f * 1024f)
                    val pct = (status.progressFloat * 100).toInt()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (totalMb > 0) String.format("%.1f MB / %.1f MB (%d%%)", downloadedMb, totalMb, pct) else String.format("%.1f MB", downloadedMb),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )

                        val etaText = if (status.etaSeconds > 0) {
                            val mins = status.etaSeconds / 60
                            val secs = status.etaSeconds % 60
                            "${mins}m ${secs}s"
                        } else "Calculating..."

                        Text(
                            text = String.format("%.1f MB/s • %s", status.speedMbPerSec, etaText),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
        is DownloadStatus.Completed -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.successGreenSoft)
                    .border(1.dp, colors.successGreen.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = colors.successGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "MODEL READY & AUTO-CONFIGURED",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = colors.successGreen
                            )
                            Text(
                                text = "${status.modelName} is active for on-device inference.",
                                fontSize = 11.sp,
                                color = colors.textPrimary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        is DownloadStatus.Failed -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accentFlameSoft)
                    .border(1.dp, colors.accentFlame.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = colors.accentFlame, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "DOWNLOAD FAILED",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = colors.accentFlame
                            )
                            Text(
                                text = status.error,
                                fontSize = 11.sp,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        else -> {}
    }
}

/**
 * Dedicated Full Screen AI Model Configuration
 * No modal dialog or popup! Full screen layout with Back button, top bar, and multi-screen navigation.
 */
@Composable
fun AgentModelConfigScreen(
    currentSettings: AiSettings,
    engine: AiAgentEngine,
    onSave: (AiSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = AppTheme.colors

    var currentScreen by remember { mutableStateOf(AiDialogScreen.TYPE_SELECT) }

    // Form states for provider config
    var selectedProvider by remember { mutableStateOf(currentSettings.provider) }
    var apiKey by remember { mutableStateOf(currentSettings.apiKey) }
    var modelName by remember { mutableStateOf(currentSettings.modelName) }
    var customBaseUrl by remember { mutableStateOf(currentSettings.customBaseUrl) }
    var showApiKey by remember { mutableStateOf(false) }

    // Custom model downloader states (Top of On-Device screen)
    var customModelName by remember { mutableStateOf("") }
    var customModelUrl by remember { mutableStateOf("") }

    // Ollama specific config states
    var ollamaBaseUrl by remember {
        mutableStateOf(
            if (currentSettings.provider == AiProvider.OLLAMA && currentSettings.customBaseUrl.isNotBlank()) {
                currentSettings.customBaseUrl
            } else {
                AiProvider.OLLAMA.defaultBaseUrl
            }
        )
    }
    var ollamaModelName by remember {
        mutableStateOf(
            if (currentSettings.provider == AiProvider.OLLAMA && currentSettings.modelName.isNotBlank()) {
                currentSettings.modelName
            } else {
                AiProvider.OLLAMA.defaultModel
            }
        )
    }

    // Test connection states
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val downloadStatus by ModelDownloadManager.downloadStatus.collectAsState()

    // Device back navigation handling
    BackHandler {
        when (currentScreen) {
            AiDialogScreen.TYPE_SELECT -> onBack()
            AiDialogScreen.CLOUD_LIST -> currentScreen = AiDialogScreen.TYPE_SELECT
            AiDialogScreen.PROVIDER_CONFIG -> currentScreen = AiDialogScreen.CLOUD_LIST
            AiDialogScreen.TINY_MODELS -> currentScreen = AiDialogScreen.TYPE_SELECT
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        when (currentScreen) {
                            AiDialogScreen.TYPE_SELECT -> onBack()
                            AiDialogScreen.CLOUD_LIST -> currentScreen = AiDialogScreen.TYPE_SELECT
                            AiDialogScreen.PROVIDER_CONFIG -> currentScreen = AiDialogScreen.CLOUD_LIST
                            AiDialogScreen.TINY_MODELS -> currentScreen = AiDialogScreen.TYPE_SELECT
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.cardBg)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = when (currentScreen) {
                            AiDialogScreen.TYPE_SELECT -> "AI CONFIGURATION"
                            AiDialogScreen.CLOUD_LIST -> "CLOUD PROVIDERS"
                            AiDialogScreen.PROVIDER_CONFIG -> selectedProvider.displayName.uppercase()
                            AiDialogScreen.TINY_MODELS -> "ON-DEVICE & TINY MODELS"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = colors.textPrimary,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (currentScreen) {
                            AiDialogScreen.TYPE_SELECT -> "Select Engine Architecture"
                            AiDialogScreen.CLOUD_LIST -> "Select Cloud Provider to Configure"
                            AiDialogScreen.PROVIDER_CONFIG -> "API Credentials & Endpoint"
                            AiDialogScreen.TINY_MODELS -> "In-App GGUF Downloads & Local Runner"
                        },
                        fontSize = 11.5.sp,
                        color = colors.textMuted
                    )
                }
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.cardBg)
                    .border(1.dp, colors.borderSubtle, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (currentScreen) {
                AiDialogScreen.TYPE_SELECT -> {
                    val isConfigured = currentSettings.isConfigured()

                    // Active Engine Status Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardElevated)
                            .border(1.dp, if (isConfigured) colors.successGreen.copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isConfigured) colors.successGreen else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isConfigured) "ACTIVE: ${currentSettings.provider.displayName.uppercase()}" else "NO AI MODEL CONFIGURED",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isConfigured) colors.textPrimary else Color(0xFFEF4444)
                                )
                                Text(
                                    text = if (isConfigured) "Model: ${currentSettings.modelName}" else "Select Cloud API or On-Device Tiny Models below to activate.",
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "SELECT ARCHITECTURE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 1: Cloud Intelligence (API)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.cardElevated)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                            .clickable { currentScreen = AiDialogScreen.CLOUD_LIST }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardBg)
                                    .border(1.dp, colors.borderSubtle, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Cloud Intelligence (API)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.textPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "OpenRouter, OpenAI GPT-4o, Claude 3.5, NVIDIA NIM, Custom",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    text = "High-speed reasoning models with live web knowledge, routine synthesis, and instant tool execution.",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Option 2: On-Device & Tiny Models
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.cardElevated)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                            .clickable { currentScreen = AiDialogScreen.TINY_MODELS }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardBg)
                                    .border(1.dp, colors.borderSubtle, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "On-Device & Tiny Models",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.textPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "Google Gemma 2 2B, Llama 3.2 1B, SmolLM2, Qwen 2.5, Ollama",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    text = "Download small GGUF weights directly to phone storage, paste custom model links, or link with Ollama on local Wi-Fi.",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }

                AiDialogScreen.CLOUD_LIST -> {
                    Text(
                        text = "SELECT CLOUD PROVIDER",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted
                    )
                    Text(
                        text = "Tap any provider to open its dedicated configuration window.",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    val cloudProviders = listOf(
                        AiProvider.NARA_ROUTER to "Free models (Ling 3.0 Flash, Space Bunny, Nemotron, Laguna). Zero cost.",
                        AiProvider.OPENROUTER to "Unified gateway with verified free models (Nemotron, Laguna, Space Bunny).",
                        AiProvider.OPENCODE to "OpenCode Zen endpoint supporting space-bunny-free.",
                        AiProvider.NVIDIA_NIM to "High-throughput enterprise AI inference endpoints.",
                        AiProvider.OPENAI to "Official OpenAI API for GPT-4o and GPT-4o-mini.",
                        AiProvider.ANTHROPIC to "Direct Claude 3.5 Sonnet & Claude 3.5 Haiku API.",
                        AiProvider.CUSTOM to "Connect any custom OpenAI-compatible server or proxy."
                    )

                    cloudProviders.forEach { (provider, desc) ->
                        val isCurrentActive = currentSettings.provider == provider && currentSettings.isConfigured()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.cardElevated)
                                .border(
                                    1.dp,
                                    if (isCurrentActive) colors.primaryActionBg else colors.borderSubtle,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    selectedProvider = provider
                                    apiKey = if (currentSettings.provider == provider) currentSettings.apiKey else ""
                                    modelName = if (currentSettings.provider == provider) currentSettings.modelName else provider.defaultModel
                                    customBaseUrl = if (currentSettings.provider == provider) currentSettings.customBaseUrl else provider.defaultBaseUrl
                                    testResult = null
                                    currentScreen = AiDialogScreen.PROVIDER_CONFIG
                                }
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = provider.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.textPrimary
                                    )

                                    if (isCurrentActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.primaryActionBg)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                color = colors.primaryActionFg
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = desc,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Default: ${provider.defaultModel}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Configure",
                                            fontSize = 12.sp,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AiDialogScreen.PROVIDER_CONFIG -> {
                    val helpHint = when (selectedProvider) {
                        AiProvider.NARA_ROUTER -> "NaraRouter (router.bynara.id) — Verified active free models! Usable at zero cost."
                        AiProvider.OPENROUTER -> "OpenRouter (openrouter.ai/keys) — Verified active free models (Nemotron, Laguna, Space Bunny)."
                        AiProvider.OPENCODE -> "OpenCode Zen endpoint (opencode.ai) — space-bunny-free verified active!"
                        AiProvider.NVIDIA_NIM -> "NVIDIA NIM (build.nvidia.com) — Enterprise AI inference."
                        AiProvider.OPENAI -> "Get an API key at platform.openai.com/api-keys"
                        AiProvider.ANTHROPIC -> "Get an API key at console.anthropic.com/settings/keys"
                        AiProvider.CUSTOM -> "Enter your custom base URL and API key (if required)"
                        else -> ""
                    }

                    if (helpHint.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.cardElevated)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = helpHint,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // API Key Field
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key", fontSize = 12.sp) },
                        placeholder = { Text("Paste key here...", fontSize = 12.sp, color = colors.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle API Key",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clipboard.primaryClip?.getItemAt(0)
                                    val text = item?.text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        apiKey = text.trim()
                                        Toast.makeText(context, "Pasted key", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primaryActionBg,
                            unfocusedBorderColor = colors.borderSubtle
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Model Identifier Field
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Identifier", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primaryActionBg,
                            unfocusedBorderColor = colors.borderSubtle
                        )
                    )

                    // Model Suggestions Chips
                    val suggestions = when (selectedProvider) {
                        AiProvider.NARA_ROUTER -> listOf(
                            "ling-3.0-flash-sante-free",
                            "space-bunny-alpha",
                            "space-bunny-alpha-bynara",
                            "ling-3.0-flash-fin-free",
                            "laguna-s-2.1",
                            "nemotron-3-ultra-free",
                            "nemotron-3-super-free"
                        )
                        AiProvider.OPENROUTER -> listOf(
                            "nvidia/nemotron-3-super-120b-a12b:free",
                            "stealth/space-bunny-alpha",
                            "poolside/laguna-s-2.1:free",
                            "nvidia/nemotron-3-ultra-550b-a55b:free",
                            "liquid/lfm-2.5-2.6b:free",
                            "cohere/north-mini-code:free"
                        )
                        AiProvider.OPENCODE -> listOf(
                            "space-bunny-free"
                        )
                        AiProvider.OPENAI -> listOf(
                            "gpt-4o-mini",
                            "gpt-4o"
                        )
                        AiProvider.ANTHROPIC -> listOf(
                            "claude-3-5-haiku-20241022",
                            "claude-3-5-sonnet-20241022"
                        )
                        AiProvider.NVIDIA_NIM -> listOf(
                            "google/gemma-3-12b-it",
                            "ibm/granite-3.0-8b-instruct",
                            "meta/llama-3.3-70b-instruct"
                        )
                        else -> emptyList()
                    }

                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.forEach { sug ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (modelName == sug) colors.primaryActionBg else colors.cardElevated)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { modelName = sug }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = sug,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (modelName == sug) colors.primaryActionFg else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (selectedProvider == AiProvider.CUSTOM || selectedProvider == AiProvider.NVIDIA_NIM || selectedProvider == AiProvider.NARA_ROUTER || selectedProvider == AiProvider.OPENCODE) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = { customBaseUrl = it },
                            label = { Text("Base URL Endpoint", fontSize = 12.sp) },
                            placeholder = { Text(selectedProvider.defaultBaseUrl, fontSize = 11.sp, color = colors.textMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primaryActionBg,
                                unfocusedBorderColor = colors.borderSubtle
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Test API Connection Button
                    Button(
                        onClick = {
                            isTesting = true
                            testResult = null
                            coroutineScope.launch {
                                val tempSettings = AiSettings(
                                    provider = selectedProvider,
                                    apiKey = apiKey.trim(),
                                    modelName = modelName.trim().ifBlank { selectedProvider.defaultModel },
                                    customBaseUrl = customBaseUrl.trim()
                                )
                                val res = engine.testConnection(tempSettings)
                                isTesting = false
                                testResult = res
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.textPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing API Connection...", color = colors.textPrimary, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test API Connection", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Test Result Display
                    testResult?.let { (success, msg) ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (success) colors.successGreenSoft else colors.accentFlameSoft)
                                .border(
                                    1.dp,
                                    if (success) colors.successGreen else colors.accentFlame,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                AiDialogScreen.TINY_MODELS -> {
                    // 1. TOP CARD: Custom Model Option (User requested at very top)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.cardElevated)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AddLink,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CUSTOM GGUF MODEL DOWNLOAD",
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = "Can't find your model? Paste any direct HuggingFace or web .gguf URL to download directly into the app.",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                            )

                            OutlinedTextField(
                                value = customModelName,
                                onValueChange = { customModelName = it },
                                label = { Text("Model Name / Identifier", fontSize = 11.sp) },
                                placeholder = { Text("e.g. Gemma-2-2B-Q4 or MyCustom-GGUF", fontSize = 11.sp, color = colors.textMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.primaryActionBg,
                                    unfocusedBorderColor = colors.borderSubtle
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = customModelUrl,
                                onValueChange = { customModelUrl = it },
                                label = { Text("Direct GGUF URL", fontSize = 11.sp) },
                                placeholder = { Text("https://huggingface.co/.../model.gguf", fontSize = 11.sp, color = colors.textMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val item = clipboard.primaryClip?.getItemAt(0)
                                        val text = item?.text?.toString() ?: ""
                                        if (text.isNotBlank()) {
                                            customModelUrl = text.trim()
                                            Toast.makeText(context, "Pasted URL", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.primaryActionBg,
                                    unfocusedBorderColor = colors.borderSubtle
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (customModelUrl.isNotBlank()) {
                                        val name = customModelName.ifBlank { "Custom-Model" }
                                        ModelDownloadManager.startDownload(context, name, customModelUrl.trim()) { file ->
                                            val updated = currentSettings.copy(
                                                provider = AiProvider.TINY_LOCAL,
                                                modelName = name,
                                                customBaseUrl = file.absolutePath
                                            )
                                            onSave(updated)
                                        }
                                    } else {
                                        Toast.makeText(context, "Please enter a valid GGUF URL", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = colors.primaryActionFg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Fast Download", color = colors.primaryActionFg, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. LIVE IN-SCREEN DOWNLOAD PROGRESS CARD
                    LiveDownloadProgressCard(
                        status = downloadStatus,
                        onCancel = { ModelDownloadManager.cancelDownload() },
                        onDismiss = { ModelDownloadManager.resetStatus() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // DOWNLOADED ON-DEVICE MODELS SECTION
                    val downloadedFiles = remember(downloadStatus) {
                        ModelDownloadManager.getAllDownloadedModelFiles(context)
                    }

                    if (downloadedFiles.isNotEmpty()) {
                        Text(
                            text = "DOWNLOADED MODELS ON THIS DEVICE (${downloadedFiles.size})",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted
                        )
                        Text(
                            text = "Locally stored GGUF model files ready for offline execution without internet.",
                            fontSize = 11.5.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )

                        downloadedFiles.forEach { file ->
                            val sizeMb = file.length() / (1024 * 1024)
                            val isThisActive = currentSettings.provider == AiProvider.TINY_LOCAL &&
                                    (currentSettings.customBaseUrl == file.absolutePath || currentSettings.modelName.contains(file.nameWithoutExtension.take(8), ignoreCase = true))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.cardElevated)
                                    .border(
                                        1.dp,
                                        if (isThisActive) colors.primaryActionBg else colors.borderSubtle,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                        Text(
                                            text = file.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$sizeMb MB • Ready on device",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isThisActive) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.primaryActionBg)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = colors.primaryActionFg
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    val cleanName = when {
                                                        file.name.contains("gemma", ignoreCase = true) -> "Google Gemma 2 2B Instruct"
                                                        file.name.contains("llama", ignoreCase = true) && file.name.contains("1b", ignoreCase = true) -> "Meta Llama 3.2 1B Instruct"
                                                        file.name.contains("llama", ignoreCase = true) && file.name.contains("3b", ignoreCase = true) -> "Meta Llama 3.2 3B Instruct"
                                                        else -> file.nameWithoutExtension
                                                    }
                                                    val updated = currentSettings.copy(
                                                        provider = AiProvider.TINY_LOCAL,
                                                        modelName = cleanName,
                                                        customBaseUrl = file.absolutePath
                                                    )
                                                    onSave(updated)
                                                    Toast.makeText(context, "Activated $cleanName", Toast.LENGTH_SHORT).show()
                                                    onBack()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("Activate", fontSize = 11.5.sp, color = colors.primaryActionFg, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // 3. CURATED ON-DEVICE MODELS CATALOG
                    Text(
                        text = "CURATED ON-DEVICE MODELS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted
                    )
                    Text(
                        text = "Quantized GGUF models optimized for mobile RAM and Snapdragon/Tensor NPU execution.",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    TinyModelCatalog.models.forEach { model ->
                        val isDownloaded = ModelDownloadManager.isModelDownloaded(context, model.name) || ModelDownloadManager.isModelDownloaded(context, model.id)
                        val existingFile = ModelDownloadManager.findExistingModelFile(context, model.name) ?: ModelDownloadManager.findExistingModelFile(context, model.id)
                        val isDownloadingThis = (downloadStatus as? DownloadStatus.Downloading)?.modelName == model.name
                        val isCurrentActive = currentSettings.provider == AiProvider.TINY_LOCAL &&
                                (currentSettings.modelName.contains(model.name, ignoreCase = true) || (existingFile != null && currentSettings.customBaseUrl == existingFile.absolutePath))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.cardElevated)
                                .border(
                                    1.dp,
                                    if (isCurrentActive) colors.primaryActionBg else colors.borderSubtle,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (isCurrentActive) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.primaryActionBg)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = colors.primaryActionFg
                                                )
                                            }
                                        } else if (isDownloaded) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.successGreenSoft)
                                                    .border(1.dp, colors.successGreen, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "DOWNLOADED",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = colors.successGreen
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.cardBg)
                                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = model.downloadSize,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${model.parameters} • Min ${model.minRam}",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                Text(
                                    text = model.description,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isDownloaded) {
                                        Button(
                                            onClick = {
                                                val f = ModelDownloadManager.findExistingModelFile(context, model.name)
                                                    ?: ModelDownloadManager.findExistingModelFile(context, model.id)
                                                val updated = currentSettings.copy(
                                                    provider = AiProvider.TINY_LOCAL,
                                                    modelName = model.name,
                                                    customBaseUrl = f?.absolutePath ?: ""
                                                )
                                                onSave(updated)
                                                Toast.makeText(context, "Activated ${model.name}", Toast.LENGTH_SHORT).show()
                                                onBack()
                                            },
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = colors.primaryActionFg, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isCurrentActive) "Active Model" else "Activate Model", fontSize = 12.sp, color = colors.primaryActionFg, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                ModelDownloadManager.startDownload(context, model.name, model.defaultUrl) { file ->
                                                    val updated = currentSettings.copy(
                                                        provider = AiProvider.TINY_LOCAL,
                                                        modelName = model.name,
                                                        customBaseUrl = file.absolutePath
                                                    )
                                                    onSave(updated)
                                                }
                                            },
                                            enabled = !isDownloadingThis,
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = colors.primaryActionFg, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                if (isDownloadingThis) "Downloading..." else "Fast In-App Download",
                                                fontSize = 11.5.sp,
                                                color = colors.primaryActionFg,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("GGUF URL", model.defaultUrl)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Direct link copied", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy URL", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. LOCAL OLLAMA SERVER (WI-FI)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.cardBg)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "LOCAL OLLAMA SERVER (WI-FI)",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Connect to an Ollama server running on your PC or Mac on the same network.",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )

                            OutlinedTextField(
                                value = ollamaBaseUrl,
                                onValueChange = { ollamaBaseUrl = it },
                                label = { Text("Ollama URL Endpoint", fontSize = 11.sp) },
                                placeholder = { Text("http://192.168.1.100:11434/v1/chat/completions", fontSize = 11.sp, color = colors.textMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.primaryActionBg,
                                    unfocusedBorderColor = colors.borderSubtle
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = ollamaModelName,
                                onValueChange = { ollamaModelName = it },
                                label = { Text("Model Tag", fontSize = 11.sp) },
                                placeholder = { Text("e.g. gemma2:2b or qwen2.5:3b", fontSize = 11.sp, color = colors.textMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.primaryActionBg,
                                    unfocusedBorderColor = colors.borderSubtle
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isTesting = true
                                        testResult = null
                                        coroutineScope.launch {
                                            val tempSettings = AiSettings(
                                                provider = AiProvider.OLLAMA,
                                                apiKey = "",
                                                modelName = ollamaModelName.trim().ifBlank { AiProvider.OLLAMA.defaultModel },
                                                customBaseUrl = ollamaBaseUrl.trim()
                                            )
                                            val res = engine.testConnection(tempSettings)
                                            isTesting = false
                                            testResult = res
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Test Server", fontSize = 11.5.sp)
                                }

                                Button(
                                    onClick = {
                                        val updated = currentSettings.copy(
                                            provider = AiProvider.OLLAMA,
                                            apiKey = "",
                                            modelName = ollamaModelName.trim().ifBlank { AiProvider.OLLAMA.defaultModel },
                                            customBaseUrl = ollamaBaseUrl.trim()
                                        )
                                        onSave(updated)
                                        Toast.makeText(context, "Activated Ollama", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Activate Ollama", fontSize = 11.5.sp, color = colors.primaryActionFg, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fixed Bottom Action Bar
        when (currentScreen) {
            AiDialogScreen.TYPE_SELECT -> {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            AiDialogScreen.CLOUD_LIST -> {
                OutlinedButton(
                    onClick = { currentScreen = AiDialogScreen.TYPE_SELECT },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("← Back to Architecture", color = colors.textSecondary, fontWeight = FontWeight.Medium)
                }
            }
            AiDialogScreen.PROVIDER_CONFIG -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentScreen = AiDialogScreen.CLOUD_LIST },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back", color = colors.textSecondary, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            val updated = currentSettings.copy(
                                provider = selectedProvider,
                                apiKey = apiKey.trim(),
                                modelName = modelName.trim().ifBlank { selectedProvider.defaultModel },
                                customBaseUrl = customBaseUrl.trim()
                            )
                            onSave(updated)
                            Toast.makeText(context, "Activated ${selectedProvider.displayName}", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save & Activate", color = colors.primaryActionFg, fontWeight = FontWeight.Bold)
                    }
                }
            }
            AiDialogScreen.TINY_MODELS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentScreen = AiDialogScreen.TYPE_SELECT },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back", color = colors.textSecondary, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun UserMessageBubble(msg: ChatMessage) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp))
                .background(colors.cardElevated)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = msg.content,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("User Message", msg.content))
                            copied = true
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = if (copied) colors.successGreen else colors.textMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantMessageBubble(msg: ChatMessage) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                .padding(14.dp)
        ) {
            Column {
                // Header with Model name attribution, latency badge, and copy button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "AI",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = msg.modelName ?: "Discipline AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (msg.latencyMs != null && msg.latencyMs > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val latencyText = if (msg.latencyMs < 1000) {
                                "${msg.latencyMs}ms"
                            } else {
                                String.format("%.1fs", msg.latencyMs / 1000.0)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.primaryActionBg.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.primaryActionBg.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = colors.primaryActionBg,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = latencyText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primaryActionBg
                                    )
                                }
                            }
                        }
                    }

                    // Copy action button
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", msg.content))
                            copied = true
                            Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy response",
                            tint = if (copied) colors.successGreen else colors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                if (msg.content.isNotBlank()) {
                    Text(
                        text = msg.content,
                        fontSize = 13.5.sp,
                        color = colors.textPrimary,
                        lineHeight = 20.sp
                    )
                }

                // Show tool calls badges if any
                if (msg.toolCalls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    msg.toolCalls.forEach { tc ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.cardElevated)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Action: ${tc.name}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ToolResultBubble(msg: ChatMessage) {
    val colors = AppTheme.colors
    val result = msg.toolResult
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (result?.success == true) colors.successGreenSoft else colors.accentFlameSoft)
            .border(
                1.dp,
                if (result?.success == true) colors.successGreen.copy(alpha = 0.4f) else colors.accentFlame.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .clickable { isExpanded = !isExpanded }
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result?.success == true) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (result?.success == true) colors.successGreen else colors.accentFlame,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = result?.summary ?: "Action complete: ${msg.id}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = if (isExpanded) 10 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.cardBg)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun StatusCard(text: String, color: Color) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(13.dp),
            strokeWidth = 2.dp,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = colors.textSecondary
        )
    }
}

enum class AiDialogScreen {
    TYPE_SELECT,
    CLOUD_LIST,
    PROVIDER_CONFIG,
    TINY_MODELS
}
