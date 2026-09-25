package com.discipline.os.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    const val DEFAULT_UPDATE_SERVER = "http://10.0.2.2:8081"
    private const val PREFS_NAME = "discipline_prefs"
    private const val KEY_UPDATE_SERVER = "update_server_url"
    private const val KEY_OFFLINE_UPDATE_CODE = "offline_update_version_code"
    private const val KEY_OFFLINE_UPDATE_NAME = "offline_update_version_name"
    private const val KEY_OFFLINE_UPDATE_PATH = "offline_update_file_path"
    private const val KEY_OFFLINE_UPDATE_TITLE = "offline_update_title"
    private const val KEY_OFFLINE_UPDATE_NOTES = "offline_update_notes"

    // Reactive flow for real-time live update popups over Wi-Fi
    val liveUpdateNotificationFlow = kotlinx.coroutines.flow.MutableSharedFlow<UpdateInfo>(replay = 1, extraBufferCapacity = 2)

    fun saveOfflineUpdate(context: Context, file: File, info: UpdateInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_OFFLINE_UPDATE_CODE, info.versionCode)
            .putString(KEY_OFFLINE_UPDATE_NAME, info.versionName)
            .putString(KEY_OFFLINE_UPDATE_PATH, file.absolutePath)
            .putString(KEY_OFFLINE_UPDATE_TITLE, info.title)
            .putString(KEY_OFFLINE_UPDATE_NOTES, info.releaseNotes.joinToString("\n"))
            .apply()
    }

    fun getSavedOfflineUpdate(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCode = prefs.getLong(KEY_OFFLINE_UPDATE_CODE, 0L)
        val savedPath = prefs.getString(KEY_OFFLINE_UPDATE_PATH, null)
        val currentCode = getCurrentVersionCode(context)

        if (savedCode > currentCode && !savedPath.isNullOrBlank()) {
            val file = File(savedPath)
            if (file.exists() && file.length() > 0L) {
                val savedName = prefs.getString(KEY_OFFLINE_UPDATE_NAME, "New Version") ?: "New Version"
                val savedTitle = prefs.getString(KEY_OFFLINE_UPDATE_TITLE, "Offline Update Ready") ?: "Offline Update Ready"
                val rawNotes = prefs.getString(KEY_OFFLINE_UPDATE_NOTES, "") ?: ""
                val notes = if (rawNotes.isNotBlank()) rawNotes.lines() else emptyList()
                return UpdateInfo(
                    versionCode = savedCode,
                    versionName = savedName,
                    title = savedTitle,
                    apkUrl = "",
                    releaseNotes = notes,
                    fileSizeBytes = file.length(),
                    isOfflineReady = true,
                    localFilePath = file.absolutePath
                )
            }
        }
        return null
    }

    fun clearOfflineUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_OFFLINE_UPDATE_CODE)
            .remove(KEY_OFFLINE_UPDATE_NAME)
            .remove(KEY_OFFLINE_UPDATE_PATH)
            .remove(KEY_OFFLINE_UPDATE_TITLE)
            .remove(KEY_OFFLINE_UPDATE_NOTES)
            .apply()
    }

    fun getSavedServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UPDATE_SERVER, DEFAULT_UPDATE_SERVER) ?: DEFAULT_UPDATE_SERVER
    }

    fun saveServerUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_UPDATE_SERVER, url.trim()).apply()
    }

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    /**
     * Checks the Wi-Fi update server for a newer version manifest (update.json).
     * Automatically attempts fallback to emulator host (10.0.2.2) if configured LAN IP fails.
     */
    suspend fun checkForUpdate(
        context: Context,
        customServerUrl: String? = null,
        forceCheck: Boolean = false
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val baseUrl = (customServerUrl ?: getSavedServerUrl(context)).trim().trimEnd('/')
        val candidateUrls = mutableListOf<String>()

        if (baseUrl.endsWith("/update.json")) {
            candidateUrls.add(baseUrl)
        } else {
            candidateUrls.add("$baseUrl/update.json")
        }

        // Add 10.0.2.2 fallback for emulator compatibility if testing remote LAN
        if (!candidateUrls.any { it.contains("10.0.2.2") }) {
            candidateUrls.add("http://10.0.2.2:8081/update.json")
        }

        var lastError: Exception? = null

        for (manifestUrl in candidateUrls) {
            try {
                val url = URL(manifestUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)

                    val remoteCode = json.optLong("versionCode", 1L)
                    val remoteName = json.optString("versionName", "1.0.0")
                    val title = json.optString("title", "DisciplineOS Update")
                    var apkUrl = json.optString("apkUrl", "")
                    val fileSizeBytes = json.optLong("fileSizeBytes", 0L)
                    val mandatory = json.optBoolean("mandatory", false)

                    // Fix relative or fallback URL if needed
                    if (apkUrl.isBlank() || apkUrl.startsWith("/")) {
                        val base = manifestUrl.substringBeforeLast('/')
                        apkUrl = "$base/DisciplineOS.apk"
                    } else if (manifestUrl.contains("10.0.2.2")) {
                        // In emulator context, redirect host if remote IP was given
                        try {
                            val parsedUri = Uri.parse(apkUrl)
                            val host = parsedUri.host
                            if (host != null && host != "10.0.2.2" && !host.startsWith("127.")) {
                                apkUrl = apkUrl.replace(host, "10.0.2.2")
                            }
                        } catch (_: Exception) {}
                    }

                    val notesList = mutableListOf<String>()
                    val notesJson = json.opt("releaseNotes")
                    if (notesJson is org.json.JSONArray) {
                        for (i in 0 until notesJson.length()) {
                            notesList.add(notesJson.getString(i))
                        }
                    } else if (notesJson is String && notesJson.isNotBlank()) {
                        notesList.addAll(notesJson.lines().filter { it.isNotBlank() })
                    }

                    val currentCode = getCurrentVersionCode(context)
                    val updateAvailable = remoteCode > currentCode || forceCheck

                    if (updateAvailable) {
                        val info = UpdateInfo(
                            versionCode = remoteCode,
                            versionName = remoteName,
                            title = title,
                            apkUrl = apkUrl,
                            releaseNotes = notesList,
                            fileSizeBytes = fileSizeBytes,
                            mandatory = mandatory
                        )
                        return@withContext Result.success(info)
                    } else {
                        return@withContext Result.success(null)
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Could not reach update server at $baseUrl"))
    }

    /**
     * Downloads the APK file with continuous live progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            val targetFile = File(updateDir, "DisciplineOS_v${updateInfo.versionName}.apk")
            val partFile = File(updateDir, "DisciplineOS_v${updateInfo.versionName}.apk.part")

            val url = URL(updateInfo.apkUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 30000
                requestMethod = "GET"
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Server returned HTTP ${conn.responseCode}"))
            }

            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else updateInfo.fileSizeBytes

            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                        onProgress(progress.coerceIn(0f, 1f), totalRead, totalBytes)
                    }
                    output.flush()
                }
            }

            if (partFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                partFile.renameTo(targetFile)
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Downloaded file is empty or missing"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Launches Android PackageInstaller intent to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) return false

        try {
            // Check unknown sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    // Still proceed with installation intent; system will prompt user to enable toggle
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
