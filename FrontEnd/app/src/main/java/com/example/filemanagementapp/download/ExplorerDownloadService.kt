package com.example.filemanagementapp.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.text.format.Formatter
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URLConnection
import kotlin.math.roundToInt

class ExplorerDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        val downloadUrl = intent?.getStringExtra(EXTRA_DOWNLOAD_URL).orEmpty()
        if (fileName.isBlank() || downloadUrl.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            buildProgressNotification(
                fileName = fileName,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = null
            )
        )

        serviceScope.launch {
            runCatching { downloadFile(fileName, downloadUrl) }
                .onSuccess {
                    ExplorerDownloadProgressStore.clear()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    NotificationManagerCompat.from(this@ExplorerDownloadService).notify(
                        NOTIFICATION_ID,
                        buildCompletedNotification(fileName)
                    )
                }
                .onFailure { throwable ->
                    ExplorerDownloadProgressStore.clear()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    NotificationManagerCompat.from(this@ExplorerDownloadService).notify(
                        NOTIFICATION_ID,
                        buildFailedNotification(fileName, throwable.message)
                    )
                }
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun downloadFile(fileName: String, downloadUrl: String) {
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()

        ExplorerNetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code}")
            }

            val body = response.body ?: throw IOException("Response body is empty")
            val totalBytes = body.contentLength().takeIf { it > 0L }
            val target = createTarget(fileName)

            target.outputStream.use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead = inputStream.read(buffer)
                    var downloadedBytes = 0L

                    while (bytesRead >= 0) {
                        if (bytesRead > 0) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            updateProgress(
                                fileName = fileName,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                        }
                        bytesRead = inputStream.read(buffer)
                    }
                    outputStream.flush()
                }
            }

            target.commit()
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun updateProgress(fileName: String, downloadedBytes: Long, totalBytes: Long?) {
        val progressPercent = if (totalBytes != null && totalBytes > 0L) {
            ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0)
                .coerceIn(0.0, 100.0)
                .roundToInt()
        } else {
            0
        }

        ExplorerDownloadProgressStore.update(
            ExplorerDownloadProgress(
                fileName = fileName,
                progressPercent = progressPercent,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
        )

        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            buildProgressNotification(fileName, progressPercent, downloadedBytes, totalBytes)
        )
    }

    private fun buildProgressNotification(
        fileName: String,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long?
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.download)
        .setContentTitle(getString(R.string.explorer_download_notification_title))
        .setContentText(
            if (totalBytes != null && totalBytes > 0L) {
                getString(
                    R.string.explorer_download_notification_progress,
                    progressPercent,
                    Formatter.formatShortFileSize(this, downloadedBytes),
                    Formatter.formatShortFileSize(this, totalBytes)
                )
            } else {
                getString(
                    R.string.explorer_download_notification_running,
                    fileName
                )
            }
        )
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, progressPercent, totalBytes == null)
        .build()

    private fun buildCompletedNotification(fileName: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download)
            .setContentTitle(getString(R.string.explorer_download_notification_done))
            .setContentText(
                getString(R.string.explorer_download_notification_done_message, fileName)
            )
            .setAutoCancel(true)
            .build()

    private fun buildFailedNotification(fileName: String, errorMessage: String?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download)
            .setContentTitle(getString(R.string.explorer_download_notification_failed))
            .setContentText(
                errorMessage?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.explorer_download_notification_failed_message, fileName)
            )
            .setAutoCancel(true)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.explorer_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.explorer_download_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createTarget(fileName: String): DownloadTarget {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStoreTarget(fileName)
        } else {
            createAppExternalTarget(fileName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createMediaStoreTarget(fileName: String): DownloadTarget {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, URLConnection.guessContentTypeFromName(fileName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Cannot create download entry")
        val outputStream = contentResolver.openOutputStream(uri)
            ?: throw IOException("Cannot open output stream")

        return DownloadTarget(
            outputStream = outputStream,
            commit = {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
        )
    }

    private fun createAppExternalTarget(fileName: String): DownloadTarget {
        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val targetFile = uniqueFile(downloadsDir, fileName)
        val outputStream = targetFile.outputStream()
        return DownloadTarget(outputStream = outputStream, commit = {})
    }

    private fun uniqueFile(parent: File, fileName: String): File {
        val original = File(parent, fileName)
        if (!original.exists()) {
            return original
        }

        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val candidateName = if (extension.isBlank()) {
                "$baseName ($index)"
            } else {
                "$baseName ($index).$extension"
            }
            val candidate = File(parent, candidateName)
            if (!candidate.exists()) {
                return candidate
            }
            index++
        }
    }

    private data class DownloadTarget(
        val outputStream: OutputStream,
        val commit: () -> Unit
    )

    companion object {
        private const val CHANNEL_ID = "explorer_downloads"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_DOWNLOAD_URL = "extra_download_url"

        fun start(context: Context, fileName: String, downloadUrl: String) {
            val intent = Intent(context, ExplorerDownloadService::class.java).apply {
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
