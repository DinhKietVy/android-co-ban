package com.example.filemanagementapp.explorer.ui

import com.example.filemanagementapp.data.ai.repository.AiAnalysisRepository
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.local.ai.AiAnalysisCache
import com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository
import com.example.filemanagementapp.data.local.ai.AiAnalysisStatus
import com.example.filemanagementapp.data.local.explorer.DirectoryCacheLocalRepository
import com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.explorer.ExplorerAdapter
import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val username: String,
    private val repository: ExplorerRepository,
    private val aiAnalysisRepository: AiAnalysisRepository,
    private val aiAnalysisLocalRepository: AiAnalysisLocalRepository,
    private val favoriteLocalRepository: FavoriteLocalRepository,
    private val directoryCacheLocalRepository: DirectoryCacheLocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState(isLoading = true))
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ExplorerUiEvent>()
    val events: SharedFlow<ExplorerUiEvent> = _events.asSharedFlow()
    private var currentDirectoryItems: List<ExplorerItem> = emptyList()

    fun loadRootDirectory() {
        loadDirectory("")
    }

    fun refreshCurrentDirectory() {
        loadDirectory(
            folderPath = _uiState.value.currentFolder,
            isRefresh = true
        )
    }

    fun loadDirectory(folderPath: String) {
        loadDirectory(folderPath = folderPath, isRefresh = false)
    }

    private fun loadDirectory(folderPath: String, isRefresh: Boolean) {
        viewModelScope.launch {
            val normalizedFolderPath = folderPath.trim().trim('/')
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }

            val cachedSnapshot = directoryCacheLocalRepository.getCachedDirectory(
                username = username,
                folderPath = normalizedFolderPath
            )
            if (cachedSnapshot != null) {
                val mergedItems = mergeLocalMetadata(cachedSnapshot.directory.items)
                applyDirectoryState(
                    directory = cachedSnapshot.directory,
                    mergedItems = mergedItems,
                    isRefresh = isRefresh,
                    isShowingCachedData = true
                )
            }

            repository.listDirectory(username = username, folderPath = normalizedFolderPath)
                .onSuccess { directory ->
                    directoryCacheLocalRepository.upsertDirectory(
                        username = username,
                        folderPath = normalizedFolderPath,
                        directory = directory
                    )
                    val mergedItems = mergeLocalMetadata(directory.items)
                    applyDirectoryState(
                        directory = directory,
                        mergedItems = mergedItems,
                        isRefresh = isRefresh,
                        isShowingCachedData = false
                    )
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isShowingCachedData = cachedSnapshot != null,
                            errorMessage = throwable.message ?: "Khong the tai danh sach thu muc"
                        )
                    }
                }
        }
    }

    fun renameItem(item: ExplorerItem, newName: String) {
        val sanitizedName = newName.trim()
        if (sanitizedName.isBlank()) {
            emitMessage("Ten moi khong duoc de trong")
            return
        }

        viewModelScope.launch {
            repository.renameItem(username = username, item = item, newName = sanitizedName)
                .onSuccess { message ->
                    directoryCacheLocalRepository.invalidateDirectory(
                        username = username,
                        folderPath = _uiState.value.currentFolder
                    )
                    val newPath = repository.buildChildPath(
                        item.path.substringBeforeLast('/', ""),
                        sanitizedName
                    )
                    aiAnalysisLocalRepository.updatePath(
                        username = username,
                        oldPath = item.path,
                        newPath = newPath,
                        isFolder = item.type == ExplorerItem.Type.FOLDER
                    )
                    favoriteLocalRepository.updatePath(
                        username = username,
                        oldPath = item.path,
                        newPath = newPath,
                        isFolder = item.type == ExplorerItem.Type.FOLDER
                    )
                    emitMessage(message)
                    refreshCurrentDirectory()
                }
                .onFailure { throwable ->
                    emitMessage(throwable.message ?: "Khong the doi ten muc")
                }
        }
    }

    fun createFolder(folderName: String) {
        val sanitizedName = folderName.trim()
        if (sanitizedName.isBlank()) {
            emitMessage("Ten folder khong duoc de trong")
            return
        }

        viewModelScope.launch {
            repository.createFolder(
                username = username,
                targetPath = _uiState.value.currentFolder,
                folderName = sanitizedName
            ).onSuccess { message ->
                directoryCacheLocalRepository.invalidateDirectory(
                    username = username,
                    folderPath = _uiState.value.currentFolder
                )
                emitMessage(message)
                refreshCurrentDirectory()
            }.onFailure { throwable ->
                emitMessage(throwable.message ?: "Khong the tao folder")
            }
        }
    }

    fun uploadFile(fileUri: Uri) {
        viewModelScope.launch {
            emitMessage("Dang upload file...")
            repository.uploadFile(
                username = username,
                targetPath = _uiState.value.currentFolder,
                fileUri = fileUri
            ).onSuccess { message ->
                directoryCacheLocalRepository.invalidateDirectory(
                    username = username,
                    folderPath = _uiState.value.currentFolder
                )
                emitMessage(message)
                refreshCurrentDirectory()
            }.onFailure { throwable ->
                emitMessage(throwable.message ?: "Khong the upload file")
            }
        }
    }

    fun moveItem(item: ExplorerItem, targetFolderPath: String) {
        val normalizedTarget = targetFolderPath.trim().trim('/')
        viewModelScope.launch {
            repository.moveItem(username = username, item = item, targetFolderPath = normalizedTarget)
                .onSuccess { message ->
                    directoryCacheLocalRepository.invalidateDirectory(
                        username = username,
                        folderPath = _uiState.value.currentFolder
                    )
                    directoryCacheLocalRepository.invalidateDirectory(
                        username = username,
                        folderPath = normalizedTarget
                    )
                    val newPath = repository.buildChildPath(normalizedTarget, item.name)
                    aiAnalysisLocalRepository.updatePath(
                        username = username,
                        oldPath = item.path,
                        newPath = newPath,
                        isFolder = item.type == ExplorerItem.Type.FOLDER
                    )
                    favoriteLocalRepository.updatePath(
                        username = username,
                        oldPath = item.path,
                        newPath = newPath,
                        isFolder = item.type == ExplorerItem.Type.FOLDER
                    )
                    emitMessage(message)
                    refreshCurrentDirectory()
                }
                .onFailure { throwable ->
                    emitMessage(throwable.message ?: "Khong the di chuyen muc")
                }
        }
    }

    fun deleteItem(item: ExplorerItem) {
        viewModelScope.launch {
            repository.deleteItem(username = username, item = item)
                .onSuccess { message ->
                    directoryCacheLocalRepository.invalidateDirectory(
                        username = username,
                        folderPath = _uiState.value.currentFolder
                    )
                    aiAnalysisLocalRepository.deletePath(
                        username = username,
                        path = item.path,
                        isFolder = item.type == ExplorerItem.Type.FOLDER
                    )
                    favoriteLocalRepository.deletePath(
                        username = username,
                        path = item.path,
                        isFolder = item.type == ExplorerItem.Type.FOLDER
                    )
                    emitMessage(message)
                    refreshCurrentDirectory()
                }
                .onFailure { throwable ->
                    emitMessage(throwable.message ?: "Khong the xoa muc")
                }
        }
    }

    fun toggleFavorite(item: ExplorerItem) {
        viewModelScope.launch {
            val isFavorite = favoriteLocalRepository.toggleFavorite(username, item)
            emitMessage(
                if (isFavorite) "Da them vao favorites" else "Da bo khoi favorites"
            )
            _uiState.update { state ->
                val updatedItems = state.items.map { current ->
                    if (current.path == item.path) current.copy(isFavorite = isFavorite) else current
                }
                currentDirectoryItems = currentDirectoryItems.map { current ->
                    if (current.path == item.path) current.copy(isFavorite = isFavorite) else current
                }
                state.copy(
                    items = applySorting(updatedItems, state.sortOption)
                )
            }
        }
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.update { state ->
            state.copy(
                sortOption = sortOption,
                items = applySorting(currentDirectoryItems, sortOption)
            )
        }
    }

    fun toggleDisplayMode() {
        _uiState.update { state ->
            state.copy(
                displayMode = if (state.displayMode == ExplorerAdapter.DisplayMode.GRID) {
                    ExplorerAdapter.DisplayMode.LIST
                } else {
                    ExplorerAdapter.DisplayMode.GRID
                }
            )
        }
    }

    fun toggleSelectionMode() {
        _uiState.update { state ->
            if (state.isSelectionMode) {
                state.copy(
                    isSelectionMode = false,
                    selectedPaths = emptySet()
                )
            } else {
                state.copy(isSelectionMode = true)
            }
        }
    }

    fun startSelection(item: ExplorerItem) {
        _uiState.update { state ->
            if (state.isSelectionMode) {
                val updatedSelection = state.selectedPaths.toMutableSet().apply {
                    if (contains(item.path)) {
                        remove(item.path)
                    } else {
                        add(item.path)
                    }
                }
                state.copy(selectedPaths = updatedSelection)
            } else {
                state.copy(
                    isSelectionMode = true,
                    selectedPaths = setOf(item.path)
                )
            }
        }
    }

    fun toggleItemSelection(item: ExplorerItem) {
        _uiState.update { state ->
            val updatedSelection = state.selectedPaths.toMutableSet().apply {
                if (contains(item.path)) {
                    remove(item.path)
                } else {
                    add(item.path)
                }
            }
            state.copy(
                isSelectionMode = true,
                selectedPaths = updatedSelection
            )
        }
    }

    fun moveSelectedItems(targetFolderPath: String) {
        val selectedItems = currentDirectoryItems.filter { it.path in _uiState.value.selectedPaths }
        if (selectedItems.isEmpty()) {
            emitMessage("Chua co item nao duoc chon")
            return
        }

        val normalizedTarget = targetFolderPath.trim().trim('/')
        viewModelScope.launch {
            var movedCount = 0
            selectedItems.forEach { item ->
                repository.moveItem(
                    username = username,
                    item = item,
                    targetFolderPath = normalizedTarget
                ).onSuccess {
                    movedCount++
                    val newPath = repository.buildChildPath(normalizedTarget, item.name)
                    aiAnalysisLocalRepository.updatePath(
                        username = username,
                        oldPath = item.path,
                        newPath = newPath,
                        isFolder = item.type == ExplorerItem.Type.FOLDER
                    )
                    favoriteLocalRepository.updatePath(
                        username = username,
                        oldPath = item.path,
                        newPath = newPath,
                        isFolder = item.type == ExplorerItem.Type.FOLDER
                    )
                }
            }

            directoryCacheLocalRepository.invalidateDirectory(
                username = username,
                folderPath = _uiState.value.currentFolder
            )
            directoryCacheLocalRepository.invalidateDirectory(
                username = username,
                folderPath = normalizedTarget
            )

            _uiState.update { state ->
                state.copy(
                    isSelectionMode = false,
                    selectedPaths = emptySet()
                )
            }
            if (movedCount > 0) {
                emitMessage("Da di chuyen $movedCount item")
            } else {
                emitMessage("Khong the di chuyen cac item da chon")
            }
            refreshCurrentDirectory()
        }
    }

    fun deleteSelectedItems() {
        val selectedItems = currentDirectoryItems.filter { it.path in _uiState.value.selectedPaths }
        if (selectedItems.isEmpty()) {
            emitMessage("Chua co item nao duoc chon")
            return
        }

        viewModelScope.launch {
            var deletedCount = 0
            selectedItems.forEach { item ->
                repository.deleteItem(username = username, item = item)
                    .onSuccess {
                        deletedCount++
                        aiAnalysisLocalRepository.deletePath(
                            username = username,
                            path = item.path,
                            isFolder = item.type == ExplorerItem.Type.FOLDER
                        )
                        favoriteLocalRepository.deletePath(
                            username = username,
                            path = item.path,
                            isFolder = item.type == ExplorerItem.Type.FOLDER
                        )
                    }
            }

            directoryCacheLocalRepository.invalidateDirectory(
                username = username,
                folderPath = _uiState.value.currentFolder
            )

            _uiState.update { state ->
                state.copy(
                    isSelectionMode = false,
                    selectedPaths = emptySet()
                )
            }
            if (deletedCount > 0) {
                emitMessage("Da xoa $deletedCount item")
            } else {
                emitMessage("Khong the xoa cac item da chon")
            }
            refreshCurrentDirectory()
        }
    }

    fun analyzeItem(item: ExplorerItem) {
        if (item.type != ExplorerItem.Type.FILE) {
            emitMessage("Chi ho tro AI analysis cho file")
            return
        }
        val previewUrl = item.previewUrl
        if (previewUrl.isNullOrBlank()) {
            emitMessage("Khong tim thay duong dan file de gui sang AI")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingAi = true) }
            aiAnalysisRepository.analyzeImageFile(
                username = username,
                fileName = item.name,
                downloadUrl = previewUrl
            ).onSuccess { result ->
                aiAnalysisLocalRepository.upsertAnalysis(
                    AiAnalysisCache(
                        username = username,
                        filePath = item.path,
                        status = AiAnalysisStatus.COMPLETED,
                        tags = result.tags,
                        ocrText = result.texts.joinToString(separator = "\n"),
                        previewImagePath = result.previewImagePath,
                        analysisType = "ocr",
                        modelSource = "predict-image"
                    )
                )
                val mergedTags = (item.tags + result.tags)
                    .map { tag -> tag.trim() }
                    .filter { tag -> tag.isNotBlank() }
                    .distinct()
                _uiState.update { state ->
                    val updatedItems = state.items.map { current ->
                        if (current.path == item.path) {
                            current.copy(
                                aiAnalyzed = true,
                                tags = mergedTags
                            )
                        } else {
                            current
                        }
                    }
                    currentDirectoryItems = currentDirectoryItems.map { current ->
                        if (current.path == item.path) {
                            current.copy(
                                aiAnalyzed = true,
                                tags = mergedTags
                            )
                        } else {
                            current
                        }
                    }
                    state.copy(
                        items = applySorting(updatedItems, state.sortOption)
                    )
                }
                _events.emit(
                    ExplorerUiEvent.OpenPreview(
                        fileName = item.name,
                        previewUrl = item.previewUrl,
                        analyzedImagePath = result.previewImagePath,
                        ocrText = result.texts.joinToString(separator = "\n"),
                        aiTags = result.tags,
                        fileSize = item.size,
                        modified = item.modified,
                        showAiPanel = true
                    )
                )
            }.onFailure { throwable ->
                emitMessage(throwable.message ?: "Khong the phan tich file voi AI")
            }.also {
                _uiState.update { state -> state.copy(isAnalyzingAi = false) }
            }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(ExplorerUiEvent.ShowMessage(message))
        }
    }

    private suspend fun mergeLocalMetadata(items: List<ExplorerItem>): List<ExplorerItem> {
        val analysisMap = aiAnalysisLocalRepository.getAnalysisByPaths(
            username = username,
            filePaths = items
                .filter { it.type == ExplorerItem.Type.FILE }
                .map { it.path }
        )
        val favoritePaths = favoriteLocalRepository.getFavoritePaths(
            username = username,
            paths = items.map { it.path }
        )

        return items.map { item ->
            val analysis = analysisMap[item.path]
            if (item.type == ExplorerItem.Type.FILE && analysis != null) {
                val mergedTags = (item.tags + analysis.tags)
                    .map { tag -> tag.trim() }
                    .filter { tag -> tag.isNotBlank() }
                    .distinct()
                item.copy(
                    aiAnalyzed = analysis.status == AiAnalysisStatus.COMPLETED,
                    tags = mergedTags,
                    isFavorite = item.path in favoritePaths
                )
            } else {
                item.copy(isFavorite = item.path in favoritePaths)
            }
        }
    }

    private fun applyDirectoryState(
        directory: com.example.filemanagementapp.data.explorer.repository.ExplorerDirectoryData,
        mergedItems: List<ExplorerItem>,
        isRefresh: Boolean,
        isShowingCachedData: Boolean
    ) {
        _uiState.update { state ->
            currentDirectoryItems = mergedItems
            val availablePaths = mergedItems.mapTo(linkedSetOf()) { item -> item.path }
            val updatedSelection = state.selectedPaths.filterTo(linkedSetOf()) { path ->
                path in availablePaths
            }
            state.copy(
                isLoading = false,
                isRefreshing = false,
                isShowingCachedData = isShowingCachedData,
                currentFolder = directory.currentFolder,
                storageSummary = directory.storageSummary,
                breadcrumbs = directory.breadcrumbs,
                items = applySorting(mergedItems, state.sortOption),
                selectedPaths = updatedSelection,
                errorMessage = null
            )
        }

        if (isRefresh && isShowingCachedData) {
            _uiState.update { state ->
                state.copy(isRefreshing = true)
            }
        }
    }

    private fun applySorting(
        items: List<ExplorerItem>,
        sortOption: SortOption
    ): List<ExplorerItem> {
        val folders = items.filter { it.type == ExplorerItem.Type.FOLDER }
        val files = items.filter { it.type == ExplorerItem.Type.FILE }
        return sortItems(folders, sortOption) + sortItems(files, sortOption)
    }

    private fun sortItems(
        items: List<ExplorerItem>,
        sortOption: SortOption
    ): List<ExplorerItem> {
        return when (sortOption) {
            SortOption.NAME -> items.sortedBy { it.name.lowercase() }
            SortOption.DATE_MODIFIED -> items.sortedByDescending { it.modifiedEpochMillis ?: Long.MIN_VALUE }
            SortOption.SIZE -> items.sortedWith(
                compareByDescending<ExplorerItem> { it.sizeBytes ?: Long.MIN_VALUE }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    class Factory(
        private val username: String,
        private val repository: ExplorerRepository,
        private val aiAnalysisRepository: AiAnalysisRepository,
        private val aiAnalysisLocalRepository: AiAnalysisLocalRepository,
        private val favoriteLocalRepository: FavoriteLocalRepository,
        private val directoryCacheLocalRepository: DirectoryCacheLocalRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ExplorerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ExplorerViewModel(
                    username = username,
                    repository = repository,
                    aiAnalysisRepository = aiAnalysisRepository,
                    aiAnalysisLocalRepository = aiAnalysisLocalRepository,
                    favoriteLocalRepository = favoriteLocalRepository,
                    directoryCacheLocalRepository = directoryCacheLocalRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
