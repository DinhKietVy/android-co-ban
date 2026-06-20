package com.example.filemanagementapp.explorer.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UploadStatus {
    RUNNING, SUCCESS, FAILED
}

data class ExplorerUploadProgress(
    val fileName: String,
    val progressPercent: Int,
    val uploadedBytes: Long,
    val totalBytes: Long?,
    val status: UploadStatus = UploadStatus.RUNNING,
    val errorMessage: String? = null
)

object ExplorerUploadProgressStore {
    private val _progress = MutableStateFlow<ExplorerUploadProgress?>(null)
    val progress: StateFlow<ExplorerUploadProgress?> = _progress.asStateFlow()

    fun update(progress: ExplorerUploadProgress) {
        _progress.value = progress
    }

    fun clear() {
        _progress.value = null
    }
}
