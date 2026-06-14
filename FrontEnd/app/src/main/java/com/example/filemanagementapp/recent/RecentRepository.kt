package com.example.filemanagementapp.recent

import com.example.filemanagementapp.data.local.ai.AiAnalysisCache
import com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository
import com.example.filemanagementapp.data.local.ai.AiAnalysisStatus
import com.example.filemanagementapp.data.local.explorer.DirectoryCacheLocalRepository
import com.example.filemanagementapp.data.local.favorite.FavoriteEntry
import com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class RecentRepository(
    private val directoryCacheLocalRepository: DirectoryCacheLocalRepository,
    private val favoriteLocalRepository: FavoriteLocalRepository,
    private val aiAnalysisLocalRepository: AiAnalysisLocalRepository
) {
    suspend fun loadRecent(username: String): List<RecentItem> = withContext(Dispatchers.IO) {
        val items = collectCachedItems(username)
        val analysisMap = loadAnalysisMap(username, items)
        val favoritePaths = loadFavoritePaths(username, items)
        val today = LocalDate.now()

        items.map { item ->
            buildRecentItem(
                item = item,
                section = resolveRecentSection(item.modifiedEpochMillis, today),
                analysis = analysisMap[item.path],
                isFavorite = item.path in favoritePaths
            )
        }
    }

    suspend fun loadFavorites(username: String): List<RecentItem> = withContext(Dispatchers.IO) {
        val favorites = favoriteLocalRepository.getAllFavorites(username)
        if (favorites.isEmpty()) {
            return@withContext emptyList()
        }

        val cachedByPath = collectCachedItems(username).associateBy { it.path }
        val analysisMap = aiAnalysisLocalRepository.getAnalysisByPaths(
            username = username,
            filePaths = favorites
                .filter { it.type == ExplorerItem.Type.FILE.name }
                .map { it.path }
        )

        favorites.map { entry ->
            val cachedItem = cachedByPath[entry.path]
            val analysis = analysisMap[entry.path]
            val isAiAnalyzed = analysis?.status == AiAnalysisStatus.COMPLETED
            val section = when {
                isAiAnalyzed -> RecentItem.Section.FAV_AI
                isRecentlyStarred(entry.createdAt) -> RecentItem.Section.FAV_RECENT
                else -> RecentItem.Section.FAV_FREQUENT
            }
            if (cachedItem != null) {
                buildRecentItem(
                    item = cachedItem,
                    section = section,
                    analysis = analysis,
                    isFavorite = true
                )
            } else {
                buildFallbackFavorite(entry, section)
            }
        }
    }

    private suspend fun collectCachedItems(username: String): List<ExplorerItem> {
        return directoryCacheLocalRepository.getAllCachedDirectories(username)
            .flatMap { it.items }
            .associateBy { it.path }
            .values
            .toList()
    }

    private suspend fun loadAnalysisMap(
        username: String,
        items: List<ExplorerItem>
    ): Map<String, AiAnalysisCache> {
        return aiAnalysisLocalRepository.getAnalysisByPaths(
            username = username,
            filePaths = items
                .filter { it.type == ExplorerItem.Type.FILE }
                .map { it.path }
        )
    }

    private suspend fun loadFavoritePaths(
        username: String,
        items: List<ExplorerItem>
    ): Set<String> {
        return favoriteLocalRepository.getFavoritePaths(
            username = username,
            paths = items.map { it.path }
        )
    }

    private fun buildRecentItem(
        item: ExplorerItem,
        section: RecentItem.Section,
        analysis: AiAnalysisCache?,
        isFavorite: Boolean
    ): RecentItem {
        val isFolder = item.type == ExplorerItem.Type.FOLDER
        val aiTags = analysis?.tags
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        return RecentItem(
            id = item.id,
            name = item.name,
            path = item.path,
            kind = if (isFolder) RecentItem.Kind.FOLDER else RecentItem.Kind.FILE,
            section = section,
            lastModified = item.modified,
            modifiedEpochMillis = item.modifiedEpochMillis,
            fileType = if (isFolder) null else resolveFileType(item.name),
            size = item.size,
            itemCount = item.itemCount,
            previewUrl = item.previewUrl,
            isImagePreviewable = item.isImagePreviewable,
            aiTags = aiTags,
            aiAnalyzed = analysis?.status == AiAnalysisStatus.COMPLETED,
            ocrSnippet = analysis?.ocrText
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim(),
            isFavorite = isFavorite
        )
    }

    private fun buildFallbackFavorite(
        entry: FavoriteEntry,
        section: RecentItem.Section
    ): RecentItem {
        val isFolder = entry.type == ExplorerItem.Type.FOLDER.name
        val name = entry.path.substringAfterLast('/').ifBlank { entry.path }
        return RecentItem(
            id = "favorite:${entry.path}",
            name = name,
            path = entry.path,
            kind = if (isFolder) RecentItem.Kind.FOLDER else RecentItem.Kind.FILE,
            section = section,
            lastModified = "",
            modifiedEpochMillis = null,
            fileType = if (isFolder) null else resolveFileType(name),
            isFavorite = true
        )
    }

    private fun resolveRecentSection(
        modifiedEpochMillis: Long?,
        today: LocalDate
    ): RecentItem.Section {
        if (modifiedEpochMillis == null) {
            return RecentItem.Section.EARLIER
        }
        val date = Instant.ofEpochMilli(modifiedEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(date, today)
        return when {
            daysAgo <= 0L -> RecentItem.Section.TODAY
            daysAgo == 1L -> RecentItem.Section.YESTERDAY
            daysAgo <= 7L -> RecentItem.Section.WEEK
            else -> RecentItem.Section.EARLIER
        }
    }

    private fun isRecentlyStarred(createdAt: Long): Boolean {
        val daysAgo = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate(),
            LocalDate.now()
        )
        return daysAgo <= RECENT_FAVORITE_DAYS
    }

    private fun resolveFileType(fileName: String): RecentItem.FileType {
        val normalizedName = fileName.lowercase()
        return when {
            normalizedName.endsWith(".jpg") ||
                normalizedName.endsWith(".jpeg") ||
                normalizedName.endsWith(".png") ||
                normalizedName.endsWith(".webp") ||
                normalizedName.endsWith(".bmp") ||
                normalizedName.endsWith(".gif") -> RecentItem.FileType.IMAGE
            normalizedName.endsWith(".mp4") ||
                normalizedName.endsWith(".mov") ||
                normalizedName.endsWith(".avi") ||
                normalizedName.endsWith(".mkv") ||
                normalizedName.endsWith(".webm") -> RecentItem.FileType.VIDEO
            normalizedName.endsWith(".mp3") ||
                normalizedName.endsWith(".wav") ||
                normalizedName.endsWith(".aac") ||
                normalizedName.endsWith(".m4a") ||
                normalizedName.endsWith(".flac") -> RecentItem.FileType.AUDIO
            normalizedName.endsWith(".pdf") -> RecentItem.FileType.PDF
            else -> RecentItem.FileType.DOCUMENT
        }
    }

    private companion object {
        private const val RECENT_FAVORITE_DAYS = 14L
    }
}
