package com.discipline.os.agent

import android.content.Context
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Connecting(val modelName: String) : DownloadStatus()
    data class Downloading(
        val modelName: String,
        val fileName: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressFloat: Float, // 0.0 .. 1.0
        val speedMbPerSec: Double,
        val etaSeconds: Long
    ) : DownloadStatus()
    data class Completed(val modelName: String, val file: File) : DownloadStatus()
    data class Failed(val modelName: String, val error: String) : DownloadStatus()
    object Cancelled : DownloadStatus()
}

object ModelDownloadManager {
    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private var activeJob: Job? = null

    fun getModelsDir(context: Context): File {
        val external = context.getExternalFilesDir("models")
        val dir = external ?: File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(context: Context, fileName: String): File {
        return File(getModelsDir(context), fileName)
    }

    /**
     * Checks if a model file is present in either app storage or public downloads
     */
    fun findExistingModelFile(context: Context, modelIdentifier: String): File? {
        val cleanId = modelIdentifier.lowercase().replace(" ", "-").replace(":", "-")
        
        // 1. App external models folder
        val dir1 = getModelsDir(context)
        val f1 = dir1.listFiles()?.firstOrNull { 
            (it.name.contains(cleanId, ignoreCase = true) || cleanId.contains(it.nameWithoutExtension, ignoreCase = true)) && it.length() > 50_000_000L 
        }
        if (f1 != null) return f1

        // 2. Public Downloads / DisciplineOS / models (DownloadManager target)
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DisciplineOS/models")
        if (publicDir.exists()) {
            val f2 = publicDir.listFiles()?.firstOrNull {
                (it.name.contains(cleanId, ignoreCase = true) || cleanId.contains(it.nameWithoutExtension, ignoreCase = true)) && it.length() > 50_000_000L
            }
            if (f2 != null) return f2
        }

        // 3. Root Downloads folder
        val rootDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (rootDownloads.exists()) {
            val f3 = rootDownloads.listFiles()?.firstOrNull {
                it.name.endsWith(".gguf", ignoreCase = true) && 
                (it.name.contains(cleanId, ignoreCase = true) || cleanId.contains(it.nameWithoutExtension, ignoreCase = true)) && 
                it.length() > 50_000_000L
            }
            if (f3 != null) return f3
        }

        // 4. Internal filesDir
        val dirInternal = File(context.filesDir, "models")
        if (dirInternal.exists()) {
            val f4 = dirInternal.listFiles()?.firstOrNull {
                (it.name.contains(cleanId, ignoreCase = true) || cleanId.contains(it.nameWithoutExtension, ignoreCase = true)) && it.length() > 50_000_000L
            }
            if (f4 != null) return f4
        }

        return null
    }

    fun isModelDownloaded(context: Context, modelIdentifier: String): Boolean {
        return findExistingModelFile(context, modelIdentifier) != null
    }

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        val maxRedirects = 6

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "DisciplineOS-Mobile-Downloader/2.12.0")

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308
            ) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (!newUrl.isNullOrBlank()) {
                    currentUrl = newUrl
                    redirects++
                    continue
                }
            }
            return connection
        }
        throw Exception("Too many redirects: $redirects")
    }

    fun startDownload(
        context: Context,
        modelName: String,
        downloadUrl: String,
        onAutoConfigure: (File) -> Unit
    ) {
        if (_downloadStatus.value is DownloadStatus.Downloading) {
            Toast.makeText(context, "A model download is already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        val rawFileName = downloadUrl.substringAfterLast("/").substringBefore("?").ifBlank { "model.gguf" }
        val fileName = if (rawFileName.endsWith(".gguf", ignoreCase = true)) rawFileName else "$rawFileName.gguf"
        val destinationFile = getModelFile(context, fileName)

        _downloadStatus.value = DownloadStatus.Connecting(modelName)

        activeJob = CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            var inputStream: BufferedInputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                connection = openConnectionWithRedirects(downloadUrl)
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw Exception("Server returned HTTP $responseCode: ${connection.responseMessage}")
                }

                val totalLength = connection.contentLengthLong.takeIf { it > 0 }
                    ?: connection.contentLength.toLong()

                inputStream = BufferedInputStream(connection.inputStream, 128 * 1024)
                outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(128 * 1024) // 128 KB buffer for ultra-fast throughput
                var totalBytesRead = 0L

                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L
                var speedMb = 0.0

                while (true) {
                    if (!isActive) {
                        try { outputStream.close() } catch (_: Exception) {}
                        try { inputStream.close() } catch (_: Exception) {}
                        destinationFile.delete()
                        _downloadStatus.value = DownloadStatus.Cancelled
                        return@launch
                    }

                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    val deltaMs = now - lastTime
                    if (deltaMs >= 500) {
                        speedMb = (bytesSinceLastTime / (1024.0 * 1024.0)) / (deltaMs / 1000.0)
                        lastTime = now
                        bytesSinceLastTime = 0L

                        val progress = if (totalLength > 0) totalBytesRead.toFloat() / totalLength.toFloat() else 0f
                        val remainingBytes = totalLength - totalBytesRead
                        val bytesPerSec = speedMb * 1024.0 * 1024.0
                        val etaSec = if (bytesPerSec > 50_000.0) (remainingBytes / bytesPerSec).toLong() else 0L

                        _downloadStatus.value = DownloadStatus.Downloading(
                            modelName = modelName,
                            fileName = fileName,
                            bytesDownloaded = totalBytesRead,
                            totalBytes = totalLength,
                            progressFloat = progress,
                            speedMbPerSec = speedMb,
                            etaSeconds = etaSec
                        )
                    }
                }

                outputStream.flush()
                outputStream.close()
                outputStream = null
                inputStream.close()
                inputStream = null

                _downloadStatus.value = DownloadStatus.Completed(modelName, destinationFile)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$modelName Download Complete! Auto-configured.", Toast.LENGTH_LONG).show()
                    onAutoConfigure(destinationFile)
                }
            } catch (e: Exception) {
                if (isActive) {
                    _downloadStatus.value = DownloadStatus.Failed(modelName, e.localizedMessage ?: "Download failed")
                }
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { inputStream?.close() } catch (_: Exception) {}
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    fun cancelDownload() {
        activeJob?.cancel()
        activeJob = null
        _downloadStatus.value = DownloadStatus.Cancelled
    }

    fun resetStatus() {
        _downloadStatus.value = DownloadStatus.Idle
    }
}
