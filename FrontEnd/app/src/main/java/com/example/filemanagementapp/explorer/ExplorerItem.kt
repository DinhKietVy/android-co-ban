package com.example.filemanagementapp.explorer

data class ExplorerItem(
    val id: String,
    val name: String,
    val type: Type,
    val modified: String,
    val itemCount: Int? = null,
    val size: String? = null,
    val aiTags: List<String> = emptyList(),
    val aiAnalyzed: Boolean = false
) {
    enum class Type {
        FOLDER,
        FILE
    }
}
