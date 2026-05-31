package com.example.filemanagementapp.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExplorerDownloadProgress(
    val fileName: String,
    val progressPercent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?
)

object ExplorerDownloadProgressStore {
    private val _progress = MutableStateFlow<ExplorerDownloadProgress?>(null)
    val progress: StateFlow<ExplorerDownloadProgress?> = _progress.asStateFlow()

    fun update(progress: ExplorerDownloadProgress) {
        _progress.value = progress
    }

    fun clear() {
        _progress.value = null
    }
}
