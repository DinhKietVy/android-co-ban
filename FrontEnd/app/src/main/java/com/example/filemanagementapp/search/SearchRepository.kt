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
        val allItems = cachedDirectories.flatMap { it.items }.associateBy { it.path }.values.toList()

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
            
            // Add "AI analyzed" tag if it has completed AI analysis
            val finalTags = if (analysis?.status == AiAnalysisStatus.COMPLETED) {
                aiTags + "AI analyzed"
            } else {
                aiTags
            }

            SearchItem(
                id = item.id,
                name = item.name,
                path = item.path,
                type = if (isFolder) "Folder" else resolveFileTypeString(item.name),
                size = item.size,
                date = item.modified,
                modifiedEpochMillis = item.modifiedEpochMillis,
                category = if (isFolder) SearchItem.Category.FOLDER else resolveCategory(item.name),
                ocrText = analysis?.ocrText?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim(),
                tags = finalTags.distinct(),
                isFavorite = isFavorite,
                rawItem = item
            )
        }
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
}
