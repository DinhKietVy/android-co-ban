package com.example.filemanagementapp.profile.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.auth.network.AuthNetworkModule
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.example.filemanagementapp.util.UiText
import com.example.filemanagementapp.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val isLoading: Boolean = false,
    val successMessage: UiText? = null,
    val errorMessage: UiText? = null
)

class EditProfileViewModel : ViewModel() {
    private val authRepository = AuthRepository(AuthNetworkModule.authApiService, AuthNetworkModule.gson)

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    fun updateProfile(fullName: String, email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, successMessage = null)
            val result = authRepository.updateProfile(fullName, email)
            
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = result.getOrNull()?.let { UiText.DynamicString(it) } 
                        ?: UiText.StringResource(R.string.msg_action_success)
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.error_auth_update_info_failed)
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(successMessage = null, errorMessage = null)
    }
}
