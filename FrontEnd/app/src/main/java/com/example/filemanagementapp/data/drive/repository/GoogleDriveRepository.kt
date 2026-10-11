package com.example.filemanagementapp.data.drive.repository

import android.content.Context
import com.example.filemanagementapp.R
import com.example.filemanagementapp.explorer.ExplorerItem
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoogleDriveRepository {

    suspend fun listFiles(
        drive: Drive,
        parentFolderId: String = "root"
    ): Result<List<ExplorerItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val rawFiles = mutableListOf<File>()

            if (parentFolderId == "shared-with-me") {
                // Tải danh sách tệp/thư mục được chia sẻ với người dùng
                val sharedList = drive.files().list()
                    .setQ("sharedWithMe = true and trashed = false")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setFields("files(id, name, mimeType, size, modifiedTime, thumbnailLink, webContentLink, webViewLink, iconLink, shared, owners(displayName, me), parents, shortcutDetails(targetId, targetMimeType))")
                    .setOrderBy("folder, name")
                    .setPageSize(100)
                    .execute()

                val allShared = sharedList.files.orEmpty()
                // Lọc bỏ những file con mà thư mục cha của nó đã nằm trong danh sách được chia sẻ để tránh trùng lặp
                val sharedFolderIds = allShared.mapNotNull { file ->
                    val isFolder = file.mimeType == "application/vnd.google-apps.folder" ||
                        (file.mimeType == "application/vnd.google-apps.shortcut" && file.shortcutDetails?.targetMimeType == "application/vnd.google-apps.folder")
                    if (isFolder) {
                        file.shortcutDetails?.targetId ?: file.id
                    } else null
                }.toSet()

                val topLevelShared = allShared.filter { file ->
                    val parents = file.parents.orEmpty()
                    parents.none { it in sharedFolderIds }
                }
                rawFiles.addAll(topLevelShared)
            } else {
                // Tải danh sách tệp thuộc thư mục hiện tại trong Drive của tôi (không lấy tràn lan sharedWithMe)
                val mainQuery = "'$parentFolderId' in parents and trashed = false"
                val fileList = drive.files().list()
                    .setQ(mainQuery)
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setFields("files(id, name, mimeType, size, modifiedTime, thumbnailLink, webContentLink, webViewLink, iconLink, shared, owners(displayName, me), shortcutDetails(targetId, targetMimeType))")
                    .setOrderBy("folder, name")
                    .setPageSize(100)
                    .execute()

                rawFiles.addAll(fileList.files.orEmpty())
            }

            // Loại bỏ trùng lặp nếu có
            val distinctFiles = rawFiles.distinctBy { it.id }

            // Chuyển đổi thành ExplorerItem
            val items = distinctFiles.map { file ->
                mapDriveFileToExplorerItem(file)
            }.toMutableList()

            // Nếu đang ở thư mục gốc của Google Drive (root), thêm một mục thư mục "Được chia sẻ với tôi"
            if (parentFolderId == "root") {
                items.add(
                    0,
                    ExplorerItem(
                        id = "gdrive_shared_with_me",
                        name = "Được chia sẻ với tôi",
                        path = "gdrive://shared-with-me",
                        type = ExplorerItem.Type.FOLDER,
                        modified = "",
                        driveFileId = "shared-with-me",
                        isGoogleDriveItem = true,
                        size = "Thư mục chia sẻ",
                        fallbackIconRes = R.drawable.users
                    )
                )
            }

            // 3. Đếm số lượng item trong mỗi folder (chạy song song bằng coroutines async)
            val itemsWithCount = items.map { item ->
                async {
                    if (item.type == ExplorerItem.Type.FOLDER && !item.driveFileId.isNullOrBlank() && item.driveFileId != "shared-with-me") {
                        val count = runCatching {
                            val countRes = drive.files().list()
                                .setQ("'${item.driveFileId}' in parents and trashed = false")
                                .setSupportsAllDrives(true)
                                .setIncludeItemsFromAllDrives(true)
                                .setFields("files(id)")
                                .setPageSize(500)
                                .execute()
                            countRes.files?.size ?: 0
                        }.getOrDefault(0)

                        item.copy(
                            itemCount = count,
                            size = "$count mục"
                        )
                    } else {
                        item
                    }
                }
            }.awaitAll()

            // Sắp xếp thư mục trước, tệp sau (thư mục "Được chia sẻ với tôi" luôn đứng đầu)
            itemsWithCount.sortedWith(
                compareBy<ExplorerItem> { it.type != ExplorerItem.Type.FOLDER }
                    .thenBy { if (it.id == "gdrive_shared_with_me") 0 else 1 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        }
    }

    suspend fun downloadFile(
        drive: Drive,
        fileId: String,
        fileName: String,
        mimeType: String?,
        totalBytes: Long? = null,
        context: Context,
        onProgress: ((downloadedBytes: Long, totalBytes: Long?, percent: Int) -> Unit)? = null
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = mimeType?.lowercase().orEmpty()
            val isGoogleDoc = mime == "application/vnd.google-apps.document"
            val isGoogleSheet = mime == "application/vnd.google-apps.spreadsheet"
            val isGoogleSlide = mime == "application/vnd.google-apps.presentation"
            val isGoogleDocsEditor = isGoogleDoc || isGoogleSlide || mime.startsWith("application/vnd.google-apps.")

            val targetFileName = when {
                (isGoogleDoc || isGoogleSlide || isGoogleDocsEditor) && !isGoogleSheet && !fileName.endsWith(".pdf", ignoreCase = true) -> "$fileName.pdf"
                isGoogleSheet && !fileName.endsWith(".xlsx", ignoreCase = true) -> "$fileName.xlsx"
                else -> fileName
            }

            val targetFile = java.io.File(context.cacheDir, targetFileName)
            var resultFile = targetFile

            onProgress?.invoke(0L, totalBytes, 0)

            try {
                val inputStream = when {
                    isGoogleSheet -> {
                        drive.files().export(fileId, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .executeMediaAsInputStream()
                    }
                    isGoogleDocsEditor -> {
                        drive.files().export(fileId, "application/pdf")
                            .executeMediaAsInputStream()
                    }
                    else -> {
                        drive.files().get(fileId)
                            .setSupportsAllDrives(true)
                            .executeMediaAsInputStream()
                    }
                }

                inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val percent = if (totalBytes != null && totalBytes > 0L) {
                                ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 99)
                            } else {
                                0
                            }
                            onProgress?.invoke(totalRead, totalBytes, percent)
                        }
                    }
                }
            } catch (e: Exception) {
                // Nếu get(fileId) báo lỗi Docs Editors (fileNotDownloadable / 403), tự động fallback sang export PDF
                val errMsg = e.message.orEmpty()
                if (errMsg.contains("Docs Editors", ignoreCase = true) || errMsg.contains("fileNotDownloadable", ignoreCase = true) || errMsg.contains("Only files with binary", ignoreCase = true)) {
                    val fallbackFile = if (!targetFileName.endsWith(".pdf", ignoreCase = true)) {
                        java.io.File(context.cacheDir, "$fileName.pdf")
                    } else {
                        targetFile
                    }
                    val exportStream = drive.files().export(fileId, "application/pdf").executeMediaAsInputStream()
                    exportStream.use { input ->
                        FileOutputStream(fallbackFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                onProgress?.invoke(totalRead, null, 0)
                            }
                        }
                    }
                    resultFile = fallbackFile
                } else {
                    throw e
                }
            }
            onProgress?.invoke(resultFile.length(), resultFile.length(), 100)
            resultFile
        }
    }

    suspend fun createFolder(
        drive: Drive,
        parentFolderId: String = "root",
        folderName: String
    ): Result<ExplorerItem> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentFolderId)
            }
            val created = drive.files().create(metadata)
                .setSupportsAllDrives(true)
                .setFields("id, name, mimeType, modifiedTime")
                .execute()
            mapDriveFileToExplorerItem(created)
        }
    }

    suspend fun uploadFile(
        drive: Drive,
        parentFolderId: String = "root",
        localFile: java.io.File,
        mimeType: String,
        fileName: String? = null
    ): Result<ExplorerItem> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = File().apply {
                name = fileName ?: localFile.name
                parents = listOf(parentFolderId)
            }
            val mediaContent = FileContent(mimeType, localFile)
            val created = drive.files().create(metadata, mediaContent)
                .setSupportsAllDrives(true)
                .setFields("id, name, mimeType, size, modifiedTime, thumbnailLink, webContentLink, webViewLink")
                .execute()
            mapDriveFileToExplorerItem(created)
        }
    }

    suspend fun deleteFile(
        drive: Drive,
        fileId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            drive.files().delete(fileId)
                .setSupportsAllDrives(true)
                .execute()
            Unit
        }
    }

    private fun mapDriveFileToExplorerItem(file: File): ExplorerItem {
        val isShortcut = file.mimeType == "application/vnd.google-apps.shortcut"
        val shortcutDetails = file.shortcutDetails
        val effectiveMimeType = if (isShortcut && !shortcutDetails?.targetMimeType.isNullOrBlank()) {
            shortcutDetails.targetMimeType
        } else {
            file.mimeType
        }
        val effectiveFileId = if (isShortcut && !shortcutDetails?.targetId.isNullOrBlank()) {
            shortcutDetails.targetId
        } else {
            file.id
        }

        val isFolder = effectiveMimeType == "application/vnd.google-apps.folder"
        val sizeBytes = file.getSize()?.toLong()
        val sizeFormatted = when {
            isFolder -> "--" // Sẽ được cập nhật chính xác bởi async counting
            sizeBytes != null && sizeBytes > 0 -> formatFileSize(sizeBytes)
            effectiveMimeType?.startsWith("application/vnd.google-apps.") == true -> {
                when (effectiveMimeType) {
                    "application/vnd.google-apps.document" -> "Google Docs"
                    "application/vnd.google-apps.spreadsheet" -> "Google Sheets"
                    "application/vnd.google-apps.presentation" -> "Google Slides"
                    "application/vnd.google-apps.form" -> "Google Forms"
                    else -> "Tài liệu Google"
                }
            }
            else -> "--"
        }
        val modifiedFormatted = file.modifiedTime?.let {
            formatModifiedTime(it.value)
        } ?: ""

        val isImage = effectiveMimeType?.startsWith("image/") == true
        val fallbackIcon = when {
            isFolder -> R.drawable.folder
            isImage -> R.drawable.image_icon
            effectiveMimeType?.contains("pdf") == true -> R.drawable.file_text
            effectiveMimeType?.startsWith("video/") == true -> R.drawable.film
            effectiveMimeType?.startsWith("audio/") == true -> R.drawable.file_audio
            else -> R.drawable.file_text
        }

        return ExplorerItem(
            id = file.id,
            name = file.name ?: "Untitled",
            path = "gdrive://$effectiveFileId",
            type = if (isFolder) ExplorerItem.Type.FOLDER else ExplorerItem.Type.FILE,
            modified = modifiedFormatted,
            modifiedEpochMillis = file.modifiedTime?.value,
            size = sizeFormatted,
            sizeBytes = sizeBytes,
            previewUrl = file.webContentLink ?: file.thumbnailLink,
            isImagePreviewable = isImage,
            fallbackIconRes = fallbackIcon,
            isGoogleDriveItem = true,
            driveFileId = effectiveFileId,
            driveShortcutId = if (isShortcut) file.id else null,
            driveMimeType = effectiveMimeType,
            driveWebViewLink = file.webViewLink,
            ownerUsername = if (file.shared == true) {
                file.owners?.firstOrNull { it.me == false }?.displayName ?: "Được chia sẻ"
            } else null
        )
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    private fun formatModifiedTime(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }
}
