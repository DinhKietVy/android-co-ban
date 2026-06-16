package com.example.filemanagementapp.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrashViewModel(
    private val username: String,
    private val repository: ExplorerRepository
) : ViewModel() {

    private var rawExplorerItems: List<ExplorerItem> = emptyList()

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    fun loadTrash() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Ensure trash exists
            repository.createFolder(username = username, targetPath = "", folderName = "trash")
            
            repository.listDirectory(username, "trash")
                .onSuccess { directory ->
                    rawExplorerItems = directory.items
                    val mappedItems = directory.items.map { it.toTrashItemModel() }
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
            itemsToRestore.forEach { item ->
                // Move back to root directory ("")
                repository.moveItem(username, item, "")
                    .onSuccess { successCount++ }
            }
            _uiState.update { it.copy(isActionLoading = false) }
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

    private fun ExplorerItem.toTrashItemModel(): TrashItemModel {
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
        
        return TrashItemModel(
            id = id,
            name = name,
            type = trashType,
            deletedDate = modified,
            daysUntilRemoval = daysLeft,
            size = size,
            itemCount = itemCount,
            aiAnalyzed = false,
            aiTags = emptyList(),
            ocrPreview = null
        )
    }

    class Factory(
        private val username: String,
        private val repository: ExplorerRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrashViewModel::class.java)) {
                return TrashViewModel(username, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
