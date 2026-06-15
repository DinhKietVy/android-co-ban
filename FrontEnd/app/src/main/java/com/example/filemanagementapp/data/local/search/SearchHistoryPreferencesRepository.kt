package com.example.filemanagementapp.data.local.search

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchHistoryPreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // In-memory flow for reactive updates
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: Flow<List<String>> = _recentSearches.asStateFlow()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val savedString = prefs.getString(KEY_HISTORY, "") ?: ""
        val historyList = if (savedString.isBlank()) {
            emptyList()
        } else {
            savedString.split(DELIMITER).filter { it.isNotBlank() }
        }
        _recentSearches.value = historyList
    }

    fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val current = _recentSearches.value.toMutableList()
        // Remove if it already exists to move it to the top
        current.remove(trimmed)
        current.add(0, trimmed) // Add to top

        // Limit to MAX_HISTORY items
        val limited = current.take(MAX_HISTORY)
        
        _recentSearches.value = limited
        
        prefs.edit {
            putString(KEY_HISTORY, limited.joinToString(DELIMITER))
        }
    }

    fun clearHistory() {
        _recentSearches.value = emptyList()
        prefs.edit { remove(KEY_HISTORY) }
    }

    companion object {
        private const val PREFS_NAME = "search_history_prefs"
        private const val KEY_HISTORY = "history"
        private const val DELIMITER = "|~|"
        private const val MAX_HISTORY = 10
    }
}
