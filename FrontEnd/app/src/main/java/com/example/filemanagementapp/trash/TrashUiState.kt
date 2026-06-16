package com.example.filemanagementapp.trash

import com.example.filemanagementapp.trash.TrashItemModel

data class TrashUiState(
    val items: List<TrashItemModel> = emptyList(),
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val errorMessage: String? = null
)
