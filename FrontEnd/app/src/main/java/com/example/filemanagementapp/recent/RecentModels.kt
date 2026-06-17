package com.example.filemanagementapp.recent

import com.example.filemanagementapp.explorer.ExplorerItem

data class RecentItem(
    val id: String,
    val name: String,
    val path: String,
    val kind: Kind,
    val section: Section,
    val lastModified: String,
    val modifiedEpochMillis: Long? = null,
    val fileType: FileType? = null,
    val size: String? = null,
    val itemCount: Int? = null,
    val previewUrl: String? = null,
    val isImagePreviewable: Boolean = false,
    val aiTags: List<String> = emptyList(),
    val aiAnalyzed: Boolean = false,
    val ocrSnippet: String? = null,
    val analyzedImagePath: String? = null,
    val isFavorite: Boolean = false
) {
    enum class Kind { FILE, FOLDER }
    enum class FileType { IMAGE, DOCUMENT, VIDEO, PDF, AUDIO }
    enum class Section { TODAY, YESTERDAY, WEEK, EARLIER, FAV_RECENT, FAV_AI, FAV_FREQUENT }

    fun toExplorerItem(): ExplorerItem {
        return ExplorerItem(
            id = id,
            name = name,
            path = path,
            type = if (kind == Kind.FOLDER) ExplorerItem.Type.FOLDER else ExplorerItem.Type.FILE,
            modified = lastModified,
            modifiedEpochMillis = modifiedEpochMillis,
            itemCount = itemCount,
            size = size,
            previewUrl = previewUrl,
            isImagePreviewable = isImagePreviewable,
            tags = aiTags,
            aiAnalyzed = aiAnalyzed,
            ocrSnippet = ocrSnippet,
            analyzedImagePath = analyzedImagePath,
            isFavorite = isFavorite
        )
    }
}

sealed class RecentListItem {
    data class Header(val section: RecentItem.Section, val count: Int) : RecentListItem()
    data class Entry(val item: RecentItem) : RecentListItem()
}
