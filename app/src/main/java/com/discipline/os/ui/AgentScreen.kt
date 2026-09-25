package com.discipline.os.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
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

    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var currentSettings by remember { mutableStateOf(AiSettings.load(context)) }

    val listState = rememberLazyListState()

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

    val quickActions = remember {
        listOf(
            "Today's routine",
            "Mark push-ups complete",
            "Reschedule bedtime to 23:00",
            "Add fuel entry",
            "Test vibration",
            "Discipline history"
        )
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
                                .background(colors.textPrimary.copy(alpha = 0.7f))
                        )
                    }

                    Text(
                        text = "${currentSettings.provider.displayName} • ${currentSettings.modelName}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colors.textMuted,
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

                // AI Settings Button
                IconButton(
                    onClick = {
                        engine.resetStatus()
                        showSettingsDialog = true
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

        // Quick Prompt Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickActions.forEach { action ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                        .clickable {
                            coroutineScope.launch {
                                engine.sendMessage(action)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = action,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
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
            items(messages, key = { it.id }) { msg ->
                when (msg.role) {
                    "user" -> UserMessageBubble(msg)
                    "assistant" -> AssistantMessageBubble(msg)
                    "tool" -> ToolResultBubble(msg)
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
                                    showSettingsDialog = true
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
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Command Discipline AI...",
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

        // AI & Tiny Models Settings Dialog
        if (showSettingsDialog) {
            AiSettingsDialog(
                currentSettings = currentSettings,
                engine = engine,
                onSave = { updated ->
                    AiSettings.save(context, updated)
                    currentSettings = updated
                    showSettingsDialog = false
                    Toast.makeText(context, "AI settings saved", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
fun UserMessageBubble(msg: ChatMessage) {
    val colors = AppTheme.colors
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
            Text(
                text = msg.content,
                fontSize = 14.sp,
                color = colors.textPrimary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun AssistantMessageBubble(msg: ChatMessage) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "AI",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Discipline AI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = colors.textPrimary
                    )
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

object ModelDownloadHelper {
    fun downloadModel(context: Context, modelName: String, url: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                fallbackToBrowser(context, url)
                return
            }
            val uri = Uri.parse(url)
            val fileName = uri.lastPathSegment?.takeIf { it.endsWith(".gguf", ignoreCase = true) }
                ?: "${modelName.lowercase().replace(" ", "_").replace(":", "_")}.gguf"

            val request = DownloadManager.Request(uri).apply {
                setTitle("DisciplineOS: $modelName")
                setDescription("Downloading AI Model ($fileName)")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "DisciplineOS/models/$fileName"
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }
            downloadManager.enqueue(request)
            Toast.makeText(context, "Download started for $modelName. Check notification bar.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            fallbackToBrowser(context, url)
        }
    }

    private fun fallbackToBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Opening direct download in browser...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to start download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

enum class AiDialogScreen {
    TYPE_SELECT,
    CLOUD_LIST,
    PROVIDER_CONFIG,
    TINY_MODELS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    currentSettings: AiSettings,
    engine: AiAgentEngine,
    onSave: (AiSettings) -> Unit,
    onDismiss: () -> Unit
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

    // Custom model downloader states
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

    // Device back navigation
    BackHandler {
        when (currentScreen) {
            AiDialogScreen.TYPE_SELECT -> onDismiss()
            AiDialogScreen.CLOUD_LIST -> currentScreen = AiDialogScreen.TYPE_SELECT
            AiDialogScreen.PROVIDER_CONFIG -> currentScreen = AiDialogScreen.CLOUD_LIST
            AiDialogScreen.TINY_MODELS -> currentScreen = AiDialogScreen.TYPE_SELECT
        }
    }

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
                    // Header Bar (fixed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentScreen != AiDialogScreen.TYPE_SELECT) {
                                IconButton(
                                    onClick = {
                                        when (currentScreen) {
                                            AiDialogScreen.CLOUD_LIST -> currentScreen = AiDialogScreen.TYPE_SELECT
                                            AiDialogScreen.PROVIDER_CONFIG -> currentScreen = AiDialogScreen.CLOUD_LIST
                                            AiDialogScreen.TINY_MODELS -> currentScreen = AiDialogScreen.TYPE_SELECT
                                            else -> {}
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Column {
                                Text(
                                    text = when (currentScreen) {
                                        AiDialogScreen.TYPE_SELECT -> "AI CONFIGURATION"
                                        AiDialogScreen.CLOUD_LIST -> "CLOUD PROVIDERS"
                                        AiDialogScreen.PROVIDER_CONFIG -> selectedProvider.displayName.uppercase()
                                        AiDialogScreen.TINY_MODELS -> "TINY MODELS & OLLAMA"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (currentScreen) {
                                        AiDialogScreen.TYPE_SELECT -> "Select engine architecture"
                                        AiDialogScreen.CLOUD_LIST -> "Select provider to configure"
                                        AiDialogScreen.PROVIDER_CONFIG -> "API keys & model endpoint"
                                        AiDialogScreen.TINY_MODELS -> "Direct GGUF downloads & local runner"
                                    },
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Body (takes remaining space)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (currentScreen) {
                            AiDialogScreen.TYPE_SELECT -> {
                                val isConfigured = if (currentSettings.provider == AiProvider.OLLAMA) {
                                    currentSettings.modelName.isNotBlank()
                                } else {
                                    currentSettings.apiKey.isNotBlank()
                                }

                                // Active Engine Banner
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.cardElevated)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isConfigured) colors.successGreen else colors.accentFlame)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = if (isConfigured) "ACTIVE: ${currentSettings.provider.displayName.uppercase()}" else "NO AI MODEL CONFIGURED",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = if (isConfigured) "Model: ${currentSettings.modelName}" else "Configure Cloud API or Local LLM below to chat.",
                                                fontSize = 11.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "SELECT ARCHITECTURE",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textMuted
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Card 1: Cloud Intelligence
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.cardElevated)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                                        .clickable { currentScreen = AiDialogScreen.CLOUD_LIST }
                                        .padding(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(colors.cardBg)
                                                .border(1.dp, colors.borderSubtle, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cloud,
                                                contentDescription = null,
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Cloud Intelligence (API)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = colors.textPrimary
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = colors.textMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = "OpenRouter, OpenAI, Claude, NVIDIA NIM, Custom",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp,
                                                color = colors.textMuted,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Text(
                                                text = "Hosted reasoning models with tool execution, web access, and sub-second generation speeds.",
                                                fontSize = 11.5.sp,
                                                color = colors.textSecondary,
                                                modifier = Modifier.padding(top = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Card 2: On-Device & Local LLMs
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.cardElevated)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                                        .clickable { currentScreen = AiDialogScreen.TINY_MODELS }
                                        .padding(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(colors.cardBg)
                                                .border(1.dp, colors.borderSubtle, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Memory,
                                                contentDescription = null,
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "On-Device & Local LLMs",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = colors.textPrimary
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = colors.textMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = "Google Gemma 2, Qwen 2.5, Phi-3.5, Ollama",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp,
                                                color = colors.textMuted,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Text(
                                                text = "Download small GGUF weights directly to phone storage, or link with Ollama running on your local Wi-Fi.",
                                                fontSize = 11.5.sp,
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
                                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                                )

                                val cloudProviders = listOf(
                                    AiProvider.OPENROUTER to "Unified gateway for 300+ models (Gemini, Claude, Llama). Recommended.",
                                    AiProvider.OPENAI to "Official OpenAI API for GPT-4o and GPT-4o-mini.",
                                    AiProvider.ANTHROPIC to "Direct Claude 3.5 Sonnet & Claude 3.5 Haiku API.",
                                    AiProvider.NVIDIA_NIM to "High-throughput enterprise AI inference endpoints.",
                                    AiProvider.CUSTOM to "Connect any custom OpenAI-compatible server or proxy."
                                )

                                cloudProviders.forEach { (provider, desc) ->
                                    val isCurrentActive = currentSettings.provider == provider
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.cardElevated)
                                            .border(
                                                1.dp,
                                                if (isCurrentActive) colors.primaryActionBg else colors.borderSubtle,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                selectedProvider = provider
                                                apiKey = if (currentSettings.provider == provider) currentSettings.apiKey else ""
                                                modelName = if (currentSettings.provider == provider) currentSettings.modelName else provider.defaultModel
                                                customBaseUrl = if (currentSettings.provider == provider) currentSettings.customBaseUrl else provider.defaultBaseUrl
                                                testResult = null
                                                currentScreen = AiDialogScreen.PROVIDER_CONFIG
                                            }
                                            .padding(14.dp)
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
                                                    fontSize = 14.sp,
                                                    color = colors.textPrimary
                                                )

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
                                                }
                                            }

                                            Text(
                                                text = desc,
                                                fontSize = 11.5.sp,
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
                                                    fontSize = 10.sp,
                                                    color = colors.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "Configure",
                                                        fontSize = 11.sp,
                                                        color = colors.textPrimary,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = colors.textSecondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AiDialogScreen.PROVIDER_CONFIG -> {
                                val helpHint = when (selectedProvider) {
                                    AiProvider.OPENROUTER -> "Get an API key at openrouter.ai/keys (Free & paid models available)"
                                    AiProvider.OPENAI -> "Get an API key at platform.openai.com/api-keys"
                                    AiProvider.ANTHROPIC -> "Get an API key at console.anthropic.com/settings/keys"
                                    AiProvider.NVIDIA_NIM -> "Get an API key at build.nvidia.com"
                                    AiProvider.CUSTOM -> "Enter your custom base URL and API key (if required)"
                                    else -> ""
                                }

                                if (helpHint.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.cardElevated)
                                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = helpHint,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = colors.textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
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

                                Spacer(modifier = Modifier.height(12.dp))

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
                                    AiProvider.OPENROUTER -> listOf(
                                        "google/gemini-2.0-flash-exp:free",
                                        "anthropic/claude-3.5-sonnet",
                                        "meta-llama/llama-3.3-70b-instruct",
                                        "deepseek/deepseek-chat"
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
                                        "meta/llama-3.3-70b-instruct",
                                        "nvidia/nemotron-4-340b-instruct"
                                    )
                                    else -> emptyList()
                                }

                                if (suggestions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        suggestions.forEach { sug ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (modelName == sug) colors.primaryActionBg else colors.cardElevated)
                                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                                                    .clickable { modelName = sug }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = sug,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (modelName == sug) colors.primaryActionFg else colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                if (selectedProvider == AiProvider.CUSTOM || selectedProvider == AiProvider.NVIDIA_NIM) {
                                    Spacer(modifier = Modifier.height(12.dp))
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

                                Spacer(modifier = Modifier.height(16.dp))

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
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isTesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = colors.textPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Testing API Connection...", color = colors.textPrimary, fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test API Connection", color = colors.textPrimary, fontSize = 12.sp)
                                    }
                                }

                                // Test Result Display
                                testResult?.let { (success, msg) ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (success) colors.successGreenSoft else colors.accentFlameSoft)
                                            .border(
                                                1.dp,
                                                if (success) colors.successGreen else colors.accentFlame,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = msg,
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }

                            AiDialogScreen.TINY_MODELS -> {
                                Text(
                                    text = "ON-DEVICE & TINY MODELS CATALOG",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textMuted
                                )
                                Text(
                                    text = "Direct GGUF model downloads to /Downloads/DisciplineOS/models/ or connect to local Ollama.",
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                                )

                                // Curated Models List
                                TinyModelCatalog.models.forEach { model ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.cardElevated)
                                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
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
                                                    fontSize = 13.5.sp,
                                                    color = colors.textPrimary
                                                )

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
                                                Button(
                                                    onClick = {
                                                        ModelDownloadHelper.downloadModel(context, model.name, model.defaultUrl)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, tint = colors.primaryActionFg, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Download GGUF", fontSize = 11.sp, color = colors.primaryActionFg)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText("GGUF URL", model.defaultUrl)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Direct link copied", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
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

                                // Custom Model Downloader Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.cardBg)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "DOWNLOAD CUSTOM MODEL",
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "Enter any direct .gguf file URL to download directly onto your phone.",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                        )

                                        OutlinedTextField(
                                            value = customModelName,
                                            onValueChange = { customModelName = it },
                                            label = { Text("Model Name", fontSize = 11.sp) },
                                            placeholder = { Text("e.g. MyCustom-GGUF", fontSize = 11.sp, color = colors.textMuted) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = colors.primaryActionBg,
                                                unfocusedBorderColor = colors.borderSubtle
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        OutlinedTextField(
                                            value = customModelUrl,
                                            onValueChange = { customModelUrl = it },
                                            label = { Text("Direct GGUF URL", fontSize = 11.sp) },
                                            placeholder = { Text("https://huggingface.co/.../model.gguf", fontSize = 11.sp, color = colors.textMuted) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
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
                                                    ModelDownloadHelper.downloadModel(context, name, customModelUrl.trim())
                                                } else {
                                                    Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryActionBg),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = colors.secondaryActionFg, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download to Phone", color = colors.secondaryActionFg, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Local Ollama Server Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.cardBg)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "LOCAL OLLAMA SERVER (WI-FI)",
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "Connect to an Ollama server running on your PC or Mac on the same network.",
                                            fontSize = 11.sp,
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

                                        Spacer(modifier = Modifier.height(6.dp))

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
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Test Server", fontSize = 11.sp)
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
                                                    onDismiss()
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryActionBg),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Activate Ollama", fontSize = 11.sp, color = colors.primaryActionFg, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Action Bar (Fixed, never obscured)
                    when (currentScreen) {
                        AiDialogScreen.TYPE_SELECT -> {
                            Button(
                                onClick = onDismiss,
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
                                        onDismiss()
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
                                    onClick = onDismiss,
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
        }
    }
}
