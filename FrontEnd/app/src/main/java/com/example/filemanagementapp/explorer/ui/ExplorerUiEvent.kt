package com.example.filemanagementapp.explorer.ui

sealed interface ExplorerUiEvent {
    data class ShowMessage(val message: String) : ExplorerUiEvent
    data class OpenPreview(
        val fileName: String,
        val previewUrl: String?,
        val analyzedImagePath: String?,
        val ocrText: String?,
        val aiTags: List<String>,
        val fileSize: String?,
        val modified: String,
        val showAiPanel: Boolean
    ) : ExplorerUiEvent
}
