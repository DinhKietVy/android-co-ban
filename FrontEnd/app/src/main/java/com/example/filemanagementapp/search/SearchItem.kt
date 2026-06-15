package com.example.filemanagementapp.search

import com.example.filemanagementapp.explorer.ExplorerItem

data class SearchItem(
    val id: String,
    val name: String,
    val path: String,
    val type: String,
    val size: String?,
    val date: String,
    val modifiedEpochMillis: Long?,
    val category: Category,
    val ocrText: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val rawItem: ExplorerItem
) {
    enum class Category {
        PDF,
        IMAGE,
        DOCUMENT,
        FOLDER,
        VIDEO,
        AUDIO,
        OTHER
    }
}
