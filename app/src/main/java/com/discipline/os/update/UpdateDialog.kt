package com.discipline.os.update

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.discipline.os.ui.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onInstallStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    val currentVersion = remember { UpdateManager.getCurrentVersionName(context) }
    val currentBuild = remember { UpdateManager.getCurrentVersionCode(context) }

    Dialog(
        onDismissRequest = {
            if (downloadState !is DownloadState.Downloading && !updateInfo.mandatory) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadState !is DownloadState.Downloading && !updateInfo.mandatory,
            dismissOnClickOutside = downloadState !is DownloadState.Downloading && !updateInfo.mandatory,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // 1. Header with Rocket Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (updateInfo.isOfflineReady) AccentCyanSoft else AccentFlameSoft)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (updateInfo.isOfflineReady) "💾" else "🚀", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (updateInfo.isOfflineReady) "OFFLINE UPDATE READY" else "OTA UPDATE READY",
                                color = if (updateInfo.isOfflineReady) AccentCyan else AccentFlame,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    if (!updateInfo.mandatory && downloadState !is DownloadState.Downloading) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CanvasBg)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title & Version Tag
                Text(
                    text = updateInfo.title.ifBlank { "New Update Available" },
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CanvasBg,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "Installed: v$currentVersion (b$currentBuild)",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(12.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyanSoft,
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Latest: v${updateInfo.versionName} (b${updateInfo.versionCode})",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Release Notes List
                Text(
                    text = "WHAT'S NEW",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CanvasBg)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (updateInfo.releaseNotes.isEmpty()) {
                        Text(
                            text = "• Performance optimizations & reliability enhancements",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    } else {
                        updateInfo.releaseNotes.forEach { note ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "⚡",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = note.removePrefix("•").trim(),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Dynamic Action / Progress Area
                when (val state = downloadState) {
                    is DownloadState.Idle -> {
                        if (updateInfo.isOfflineReady) {
                            // Saved Offline Mode: App launch or offline prompt
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Install Later", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        val f = File(updateInfo.localFilePath ?: "")
                                        if (f.exists()) {
                                            UpdateManager.installApk(context, f)
                                            onInstallStarted()
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryActionBg,
                                        contentColor = PrimaryActionFg
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Install Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // Online Wi-Fi Update Mode: Full 3-action choice
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Direct Update Now
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            downloadState = DownloadState.Downloading(0f, 0L, updateInfo.fileSizeBytes, updateInfo)
                                            val result = UpdateManager.downloadApk(context, updateInfo) { progress, down, total ->
                                                downloadState = DownloadState.Downloading(progress, down, total, updateInfo)
                                            }
                                            result.fold(
                                                onSuccess = { file ->
                                                    downloadedFile = file
                                                    downloadState = DownloadState.Downloaded(file, updateInfo)
                                                    val installed = UpdateManager.installApk(context, file)
                                                    if (installed) {
                                                        onInstallStarted()
                                                    }
                                                },
                                                onFailure = { err ->
                                                    downloadState = DownloadState.Error(err.message ?: "Download failed")
                                                }
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryActionBg,
                                        contentColor = PrimaryActionFg
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Update Now (Download & Install)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 2. Save Offline / Update Later + Skip
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!updateInfo.mandatory) {
                                        OutlinedButton(
                                            onClick = onDismiss,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                            border = BorderStroke(1.dp, BorderSubtle),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Skip", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                downloadState = DownloadState.Downloading(0f, 0L, updateInfo.fileSizeBytes, updateInfo)
                                                val result = UpdateManager.downloadApk(context, updateInfo) { progress, down, total ->
                                                    downloadState = DownloadState.Downloading(progress, down, total, updateInfo)
                                                }
                                                result.fold(
                                                    onSuccess = { file ->
                                                        downloadedFile = file
                                                        UpdateManager.saveOfflineUpdate(context, file, updateInfo)
                                                        downloadState = DownloadState.SavedOffline(file, updateInfo)
                                                    },
                                                    onFailure = { err ->
                                                        downloadState = DownloadState.Error(err.message ?: "Download failed")
                                                    }
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                        modifier = Modifier.weight(1.5f)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("💾", fontSize = 12.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save Offline (Later)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is DownloadState.SavedOffline -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentCyanSoft,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Saved Offline Successfully! 💾",
                                            color = AccentCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Stored on your device. You can install it anytime even without Wi-Fi.",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Close", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        val file = downloadedFile ?: state.apkFile
                                        UpdateManager.installApk(context, file)
                                        onInstallStarted()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryActionBg,
                                        contentColor = PrimaryActionFg
                                    ),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Text("Install Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    is DownloadState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading update from Wi-Fi...",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AccentCyan,
                                trackColor = CanvasBg
                            )

                            val downMB = String.format(Locale.US, "%.1f", state.downloadedBytes / (1024f * 1024f))
                            val totalMB = String.format(Locale.US, "%.1f", state.totalBytes / (1024f * 1024f))
                            Text(
                                text = "$downMB MB / $totalMB MB",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }

                    is DownloadState.Downloaded -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SuccessGreenSoft,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Download complete! Ready to install.",
                                        color = SuccessGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val file = downloadedFile ?: state.apkFile
                                    UpdateManager.installApk(context, file)
                                    onInstallStarted()
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SuccessGreen,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Install Update Now", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    is DownloadState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentFlameSoft,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentFlame, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.message,
                                        color = AccentFlame,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Dismiss", fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        downloadState = DownloadState.Idle
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryActionBg, contentColor = PrimaryActionFg),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}
