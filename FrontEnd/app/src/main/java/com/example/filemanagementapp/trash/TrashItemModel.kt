package com.example.filemanagementapp.trash

data class TrashItemModel(
    val id: String,
    val name: String,
    val type: Type,
    val deletedDate: String,
    val daysUntilRemoval: Int,
    val size: String? = null,
    val itemCount: Int? = null,
    val aiAnalyzed: Boolean = false,
    val aiTags: List<String> = emptyList(),
    val ocrPreview: String? = null,
    val previewUrl: String? = null
) {
    enum class Type {
        FOLDER,
        IMAGE,
        DOCUMENT,
        SPREADSHEET,
        VIDEO,
        FILE
    }
}
