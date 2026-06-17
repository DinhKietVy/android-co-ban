package com.example.filemanagementapp.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository
import com.example.filemanagementapp.data.local.search.SearchHistoryPreferencesRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val username: String,
    private val searchRepository: SearchRepository,
    private val searchHistoryRepo: SearchHistoryPreferencesRepository,
    private val favoriteLocalRepository: FavoriteLocalRepository
) : ViewModel() {

    enum class Sort { NAME, DATE, SIZE }

    data class UiState(
        val isLoading: Boolean = true,
        val allItems: List<SearchItem> = emptyList(),
        val filteredItems: List<SearchItem> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        val query: String = "",
        val selectedCategory: String = "all",
        val currentSort: Sort = Sort.NAME
    )

    private val _isLoading = MutableStateFlow(true)
    private val _allItems = MutableStateFlow<List<SearchItem>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow("all")
    private val _currentSort = MutableStateFlow(Sort.NAME)

    private data class FilterCriteria(val query: String, val category: String, val sort: Sort)
    private val filterCriteria = combine(_query, _selectedCategory, _currentSort) { q, c, s ->
        FilterCriteria(q, c, s)
    }

    val uiState: StateFlow<UiState> = combine(
        _isLoading,
        _allItems,
        filterCriteria,
        searchHistoryRepo.recentSearches
    ) { loading, allItems, criteria, recentSearches ->
        val filtered = filterAndSort(allItems, criteria.query, criteria.category, criteria.sort)
        UiState(
            isLoading = loading,
            allItems = allItems,
            filteredItems = filtered,
            recentSearches = recentSearches,
            query = criteria.query,
            selectedCategory = criteria.category,
            currentSort = criteria.sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    init {
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val items = searchRepository.loadAllSearchItems(username)
            _allItems.value = items
            _isLoading.value = false
        }
    }

    fun updateQuery(query: String) {
        _query.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectSort(sort: Sort) {
        _currentSort.value = sort
    }

    fun addRecentSearch(query: String) {
        searchHistoryRepo.addSearchQuery(query)
    }

    fun toggleFavorite(item: SearchItem) {
        viewModelScope.launch {
            favoriteLocalRepository.toggleFavorite(username, item.rawItem)
            loadFiles() // Refresh list
        }
    }

    private fun filterAndSort(
        items: List<SearchItem>,
        query: String,
        category: String,
        sort: Sort
    ): List<SearchItem> {
        if (query.isBlank()) {
            return emptyList()
        }

        val filtered = items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                (item.ocrText?.contains(query, ignoreCase = true) == true) ||
                item.tags.any { it.contains(query, ignoreCase = true) }

            val matchesCategory = when (category) {
                "all" -> true
                "images" -> item.category == SearchItem.Category.IMAGE
                "documents" -> item.category == SearchItem.Category.DOCUMENT
                "pdf" -> item.category == SearchItem.Category.PDF
                "videos" -> item.category == SearchItem.Category.VIDEO
                "ocr" -> !item.ocrText.isNullOrBlank()
                "ai-objects" -> item.tags.any { !it.equals("AI analyzed", ignoreCase = true) }
                "favorites" -> item.isFavorite
                else -> true
            }
            matchesQuery && matchesCategory
        }

        val folders = filtered.filter { it.rawItem.type == com.example.filemanagementapp.explorer.ExplorerItem.Type.FOLDER }
        val files = filtered.filter { it.rawItem.type == com.example.filemanagementapp.explorer.ExplorerItem.Type.FILE }
        
        fun sortItems(list: List<SearchItem>): List<SearchItem> {
            return when (sort) {
                Sort.NAME -> list.sortedBy { it.name.lowercase() }
                Sort.DATE -> list.sortedByDescending { it.rawItem.modifiedEpochMillis ?: 0L }
                Sort.SIZE -> list.sortedByDescending { it.rawItem.sizeBytes ?: 0L }
            }
        }
        
        return sortItems(folders) + sortItems(files)
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val username: String,
        private val searchRepository: SearchRepository,
        private val searchHistoryRepo: SearchHistoryPreferencesRepository,
        private val favoriteLocalRepository: FavoriteLocalRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(
                username,
                searchRepository,
                searchHistoryRepo,
                favoriteLocalRepository
            ) as T
        }
    }
}
