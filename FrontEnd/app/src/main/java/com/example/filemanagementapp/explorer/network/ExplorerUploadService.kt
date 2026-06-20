package com.example.filemanagementapp.explorer.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.text.format.Formatter
import androidx.annotation.RequiresPermission
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

class ExplorerUploadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileUriString = intent?.getStringExtra(EXTRA_FILE_URI).orEmpty()
        val targetPath = intent?.getStringExtra(EXTRA_TARGET_PATH).orEmpty()
        val username = intent?.getStringExtra(EXTRA_USERNAME).orEmpty()

        if (fileUriString.isBlank() || username.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val fileUri = Uri.parse(fileUriString)
        val fileName = resolveDisplayName(fileUri) ?: "upload-${System.currentTimeMillis()}"

        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            buildProgressNotification(
                fileName = fileName,
                progressPercent = 0,
                uploadedBytes = 0L,
                totalBytes = null
            )
        )

        serviceScope.launch {
            runCatching { uploadFile(fileUri, fileName, targetPath, username) }
                .onSuccess {
                    ExplorerUploadProgressStore.update(
                        ExplorerUploadProgress(
                            fileName = fileName,
                            progressPercent = 100,
                            uploadedBytes = 0L,
                            totalBytes = 0L,
                            status = UploadStatus.SUCCESS
                        )
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    NotificationManagerCompat.from(this@ExplorerUploadService).notify(
                        NOTIFICATION_ID,
                        buildCompletedNotification(fileName)
                    )
                }
                .onFailure { throwable ->
                    ExplorerUploadProgressStore.update(
                        ExplorerUploadProgress(
                            fileName = fileName,
                            progressPercent = 0,
                            uploadedBytes = 0L,
                            totalBytes = 0L,
                            status = UploadStatus.FAILED,
                            errorMessage = throwable.message
                        )
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    NotificationManagerCompat.from(this@ExplorerUploadService).notify(
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

    private suspend fun uploadFile(fileUri: Uri, fileName: String, targetPath: String, username: String) {
        val tempFile = File.createTempFile("upload-", "-$fileName", cacheDir)
        contentResolver.openInputStream(fileUri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Khong the doc file duoc chon")

        val fileRequestBody = tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val progressRequestBody = ProgressRequestBody(fileRequestBody) { bytesWritten, contentLength ->
            updateProgress(fileName, bytesWritten, contentLength.takeIf { it > 0L })
        }

        val response = ExplorerNetworkModule.uploadApiService.uploadFile(
            username = username.toRequestBody("text/plain".toMediaTypeOrNull()),
            targetPath = targetPath.toRequestBody("text/plain".toMediaTypeOrNull()),
            file = MultipartBody.Part.createFormData("file", fileName, progressRequestBody)
        )

        tempFile.delete()

        if (!response.isSuccessful) {
            throw IOException("Upload failed: ${response.code()}")
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun updateProgress(fileName: String, uploadedBytes: Long, totalBytes: Long?) {
        val progressPercent = if (totalBytes != null && totalBytes > 0L) {
            ((uploadedBytes.toDouble() / totalBytes.toDouble()) * 100.0)
                .coerceIn(0.0, 100.0)
                .roundToInt()
        } else {
            0
        }

        ExplorerUploadProgressStore.update(
            ExplorerUploadProgress(
                fileName = fileName,
                progressPercent = progressPercent,
                uploadedBytes = uploadedBytes,
                totalBytes = totalBytes,
                status = UploadStatus.RUNNING
            )
        )

        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            buildProgressNotification(fileName, progressPercent, uploadedBytes, totalBytes)
        )
    }

    private fun buildProgressNotification(
        fileName: String,
        progressPercent: Int,
        uploadedBytes: Long,
        totalBytes: Long?
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.cloud) // ensure this exists, fallback to download icon if not
        .setContentTitle(getString(R.string.explorer_upload_notification_title))
        .setContentText(
            if (totalBytes != null && totalBytes > 0L) {
                getString(
                    R.string.explorer_upload_notification_progress,
                    progressPercent,
                    Formatter.formatShortFileSize(this, uploadedBytes),
                    Formatter.formatShortFileSize(this, totalBytes)
                )
            } else {
                getString(
                    R.string.explorer_upload_notification_running,
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
            .setSmallIcon(R.drawable.cloud)
            .setContentTitle(getString(R.string.explorer_upload_notification_done))
            .setContentText(
                getString(R.string.explorer_upload_notification_done_message, fileName)
            )
            .setAutoCancel(true)
            .build()

    private fun buildFailedNotification(fileName: String, errorMessage: String?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.cloud)
            .setContentTitle(getString(R.string.explorer_upload_notification_failed))
            .setContentText(
                errorMessage?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.explorer_upload_notification_failed_message, fileName)
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
            getString(R.string.explorer_upload_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.explorer_upload_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "explorer_uploads"
        private const val NOTIFICATION_ID = 2002
        private const val EXTRA_FILE_URI = "extra_file_uri"
        private const val EXTRA_TARGET_PATH = "extra_target_path"
        private const val EXTRA_USERNAME = "extra_username"

        fun start(context: Context, fileUri: Uri, targetPath: String, username: String) {
            val intent = Intent(context, ExplorerUploadService::class.java).apply {
                putExtra(EXTRA_FILE_URI, fileUri.toString())
                putExtra(EXTRA_TARGET_PATH, targetPath)
                putExtra(EXTRA_USERNAME, username)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
