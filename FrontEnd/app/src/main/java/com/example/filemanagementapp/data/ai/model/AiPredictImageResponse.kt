package com.example.filemanagementapp.data.ai.model

data class DlpFindingDto(
    val type: String = "",
    val start: Int = 0,
    val end: Int = 0
)

data class DlpScanResponse(
    val sensitive: Boolean = false,
    val findings: List<DlpFindingDto> = emptyList(),
    val redactedText: String = "",
    val error: String? = null
)

data class AiPredictImageResponse(
    val texts: List<String> = emptyList(),
    val tags: List<String>? = emptyList(),
    val image_base64: String? = null,
    val dlp: DlpScanResponse? = null,
    val error: String? = null
)
