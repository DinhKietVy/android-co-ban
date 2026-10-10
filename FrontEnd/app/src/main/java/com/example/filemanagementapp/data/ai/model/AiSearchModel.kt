package com.example.filemanagementapp.data.ai.model

data class SearchRequest(
    val username: String,
    val query: String
)

data class AiSearchResponse(
    val success: Boolean,
    val data: AiSearchData?
)

data class AiSearchData(
    val query: String,
    val totalMatched: Int,
    val files: List<AiSearchFileItem> = emptyList()
)

data class AiSearchFileItem(
    val filePath: String,
    val name: String,
    val size: Long? = null,
    val createdAt: String? = null,
    val tags: List<String> = emptyList()
)
