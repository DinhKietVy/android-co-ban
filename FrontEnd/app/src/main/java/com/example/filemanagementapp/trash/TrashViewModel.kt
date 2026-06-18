package com.example.filemanagementapp.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrashViewModel(
    private val username: String,
    private val repository: ExplorerRepository,
    private val aiAnalysisLocalRepository: com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository
) : ViewModel() {

    private var rawExplorerItems: List<ExplorerItem> = emptyList()

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()
    
    private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun loadTrash() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = rawExplorerItems.isEmpty(), errorMessage = null) }
            // Ensure trash exists
            repository.createFolder(username = username, targetPath = "", folderName = "trash")
            
            repository.listDirectory(username, "trash")
                .onSuccess { directory ->
                    val items = directory.items
                    rawExplorerItems = items
                    
                    val analysisMap = aiAnalysisLocalRepository.getAnalysisByPaths(
                        username = username,
                        filePaths = items.filter { it.type == ExplorerItem.Type.FILE }.map { it.path }
                    )
                    
                    val mappedItems = items.map { it.toTrashItemModel(analysisMap[it.path]) }
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            items = mappedItems
                        ) 
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorMessage = error.message ?: "Failed to load trash"
                        ) 
                    }
                }
        }
    }

    fun restoreItems(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        val itemsToRestore = rawExplorerItems.filter { it.id in itemIds }
        if (itemsToRestore.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            var successCount = 0
            var errorCount = 0
            itemsToRestore.forEach { item ->
                // Restore back to original path via backend sidecar metadata
                repository.moveItem(username, item, "RESTORE")
                    .onSuccess { successCount++ }
                    .onFailure { errorCount++ }
            }
            _uiState.update { it.copy(isActionLoading = false) }
            
            if (successCount > 0) {
                _uiEvent.emit("Đã khôi phục thành công $successCount mục")
            }
            if (errorCount > 0) {
                _uiEvent.emit("Lỗi: Không thể khôi phục $errorCount mục")
            }
            
            loadTrash()
        }
    }

    fun deleteItemsForever(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        val itemsToDelete = rawExplorerItems.filter { it.id in itemIds }
        if (itemsToDelete.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            var successCount = 0
            itemsToDelete.forEach { item ->
                repository.deleteItem(username, item)
                    .onSuccess { successCount++ }
            }
            _uiState.update { it.copy(isActionLoading = false) }
            loadTrash()
        }
    }

    fun emptyTrash() {
        deleteItemsForever(rawExplorerItems.map { it.id }.toSet())
    }

    private fun formatSize(sizeInBytes: Long): String {
        if (sizeInBytes < 1024) return "$sizeInBytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = sizeInBytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        val formatted = if (value >= 10 || value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
        return "$formatted ${units[unitIndex]}"
    }

    private fun ExplorerItem.toTrashItemModel(analysis: com.example.filemanagementapp.data.local.ai.AiAnalysisCache?): TrashItemModel {
        val millis = modifiedEpochMillis ?: 0L
        val daysPassed = (System.currentTimeMillis() - millis) / (1000 * 60 * 60 * 24)
        val daysLeft = maxOf(0, 30 - daysPassed).toInt()
        
        val trashType = when(type) {
            ExplorerItem.Type.FOLDER -> TrashItemModel.Type.FOLDER
            ExplorerItem.Type.FILE -> {
                val ext = name.substringAfterLast('.', "").lowercase()
                when(ext) {
                    "jpg", "jpeg", "png", "gif", "webp" -> TrashItemModel.Type.IMAGE
                    "mp4", "avi", "mov", "mkv" -> TrashItemModel.Type.VIDEO
                    "pdf", "doc", "docx", "txt" -> TrashItemModel.Type.DOCUMENT
                    "xls", "xlsx", "csv" -> TrashItemModel.Type.SPREADSHEET
                    else -> TrashItemModel.Type.FILE
                }
            }
        }

        var finalSizeBytes = sizeBytes ?: 0L
        var finalSizeStr = size
        if (analysis?.previewImagePath != null) {
            val file = java.io.File(analysis.previewImagePath)
            if (file.exists()) {
                finalSizeBytes += file.length()
                finalSizeStr = formatSize(finalSizeBytes)
            }
        }
        
        return TrashItemModel(
            id = id,
            name = name,
            type = trashType,
            deletedDate = modified,
            daysUntilRemoval = daysLeft,
            size = finalSizeStr,
            itemCount = itemCount,
            aiAnalyzed = false,
            aiTags = emptyList(),
            ocrPreview = null,
            previewUrl = previewUrl
        )
    }

    class Factory(
        private val username: String,
        private val repository: ExplorerRepository,
        private val aiAnalysisLocalRepository: com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrashViewModel::class.java)) {
                return TrashViewModel(username, repository, aiAnalysisLocalRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
