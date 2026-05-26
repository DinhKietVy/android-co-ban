package com.example.filemanagementapp.recent

data class RecentItem(
    val id: String,
    val name: String,
    val kind: Kind,
    val section: Section,
    val lastModified: String,
    val fileType: FileType? = null,
    val size: String? = null,
    val itemCount: Int? = null,
    val aiTags: List<String> = emptyList(),
    val aiAnalyzed: Boolean = false,
    val ocrSnippet: String? = null,
    val isFavorite: Boolean = false
) {
    enum class Kind { FILE, FOLDER }
    enum class FileType { IMAGE, DOCUMENT, VIDEO, PDF, AUDIO }
    enum class Section { TODAY, YESTERDAY, WEEK, EARLIER, FAV_RECENT, FAV_AI, FAV_FREQUENT }
}

sealed class RecentListItem {
    data class Header(val title: String, val count: Int, val iconRes: Int) : RecentListItem()
    data class Entry(val item: RecentItem) : RecentListItem()
}
