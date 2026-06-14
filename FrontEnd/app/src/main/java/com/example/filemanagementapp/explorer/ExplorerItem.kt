package com.example.filemanagementapp.explorer

import androidx.annotation.DrawableRes

data class ExplorerItem(
    val id: String,
    val name: String,
    val path: String,
    val type: Type,
    val modified: String,
    val modifiedEpochMillis: Long? = null,
    val itemCount: Int? = null,
    val size: String? = null,
    val sizeBytes: Long? = null,
    val previewUrl: String? = null,
    val isImagePreviewable: Boolean = false,
    @param:DrawableRes val fallbackIconRes: Int? = null,
    val tags: List<String> = emptyList(),
    val aiAnalyzed: Boolean = false,
    val ocrSnippet: String? = null,
    val isFavorite: Boolean = false
) {
    enum class Type {
        FOLDER,
        FILE
    }
}
