package com.example.filemanagementapp.profile.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.auth.network.AuthNetworkModule
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.example.filemanagementapp.data.auth.model.ChangePasswordRequest
import com.example.filemanagementapp.util.UiText
import com.example.filemanagementapp.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val isLoading: Boolean = false,
    val successMessage: UiText? = null,
    val errorMessage: UiText? = null
)

class ChangePasswordViewModel : ViewModel() {
    private val authRepository = AuthRepository(AuthNetworkModule.authApiService, AuthNetworkModule.gson)

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, successMessage = null)
            val result = authRepository.changePassword(
                ChangePasswordRequest(oldPassword = oldPassword, newPassword = newPassword)
            )
            
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = result.getOrNull()?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.change_password_success)
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.profile_change_password) // Or add specific error string
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(successMessage = null, errorMessage = null)
    }
}
