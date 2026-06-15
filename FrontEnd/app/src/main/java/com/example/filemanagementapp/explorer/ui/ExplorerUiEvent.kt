package com.example.filemanagementapp.explorer.ui

import com.example.filemanagementapp.util.UiText

sealed interface ExplorerUiEvent {
    data class ShowMessage(val message: UiText) : ExplorerUiEvent
    data class OpenPreview(
        val item: com.example.filemanagementapp.explorer.ExplorerItem,
        val username: String,
        val analyzedImagePath: String?,
        val ocrText: String?,
        val aiTags: List<String>,
        val showAiPanel: Boolean
    ) : ExplorerUiEvent
}
