package com.example.filemanagementapp.profile

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.auth.network.AuthNetworkModule
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.data.local.profile.AiFeaturesSettings
import com.example.filemanagementapp.data.local.profile.SettingsPreferencesRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import com.example.filemanagementapp.util.UiText
import com.example.filemanagementapp.R
import kotlinx.coroutines.launch

data class UserProfile(
    val fullName: String,
    val email: String,
    val username: String,
    val isPro: Boolean,
    val avatarUrl: String? = null
)

data class StorageUsage(
    val totalFormatted: String,
    val usedFormatted: String,
    val imagesFormatted: String,
    val documentsFormatted: String,
    val videosFormatted: String,
    val otherFormatted: String
)

data class ProfileUiState(
    val profile: UserProfile? = null,
    val storage: StorageUsage? = null,
    val aiSettings: AiFeaturesSettings = AiFeaturesSettings(),
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val isAccountDeleted: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(AuthNetworkModule.authApiService, AuthNetworkModule.gson)
    private val explorerRepository = ExplorerRepository(
        appContext = application.applicationContext,
        explorerApiService = ExplorerNetworkModule.explorerApiService,
        gson = ExplorerNetworkModule.gson
    )
    
    private val settingsRepository = SettingsPreferencesRepository(application.applicationContext)
    
    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val appDatabase = com.example.filemanagementapp.data.local.AppDatabase.getInstance(application.applicationContext)
    private val aiAnalysisLocalRepository = com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository(
        appDatabase.aiAnalysisCacheDao(), 
        ExplorerNetworkModule.gson
    )

    init {
        fetchProfileData()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.aiSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(aiSettings = settings)
            }
        }
    }

    private fun fetchProfileData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            val profileResult = authRepository.autoLogin()
            
            if (profileResult.isSuccess) {
                val loginUser = profileResult.getOrNull()
                val userProfile = loginUser?.let {
                    UserProfile(
                        fullName = it.displayName ?: getApplication<Application>().getString(R.string.profile_default_name),
                        email = it.email ?: "",
                        username = it.username,
                        isPro = false
                    )
                }
                
                var aggregatedStorage = aggregateLocalStorage("", emptyList())
                if (loginUser != null) {
                    val filesResult = explorerRepository.listDirectory(loginUser.username, "")
                    if (filesResult.isSuccess) {
                        aggregatedStorage = aggregateLocalStorage(loginUser.username, filesResult.getOrNull()?.items ?: emptyList())
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    profile = userProfile,
                    storage = aggregatedStorage,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = profileResult.exceptionOrNull()?.message?.let { UiText.DynamicString(it) }
                )
            }
        }
    }
    
    private suspend fun aggregateLocalStorage(username: String, items: List<ExplorerItem>): StorageUsage {
        var imagesBytes = 0L
        var docsBytes = 0L
        var videosBytes = 0L
        var otherBytes = 0L

        val analysisMap = if (username.isNotBlank() && items.isNotEmpty()) {
            aiAnalysisLocalRepository.getAnalysisByPaths(
                username = username,
                filePaths = items.filter { it.type == ExplorerItem.Type.FILE }.map { it.path }
            )
        } else {
            emptyMap()
        }

        for (item in items) {
            if (item.type == ExplorerItem.Type.FILE) {
                var size = item.sizeBytes ?: 0L
                val analysis = analysisMap[item.path]
                if (analysis?.previewImagePath != null) {
                    val file = java.io.File(analysis.previewImagePath)
                    if (file.exists()) {
                        size += file.length()
                    }
                }

                val name = item.name.lowercase()
                when {
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                        name.endsWith(".webp") || name.endsWith(".bmp") || name.endsWith(".gif") -> imagesBytes += size
                    
                    name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") ||
                        name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".txt") -> docsBytes += size
                    
                    name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".avi") ||
                        name.endsWith(".mkv") || name.endsWith(".webm") -> videosBytes += size
                    
                    else -> otherBytes += size
                }
            }
        }

        val totalUsedBytes = imagesBytes + docsBytes + videosBytes + otherBytes

        return StorageUsage(
            totalFormatted = formatSize(0L), // Not used anymore as limit was removed
            usedFormatted = formatSize(totalUsedBytes),
            imagesFormatted = formatSize(imagesBytes),
            documentsFormatted = formatSize(docsBytes),
            videosFormatted = formatSize(videosBytes),
            otherFormatted = formatSize(otherBytes)
        )
    }

    private fun formatSize(sizeInBytes: Long): String {
        if (sizeInBytes < 1024) return "$sizeInBytes B"

        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = sizeInBytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }

        val formatted = if (value >= 10 || value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
        return "$formatted ${units[unitIndex]}"
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.deleteAccount()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, isAccountDeleted = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.error_auth_delete_account_failed)
                )
            }
        }
    }

    fun updateAutoOcr(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoOcr(enabled) }
    }

    fun updateAutoObject(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoObject(enabled) }
    }

    fun updateAiMetadata(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAiMetadata(enabled) }
    }
}
