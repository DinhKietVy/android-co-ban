package com.example.filemanagementapp.search

data class SearchItem(
    val id: Int,
    val name: String,
    val type: String,
    val size: String,
    val date: String,
    val category: Category,
    val ocrText: String? = null,
    val tags: List<String> = emptyList()
) {
    enum class Category {
        PDF,
        IMAGE,
        DOCUMENT
    }
}
