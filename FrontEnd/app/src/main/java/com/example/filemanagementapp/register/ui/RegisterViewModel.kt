package com.example.filemanagementapp.register.ui

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.login.data.model.LoginUser
import com.example.filemanagementapp.login.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RegisterEvent>()
    val events: SharedFlow<RegisterEvent> = _events.asSharedFlow()

    fun onFullNameChanged(value: String) {
        _uiState.update { it.copy(fullName = value, fullNameError = null) }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, emailError = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, usernameError = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                confirmPasswordError = null
            )
        }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null) }
    }

    fun onTermsChanged(accepted: Boolean) {
        _uiState.update { it.copy(isTermsAccepted = accepted, termsError = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun register() {
        val state = _uiState.value
        val fullName = state.fullName.trim()
        val email = state.email.trim()
        val username = state.username.trim()
        val password = state.password
        val confirmPassword = state.confirmPassword

        val fullNameError = if (fullName.isBlank()) "Vui long nhap ho ten" else null
        val emailError = when {
            email.isBlank() -> "Vui long nhap email"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Email khong hop le"
            else -> null
        }
        val usernameError = when {
            username.isBlank() -> "Vui long nhap username"
            username.length < 4 -> "Username toi thieu 4 ky tu"
            else -> null
        }
        val passwordError = when {
            password.isBlank() -> "Vui long nhap mat khau"
            password.length < 6 -> "Mat khau toi thieu 6 ky tu"
            else -> null
        }
        val confirmPasswordError = when {
            confirmPassword.isBlank() -> "Vui long xac nhan mat khau"
            confirmPassword != password -> "Mat khau xac nhan khong khop"
            else -> null
        }
        val termsError = if (!state.isTermsAccepted) "Ban can dong y dieu khoan" else null

        if (
            fullNameError != null ||
            emailError != null ||
            usernameError != null ||
            passwordError != null ||
            confirmPasswordError != null ||
            termsError != null
        ) {
            _uiState.update {
                it.copy(
                    fullNameError = fullNameError,
                    emailError = emailError,
                    usernameError = usernameError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError,
                    termsError = termsError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRegisterLoading = true,
                    fullNameError = null,
                    emailError = null,
                    usernameError = null,
                    passwordError = null,
                    confirmPasswordError = null,
                    termsError = null
                )
            }

            authRepository.register(
                username = username,
                fullName = fullName,
                email = email,
                password = password
            )
                .onSuccess {
                    _uiState.update { current -> current.copy(isRegisterLoading = false) }
                    _events.emit(RegisterEvent.ShowMessage("Tao tai khoan thanh cong"))
                    _events.emit(RegisterEvent.NavigateToLogin(username))
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isRegisterLoading = false,
                            usernameError = throwable.message
                        )
                    }
                    _events.emit(
                        RegisterEvent.ShowMessage(
                            throwable.message ?: "Tao tai khoan that bai"
                        )
                    )
                }
        }
    }

    fun onGoogleRegisterStarted() {
        _uiState.update {
            it.copy(
                isGoogleLoading = true,
                fullNameError = null,
                emailError = null,
                usernameError = null,
                passwordError = null,
                confirmPasswordError = null,
                termsError = null
            )
        }
    }

    fun onGoogleRegisterFinished(result: Result<LoginUser>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleLoading = false) }
            result.onSuccess { user ->
                authRepository.syncGoogleUser(user)
                    .onSuccess { syncedUser ->
                        _events.emit(RegisterEvent.NavigateToMain(syncedUser))
                    }
                    .onFailure { throwable ->
                        _events.emit(
                            RegisterEvent.ShowMessage(
                                throwable.message ?: "Dong bo tai khoan Google that bai"
                            )
                        )
                    }
            }.onFailure { throwable ->
                _events.emit(
                    RegisterEvent.ShowMessage(
                        throwable.message ?: "Google Sign-In that bai"
                    )
                )
            }
        }
    }

    class Factory(
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RegisterViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
