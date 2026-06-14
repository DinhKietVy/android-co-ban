package com.example.filemanagementapp.recent

data class RecentUiState(
    val isLoading: Boolean = false,
    val activeTab: RecentTab = RecentTab.RECENT,
    val query: String = "",
    val quickAccess: List<RecentItem> = emptyList(),
    val listItems: List<RecentListItem> = emptyList()
)

enum class RecentTab { RECENT, FAVORITES }
