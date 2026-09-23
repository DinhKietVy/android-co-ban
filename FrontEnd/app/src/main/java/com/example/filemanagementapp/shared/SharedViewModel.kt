package com.example.filemanagementapp.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SharedUiState(
    val isLoading: Boolean = false,
    val items: List<ExplorerItem> = emptyList(),
    val error: String? = null
)

class SharedViewModel(
    private val username: String,
    private val repository: ExplorerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SharedUiState())
    val uiState: StateFlow<SharedUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getSharedToMe(username).onSuccess { response ->
                val explorerItems = response.data.map { sharedFile ->
                    // Map to ExplorerItem
                    val fileName = sharedFile.filePath.substringAfterLast("/")
                    ExplorerItem(
                        id = sharedFile.filePath, // Use path as id
                        name = fileName,
                        path = sharedFile.filePath,
                        type = ExplorerItem.Type.FILE,
                        size = null,
                        modified = "",
                        isFavorite = false,
                        aiAnalyzed = false,
                        previewUrl = null, // Will build it when clicking
                        ownerUsername = sharedFile.ownerUsername,
                        permission = sharedFile.permission
                    )
                }
                _uiState.update { it.copy(isLoading = false, items = explorerItems) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, error = throwable.message) }
            }
        }
    }

    fun renameItem(item: ExplorerItem, newName: String) {
        viewModelScope.launch {
            // Need to call API on ownerUsername?
            // Actually, the backend requires `username` which is the owner of the folder,
            // or we need to pass the ownerUsername to the API.
            // Currently backend `renameItem` expects `username` as the owner.
            repository.renameItem(
                username = item.ownerUsername ?: username,
                item = item,
                newName = newName
            ).onSuccess {
                load() // reload
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.message) }
            }
        }
    }

    fun deleteItem(item: ExplorerItem) {
        viewModelScope.launch {
            repository.deleteItem(
                username = item.ownerUsername ?: username,
                item = item
            ).onSuccess {
                load()
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.message) }
            }
        }
    }
}
