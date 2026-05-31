package com.example.filemanagementapp.data.ai.model

data class AiPredictImageResponse(
    val texts: List<String> = emptyList(),
    val image_base64: String? = null,
    val error: String? = null
)
