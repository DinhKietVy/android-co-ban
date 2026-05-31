package com.example.filemanagementapp.explorer.ui

import com.example.filemanagementapp.explorer.ExplorerBreadcrumbItem
import com.example.filemanagementapp.explorer.ExplorerAdapter
import com.example.filemanagementapp.explorer.ExplorerItem

data class ExplorerUiState(
    val isLoading: Boolean = false,
    val isAnalyzingAi: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSelectionMode: Boolean = false,
    val displayMode: ExplorerAdapter.DisplayMode = ExplorerAdapter.DisplayMode.GRID,
    val sortOption: SortOption = SortOption.NAME,
    val isShowingCachedData: Boolean = false,
    val currentFolder: String = "",
    val storageSummary: String = "",
    val breadcrumbs: List<ExplorerBreadcrumbItem> = emptyList(),
    val items: List<ExplorerItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val errorMessage: String? = null
)

enum class SortOption {
    NAME,
    DATE_MODIFIED,
    SIZE
}
