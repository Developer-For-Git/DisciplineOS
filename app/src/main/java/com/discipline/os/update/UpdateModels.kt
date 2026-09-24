package com.discipline.os.update

import java.io.File

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val title: String,
    val apkUrl: String,
    val releaseNotes: List<String>,
    val fileSizeBytes: Long = 0L,
    val mandatory: Boolean = false,
    val isOfflineReady: Boolean = false,
    val localFilePath: String? = null
)

sealed class DownloadState {
    object Idle : DownloadState()
    object Checking : DownloadState()
    data class Available(val info: UpdateInfo) : DownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val info: UpdateInfo
    ) : DownloadState()
    data class Downloaded(val apkFile: File, val info: UpdateInfo) : DownloadState()
    data class SavedOffline(val apkFile: File, val info: UpdateInfo) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
