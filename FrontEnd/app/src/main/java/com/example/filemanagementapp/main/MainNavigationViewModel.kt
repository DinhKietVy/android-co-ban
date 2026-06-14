package com.example.filemanagementapp.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MainNavigationViewModel : ViewModel() {
    private val _openFolderRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openFolderRequests: SharedFlow<String> = _openFolderRequests.asSharedFlow()

    fun requestOpenFolder(folderPath: String) {
        _openFolderRequests.tryEmit(folderPath)
    }
}
