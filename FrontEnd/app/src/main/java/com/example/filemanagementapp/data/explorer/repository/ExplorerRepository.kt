package com.example.filemanagementapp.data.explorer.repository

import android.content.Context
import android.net.Uri
import com.example.filemanagementapp.BuildConfig
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.model.ExplorerCreateFolderRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerDeleteFileRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerDeleteFolderRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerDirectoryPayload
import com.example.filemanagementapp.data.explorer.model.ExplorerListRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerListResponse
import com.example.filemanagementapp.data.explorer.model.ExplorerMoveFileRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerMoveFolderRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerMutationResponse
import com.example.filemanagementapp.data.explorer.model.ExplorerRenameFileRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerRenameFolderRequest
import com.example.filemanagementapp.data.explorer.network.ExplorerApiService
import com.example.filemanagementapp.explorer.ExplorerBreadcrumbItem
import com.example.filemanagementapp.explorer.ExplorerItem
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ExplorerRepository(
    private val appContext: Context,
    private val explorerApiService: ExplorerApiService,
    private val gson: Gson
) {
    suspend fun listDirectory(
        username: String,
        folderPath: String
    ): Result<ExplorerDirectoryData> = withContext(Dispatchers.IO) {
        try {
            val response = explorerApiService.listDirectory(
                ExplorerListRequest(
                    username = username,
                    folderPath = folderPath
                )
            )
            response.toDirectoryResult()
        } catch (ioException: IOException) {
            Result.failure(
                Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
            )
        } catch (exception: Exception) {
            Result.failure(Exception(exception.message ?: "Khong the tai danh sach thu muc"))
        }
    }

    suspend fun renameItem(
        username: String,
        item: ExplorerItem,
        newName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = when (item.type) {
                ExplorerItem.Type.FILE -> explorerApiService.renameFile(
                    ExplorerRenameFileRequest(
                        username = username,
                        filePath = item.path,
                        newFileName = newName
                    )
                )

                ExplorerItem.Type.FOLDER -> explorerApiService.renameFolder(
                    ExplorerRenameFolderRequest(
                        username = username,
                        folderPath = item.path,
                        newFolderName = newName
                    )
                )
            }
            response.toMutationResult()
        } catch (ioException: IOException) {
            Result.failure(Exception("Khong the ket noi toi server. Kiem tra backend va mang."))
        } catch (exception: Exception) {
            Result.failure(Exception(exception.message ?: "Khong the doi ten muc"))
        }
    }

    suspend fun createFolder(
        username: String,
        targetPath: String,
        folderName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = explorerApiService.createFolder(
                ExplorerCreateFolderRequest(
                    username = username,
                    targetPath = targetPath,
                    folderName = folderName
                )
            )
            response.toMutationResult()
        } catch (ioException: IOException) {
            Result.failure(Exception("Khong the ket noi toi server. Kiem tra backend va mang."))
        } catch (exception: Exception) {
            Result.failure(Exception(exception.message ?: "Khong the tao folder"))
        }
    }

    suspend fun uploadFile(
        username: String,
        targetPath: String,
        fileUri: Uri
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val resolver = appContext.contentResolver
            val fileName = resolveDisplayName(fileUri) ?: "upload-${System.currentTimeMillis()}"
            val tempFile = File.createTempFile("upload-", "-$fileName", appContext.cacheDir)
            resolver.openInputStream(fileUri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(Exception("Khong the doc file duoc chon"))

            val response = explorerApiService.uploadFile(
                username = username.toRequestBody("text/plain".toMediaTypeOrNull()),
                targetPath = targetPath.toRequestBody("text/plain".toMediaTypeOrNull()),
                file = MultipartBody.Part.createFormData(
                    "file",
                    fileName,
                    tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
            )
            tempFile.delete()
            val mutationResult = response.toMutationResult()
            mutationResult.map { message -> Pair(message, fileName) }
        } catch (ioException: IOException) {
            Result.failure(Exception("Khong the ket noi toi server. Kiem tra backend va mang."))
        } catch (exception: Exception) {
            Result.failure(Exception(exception.message ?: "Khong the upload file"))
        }
    }

    suspend fun moveItem(
        username: String,
        item: ExplorerItem,
        targetFolderPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = when (item.type) {
                ExplorerItem.Type.FILE -> explorerApiService.moveFile(
                    ExplorerMoveFileRequest(
                        username = username,
                        sourceFilePath = item.path,
                        targetFolderPath = targetFolderPath
                    )
                )

                ExplorerItem.Type.FOLDER -> explorerApiService.moveFolder(
                    ExplorerMoveFolderRequest(
                        username = username,
                        sourceFolderPath = item.path,
                        targetFolderPath = targetFolderPath
                    )
                )
            }
            response.toMutationResult()
        } catch (ioException: IOException) {
            Result.failure(Exception("Khong the ket noi toi server. Kiem tra backend va mang."))
        } catch (exception: Exception) {
            Result.failure(Exception(exception.message ?: "Khong the di chuyen muc"))
        }
    }

    suspend fun deleteItem(
        username: String,
        item: ExplorerItem
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = when (item.type) {
                ExplorerItem.Type.FILE -> explorerApiService.deleteFile(
                    ExplorerDeleteFileRequest(
                        username = username,
                        filePath = item.path
                    )
                )

                ExplorerItem.Type.FOLDER -> explorerApiService.deleteFolder(
                    ExplorerDeleteFolderRequest(
                        username = username,
                        folderPath = item.path
                    )
                )
            }
            response.toMutationResult()
        } catch (ioException: IOException) {
            Result.failure(Exception("Khong the ket noi toi server. Kiem tra backend va mang."))
        } catch (exception: Exception) {
            Result.failure(Exception(exception.message ?: "Khong the xoa muc"))
        }
    }

    private fun Response<ExplorerListResponse>.toDirectoryResult(): Result<ExplorerDirectoryData> {
        if (isSuccessful) {
            val payload = body()?.data
                ?: return Result.failure(Exception("Server khong tra ve du lieu thu muc hop le"))

            return Result.success(payload.toDirectoryData())
        }

        val parsedError = errorBody()?.charStream()?.use { reader ->
            runCatching { gson.fromJson(reader, ExplorerListResponse::class.java) }.getOrNull()
        }
        val errorMessage = parsedError?.error
            ?: parsedError?.detail
            ?: message()
            ?: "Khong the tai danh sach thu muc"
        return Result.failure(Exception(errorMessage))
    }

    private fun Response<ExplorerMutationResponse>.toMutationResult(): Result<String> {
        if (isSuccessful) {
            return Result.success(body()?.message ?: "Thao tac thanh cong")
        }

        val parsedError = errorBody()?.charStream()?.use { reader ->
            runCatching { gson.fromJson(reader, ExplorerMutationResponse::class.java) }.getOrNull()
        }
        val errorMessage = parsedError?.error
            ?: parsedError?.detail
            ?: message()
            ?: "Khong the thuc hien thao tac"
        return Result.failure(Exception(errorMessage))
    }

    private fun ExplorerDirectoryPayload.toDirectoryData(): ExplorerDirectoryData {
        val normalizedFolder = currentFolder.orEmpty().trim('/')
        val explorerItems = buildList {
            folders.forEach { folder ->
                val folderName = folder.name.orEmpty()
                if (folderName.isNotBlank() && !folderName.equals("trash", ignoreCase = true) && !folderName.equals(".trash", ignoreCase = true)) {
                    add(
                        ExplorerItem(
                            id = "folder:${joinPath(normalizedFolder, folderName)}",
                            name = folderName,
                            path = joinPath(normalizedFolder, folderName),
                            type = ExplorerItem.Type.FOLDER,
                            itemCount = folder.itemCount,
                            modified = formatDate(folder.modifiedAt),
                            modifiedEpochMillis = parseEpochMillis(folder.modifiedAt)
                        )
                    )
                }
            }
            files.forEach { file ->
                val fileName = file.name.orEmpty()
                if (fileName.isNotBlank()) {
                    add(
                        ExplorerItem(
                            id = "file:${joinPath(normalizedFolder, fileName)}",
                            name = fileName,
                            path = joinPath(normalizedFolder, fileName),
                            type = ExplorerItem.Type.FILE,
                            modified = formatDate(file.modifiedAt),
                            modifiedEpochMillis = parseEpochMillis(file.modifiedAt),
                            size = file.size?.let(::formatSize),
                            sizeBytes = file.size,
                            previewUrl = buildPreviewUrl(username.orEmpty(), joinPath(normalizedFolder, fileName)),
                            isImagePreviewable = isImageFile(fileName),
                            fallbackIconRes = resolveFallbackIcon(fileName),
                            tags = buildFileTags(fileName)
                        )
                    )
                }
            }
        }

        return ExplorerDirectoryData(
            currentFolder = normalizedFolder,
            storageSummary = buildStorageSummary(
                totalUsedBytes = totalUsedBytes ?: 0L
            ),
            breadcrumbs = buildBreadcrumbs(normalizedFolder),
            items = explorerItems
        )
    }

    private fun buildBreadcrumbs(currentFolder: String): List<ExplorerBreadcrumbItem> {
        if (currentFolder.isBlank()) return emptyList()

        val segments = currentFolder.split('/').filter { it.isNotBlank() }
        var cumulativePath = ""
        return segments.map { segment ->
            cumulativePath = joinPath(cumulativePath, segment)
            ExplorerBreadcrumbItem(
                title = segment,
                path = cumulativePath
            )
        }
    }

    private fun joinPath(parent: String, child: String): String {
        val normalizedParent = parent.trim('/')
        val normalizedChild = child.trim('/')
        return when {
            normalizedParent.isBlank() -> normalizedChild
            normalizedChild.isBlank() -> normalizedParent
            else -> "$normalizedParent/$normalizedChild"
        }
    }

    fun buildChildPath(parentPath: String, childName: String): String {
        return joinPath(parentPath, childName)
    }

    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            OffsetDateTime.parse(value).format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            )
        }.getOrDefault(value)
    }

    private fun parseEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun resolveDisplayName(fileUri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return appContext.contentResolver.query(fileUri, projection, null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
    }

    private fun formatSize(sizeInBytes: Long): String {
        if (sizeInBytes < 1024) return "$sizeInBytes B"

        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = sizeInBytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }

        val formatted = if (value >= 10 || value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format("%.1f", value)
        }
        return "$formatted ${units[unitIndex]}"
    }

    private fun buildPreviewUrl(username: String, filePath: String): String {
        val normalizedBaseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$normalizedBaseUrl/api/data/download?username=${encodeQuery(username)}&filePath=${encodeQuery(filePath)}"
    }

    private fun encodeQuery(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun isImageFile(fileName: String): Boolean {
        val normalizedName = fileName.lowercase()
        return normalizedName.endsWith(".jpg") ||
            normalizedName.endsWith(".jpeg") ||
            normalizedName.endsWith(".png") ||
            normalizedName.endsWith(".webp") ||
            normalizedName.endsWith(".bmp") ||
            normalizedName.endsWith(".gif")
    }

    private fun resolveFallbackIcon(fileName: String): Int {
        val normalizedName = fileName.lowercase()
        return when {
            normalizedName.endsWith(".jpg") ||
                normalizedName.endsWith(".jpeg") ||
                normalizedName.endsWith(".png") ||
                normalizedName.endsWith(".webp") ||
                normalizedName.endsWith(".bmp") ||
                normalizedName.endsWith(".gif") -> R.drawable.image_icon
            normalizedName.endsWith(".mp4") ||
                normalizedName.endsWith(".mov") ||
                normalizedName.endsWith(".avi") ||
                normalizedName.endsWith(".mkv") ||
                normalizedName.endsWith(".webm") -> R.drawable.film
            normalizedName.endsWith(".mp3") ||
                normalizedName.endsWith(".wav") ||
                normalizedName.endsWith(".aac") ||
                normalizedName.endsWith(".m4a") ||
                normalizedName.endsWith(".flac") -> R.drawable.file_audio
            normalizedName.endsWith(".pdf") -> R.drawable.file_text
            else -> R.drawable.file_text
        }
    }

    private fun buildFileTags(fileName: String): List<String> {
        val extension = fileName.substringAfterLast('.', "").trim()
        if (extension.isBlank()) return emptyList()
        return listOf(extension.uppercase())
    }

    private fun buildStorageSummary(totalUsedBytes: Long): String {
        return "Storage: ${formatSize(totalUsedBytes)} used"
    }
}

data class ExplorerDirectoryData(
    val currentFolder: String,
    val storageSummary: String,
    val breadcrumbs: List<ExplorerBreadcrumbItem>,
    val items: List<ExplorerItem>
)
