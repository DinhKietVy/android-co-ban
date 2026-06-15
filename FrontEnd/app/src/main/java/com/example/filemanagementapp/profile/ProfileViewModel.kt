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
    val totalGb: Double,
    val usedGb: Double,
    val imagesGb: Double,
    val documentsGb: Double,
    val videosGb: Double,
    val otherGb: Double
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
                
                var aggregatedStorage = aggregateLocalStorage(emptyList())
                if (loginUser != null) {
                    val filesResult = explorerRepository.listDirectory(loginUser.username, "")
                    if (filesResult.isSuccess) {
                        aggregatedStorage = aggregateLocalStorage(filesResult.getOrNull()?.items ?: emptyList())
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
    
    private fun aggregateLocalStorage(items: List<ExplorerItem>): StorageUsage {
        var imagesBytes = 0L
        var docsBytes = 0L
        var videosBytes = 0L
        var otherBytes = 0L

        for (item in items) {
            if (item.type == ExplorerItem.Type.FILE) {
                val size = item.sizeBytes ?: 0L
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
        
        // Convert Bytes to GB
        val bytesToGb = 1024.0 * 1024.0 * 1024.0

        return StorageUsage(
            totalGb = 0.0, // Not used anymore as limit was removed
            usedGb = Math.round((totalUsedBytes / bytesToGb) * 100) / 100.0,
            imagesGb = Math.round((imagesBytes / bytesToGb) * 100) / 100.0,
            documentsGb = Math.round((docsBytes / bytesToGb) * 100) / 100.0,
            videosGb = Math.round((videosBytes / bytesToGb) * 100) / 100.0,
            otherGb = Math.round((otherBytes / bytesToGb) * 100) / 100.0
        )
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
