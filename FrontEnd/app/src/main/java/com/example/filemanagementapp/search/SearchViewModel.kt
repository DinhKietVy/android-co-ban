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

import com.example.filemanagementapp.data.ai.repository.AiAnalysisRepository

class SearchViewModel(
    private val username: String,
    private val searchRepository: SearchRepository,
    private val searchHistoryRepo: SearchHistoryPreferencesRepository,
    private val favoriteLocalRepository: FavoriteLocalRepository,
    private val aiAnalysisRepository: AiAnalysisRepository
) : ViewModel() {

    enum class Sort { NAME, DATE, SIZE }

    data class UiState(
        val isLoading: Boolean = true,
        val isAiLoading: Boolean = false,
        val allItems: List<SearchItem> = emptyList(),
        val filteredItems: List<SearchItem> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        val query: String = "",
        val selectedCategory: String = "all",
        val currentSort: Sort = Sort.NAME
    )

    private val _isLoading = MutableStateFlow(true)
    private val _isAiLoading = MutableStateFlow(false)
    private val _aiSearchResults = MutableStateFlow<List<String>?>(null)
    private val _allItems = MutableStateFlow<List<SearchItem>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow("all")
    private val _currentSort = MutableStateFlow(Sort.NAME)

    private data class FilterCriteria(val query: String, val category: String, val sort: Sort, val aiResult: List<String>?)
    private val filterCriteria = combine(_query, _selectedCategory, _currentSort, _aiSearchResults) { q, c, s, ai ->
        FilterCriteria(q, c, s, ai)
    }

    val uiState: StateFlow<UiState> = combine(
        _isLoading,
        _isAiLoading,
        _allItems,
        filterCriteria,
        searchHistoryRepo.recentSearches
    ) { loading, aiLoading, allItems, criteria, recentSearches ->
        val filtered = filterAndSort(allItems, criteria.query, criteria.category, criteria.sort, criteria.aiResult)
        UiState(
            isLoading = loading,
            isAiLoading = aiLoading,
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

    fun performAiSearch(query: String) {
        if (query.isBlank()) {
            _aiSearchResults.value = null
            return
        }
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiSearchResults.value = null
            
            val result = aiAnalysisRepository.semanticSearch(username, query)
            if (result.isSuccess) {
                val files = result.getOrNull()?.data?.files ?: emptyList()
                _aiSearchResults.value = files
            } else {
                _aiSearchResults.value = emptyList() // or handle error
            }
            _isAiLoading.value = false
        }
    }

    private fun filterAndSort(
        items: List<SearchItem>,
        query: String,
        category: String,
        sort: Sort,
        aiResult: List<String>?
    ): List<SearchItem> {
        if (query.isBlank()) {
            return emptyList()
        }

        val filtered = items.filter { item ->
            val matchesQuery = if (aiResult != null) {
                // Nếu có kết quả AI, chỉ hiển thị những file nằm trong danh sách AI trả về
                aiResult.any { it.equals(item.name, ignoreCase = true) || it.contains(item.name, ignoreCase = true) }
            } else if (query.isBlank()) {
                true
            } else {
                when (category) {
                    "ocr" -> item.ocrText?.contains(query, ignoreCase = true) == true
                    "ai-objects" -> item.aiTags.any { it.contains(query, ignoreCase = true) }
                    else -> item.name.contains(query, ignoreCase = true) ||
                            (item.ocrText?.contains(query, ignoreCase = true) == true) ||
                            item.tags.any { it.contains(query, ignoreCase = true) }
                }
            }

            val matchesCategory = when (category) {
                "all" -> true
                "images" -> item.category == SearchItem.Category.IMAGE
                "documents" -> item.category == SearchItem.Category.DOCUMENT
                "pdf" -> item.category == SearchItem.Category.PDF
                "videos" -> item.category == SearchItem.Category.VIDEO
                "ocr" -> !item.ocrText.isNullOrBlank()
                "ai-objects" -> item.aiTags.isNotEmpty()
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
        private val favoriteLocalRepository: FavoriteLocalRepository,
        private val aiAnalysisRepository: com.example.filemanagementapp.data.ai.repository.AiAnalysisRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(
                username,
                searchRepository,
                searchHistoryRepo,
                favoriteLocalRepository,
                aiAnalysisRepository
            ) as T
        }
    }
}
