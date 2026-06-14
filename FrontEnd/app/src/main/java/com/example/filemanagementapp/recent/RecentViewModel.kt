package com.example.filemanagementapp.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecentViewModel(
    private val username: String,
    private val repository: RecentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentUiState(isLoading = true))
    val uiState: StateFlow<RecentUiState> = _uiState.asStateFlow()

    private var recentItems: List<RecentItem> = emptyList()
    private var favoriteItems: List<RecentItem> = emptyList()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            recentItems = repository.loadRecent(username)
            favoriteItems = repository.loadFavorites(username)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    quickAccess = buildQuickAccess(),
                    listItems = buildListItems(state.activeTab, state.query)
                )
            }
        }
    }

    fun setTab(tab: RecentTab) {
        _uiState.update { state ->
            state.copy(
                activeTab = tab,
                listItems = buildListItems(tab, state.query)
            )
        }
    }

    fun setQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                query = query,
                listItems = buildListItems(state.activeTab, query)
            )
        }
    }

    private fun buildQuickAccess(): List<RecentItem> {
        return recentItems
            .filter { it.kind == RecentItem.Kind.FILE || it.kind == RecentItem.Kind.FOLDER }
            .sortedByDescending { it.modifiedEpochMillis ?: Long.MIN_VALUE }
            .take(4)
    }

    private fun buildListItems(tab: RecentTab, query: String): List<RecentListItem> {
        val source = if (tab == RecentTab.RECENT) recentItems else favoriteItems
        val filtered = source.filter { matches(it, query) }
        val sectionOrder = if (tab == RecentTab.RECENT) RECENT_SECTIONS else FAVORITE_SECTIONS
        return buildList {
            sectionOrder.forEach { section ->
                val sectionItems = filtered.filter { it.section == section }
                if (sectionItems.isNotEmpty()) {
                    add(RecentListItem.Header(section, sectionItems.size))
                    sectionItems.forEach { add(RecentListItem.Entry(it)) }
                }
            }
        }
    }

    private fun matches(item: RecentItem, query: String): Boolean {
        if (query.isBlank()) return true
        val normalized = query.trim().lowercase()
        return item.name.lowercase().contains(normalized) ||
            item.aiTags.any { it.lowercase().contains(normalized) } ||
            (item.ocrSnippet?.lowercase()?.contains(normalized) == true)
    }

    class Factory(
        private val username: String,
        private val repository: RecentRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RecentViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RecentViewModel(username, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        private val RECENT_SECTIONS = listOf(
            RecentItem.Section.TODAY,
            RecentItem.Section.YESTERDAY,
            RecentItem.Section.WEEK,
            RecentItem.Section.EARLIER
        )
        private val FAVORITE_SECTIONS = listOf(
            RecentItem.Section.FAV_RECENT,
            RecentItem.Section.FAV_AI,
            RecentItem.Section.FAV_FREQUENT
        )
    }
}
