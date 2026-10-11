package com.example.filemanagementapp.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainNavigationViewModel : ViewModel() {
    private val _openFolderRequests = MutableStateFlow<String?>(null)
    val openFolderRequests: StateFlow<String?> = _openFolderRequests.asStateFlow()

    private val _openDriveRequests = MutableStateFlow(false)
    val openDriveRequests: StateFlow<Boolean> = _openDriveRequests.asStateFlow()

    private val _triggerCreateFabRequests = MutableStateFlow(false)
    val triggerCreateFabRequests: StateFlow<Boolean> = _triggerCreateFabRequests.asStateFlow()

    fun requestOpenFolder(folderPath: String) {
        _openFolderRequests.value = folderPath
    }

    fun consumeOpenFolderRequest() {
        _openFolderRequests.value = null
    }

    fun requestOpenDrive() {
        _openDriveRequests.value = true
    }

    fun consumeOpenDriveRequest() {
        _openDriveRequests.value = false
    }

    fun requestTriggerCreateFab() {
        _triggerCreateFabRequests.value = true
    }

    fun consumeCreateFabRequest() {
        _triggerCreateFabRequests.value = false
    }
}
