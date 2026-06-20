package com.example.filemanagementapp.search

import com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository
import com.example.filemanagementapp.data.local.ai.AiAnalysisStatus
import com.example.filemanagementapp.data.local.explorer.DirectoryCacheLocalRepository
import com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchRepository(
    private val directoryCacheLocalRepository: DirectoryCacheLocalRepository,
    private val aiAnalysisLocalRepository: AiAnalysisLocalRepository,
    private val favoriteLocalRepository: FavoriteLocalRepository
) {
    suspend fun loadAllSearchItems(username: String): List<SearchItem> = withContext(Dispatchers.IO) {
        val cachedDirectories = directoryCacheLocalRepository.getAllCachedDirectories(username)
        val allItems = cachedDirectories.flatMap { it.items }
            .associateBy { it.path }
            .values
            .filter { isItemVisible(it.path) }
            .toList()

        val analysisMap = aiAnalysisLocalRepository.getAnalysisByPaths(
            username = username,
            filePaths = allItems.filter { it.type == ExplorerItem.Type.FILE }.map { it.path }
        )

        val favoritePaths = favoriteLocalRepository.getFavoritePaths(
            username = username,
            paths = allItems.map { it.path }
        )

        allItems.map { item ->
            val isFolder = item.type == ExplorerItem.Type.FOLDER
            val analysis = analysisMap[item.path]
            val isFavorite = item.path in favoritePaths

            val aiTags = analysis?.tags
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                .orEmpty()
            
            // Merge original item tags (like file extensions) with AI tags
            val mergedTags = (item.tags + aiTags)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val finalTags = if (analysis?.status == com.example.filemanagementapp.data.local.ai.AiAnalysisStatus.COMPLETED) {
                mergedTags + "AI analyzed"
            } else {
                mergedTags
            }

            var finalSizeBytes = item.sizeBytes
            var finalSizeStr = item.size
            if (analysis?.previewImagePath != null) {
                val file = java.io.File(analysis.previewImagePath)
                if (file.exists()) {
                    finalSizeBytes = (finalSizeBytes ?: 0L) + file.length()
                    finalSizeStr = formatSize(finalSizeBytes)
                }
            }

            SearchItem(
                id = item.id,
                name = item.name,
                path = item.path,
                type = if (isFolder) "Folder" else resolveFileTypeString(item.name),
                size = finalSizeStr,
                date = item.modified,
                modifiedEpochMillis = item.modifiedEpochMillis,
                category = if (isFolder) SearchItem.Category.FOLDER else resolveCategory(item.name),
                ocrText = analysis?.ocrText?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim(),
                tags = finalTags,
                aiTags = aiTags,
                isFavorite = isFavorite,
                rawItem = item.copy(
                    aiAnalyzed = analysis?.status == com.example.filemanagementapp.data.local.ai.AiAnalysisStatus.COMPLETED,
                    isFavorite = isFavorite,
                    tags = finalTags,
                    analyzedImagePath = analysis?.previewImagePath,
                    sizeBytes = finalSizeBytes,
                    size = finalSizeStr
                )
            )
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
            String.format(java.util.Locale.US, "%.1f", value)
        }
        return "$formatted ${units[unitIndex]}"
    }

    private fun resolveFileTypeString(fileName: String): String {
        val normalizedName = fileName.lowercase()
        return when {
            normalizedName.endsWith(".jpg") || normalizedName.endsWith(".png") || normalizedName.endsWith(".jpeg") -> "Image"
            normalizedName.endsWith(".pdf") -> "PDF"
            normalizedName.endsWith(".mp4") || normalizedName.endsWith(".mov") -> "Video"
            normalizedName.endsWith(".mp3") || normalizedName.endsWith(".wav") -> "Audio"
            else -> "Document"
        }
    }

    private fun resolveCategory(fileName: String): SearchItem.Category {
        val normalizedName = fileName.lowercase()
        return when {
            normalizedName.endsWith(".jpg") || normalizedName.endsWith(".png") || normalizedName.endsWith(".jpeg") || normalizedName.endsWith(".webp") -> SearchItem.Category.IMAGE
            normalizedName.endsWith(".pdf") -> SearchItem.Category.PDF
            normalizedName.endsWith(".mp4") || normalizedName.endsWith(".mov") || normalizedName.endsWith(".mkv") -> SearchItem.Category.VIDEO
            normalizedName.endsWith(".mp3") || normalizedName.endsWith(".wav") || normalizedName.endsWith(".m4a") -> SearchItem.Category.AUDIO
            else -> SearchItem.Category.DOCUMENT
        }
    }

    private fun isItemVisible(path: String): Boolean {
        val lowerPath = path.lowercase()
        if (lowerPath.startsWith("trash") || lowerPath.contains("/trash/")) return false
        if (lowerPath.startsWith("ai/") || lowerPath == "ai" || lowerPath.contains("/ai/")) return false
        if (path.startsWith(".") || path.contains("/.")) return false
        return true
    }
}
