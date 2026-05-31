package com.example.filemanagementapp.login.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.auth.local.LoginPreferencesRepository
import com.example.filemanagementapp.data.auth.model.LoginUser
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val loginPreferencesRepository: LoginPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    init {
        observeSavedPreferences()
    }

    fun onUsernameChanged(value: String) {
        _uiState.update {
            it.copy(
                username = value,
                usernameError = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { state ->
            state.copy(isPasswordVisible = !state.isPasswordVisible)
        }
    }

    fun onRememberMeChanged(isChecked: Boolean) {
        _uiState.update { state ->
            state.copy(isRememberMeChecked = isChecked)
        }
    }

    fun login() {
        val currentState = _uiState.value
        val username = currentState.username.trim()
        val password = currentState.password

        val usernameError = if (username.isBlank()) {
            "Vui long nhap email hoac username"
        } else {
            null
        }
        val passwordError = if (password.isBlank()) {
            "Vui long nhap mat khau"
        } else {
            null
        }

        if (usernameError != null || passwordError != null) {
            _uiState.update {
                it.copy(
                    usernameError = usernameError,
                    passwordError = passwordError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoginLoading = true,
                    usernameError = null,
                    passwordError = null
                )
            }

            authRepository.login(username = username, password = password)
                .onSuccess { user ->
                    persistRememberMe(username)
                    _uiState.update { it.copy(isLoginLoading = false) }
                    _events.emit(LoginEvent.NavigateToMain(user))
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoginLoading = false,
                            passwordError = throwable.message
                        )
                    }
                    _events.emit(
                        LoginEvent.ShowMessage(
                            throwable.message ?: "Dang nhap that bai"
                        )
                    )
                }
        }
    }

    fun onGoogleLoginStarted() {
        _uiState.update {
            it.copy(
                isGoogleLoading = true,
                usernameError = null,
                passwordError = null
            )
        }
    }

    fun onGoogleLoginFinished(result: Result<LoginUser>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleLoading = false) }
            result.onSuccess { user ->
                authRepository.syncGoogleUser(user)
                    .onSuccess { syncedUser ->
                        persistRememberMe(syncedUser.email ?: syncedUser.username)
                        _events.emit(LoginEvent.NavigateToMain(syncedUser))
                    }
                    .onFailure { throwable ->
                        _events.emit(
                            LoginEvent.ShowMessage(
                                throwable.message ?: "Dong bo tai khoan Google that bai"
                            )
                        )
                    }
            }.onFailure { throwable ->
                _events.emit(
                    LoginEvent.ShowMessage(
                        throwable.message ?: "Google Sign-In that bai"
                    )
                )
            }
        }
    }

    private fun observeSavedPreferences() {
        viewModelScope.launch {
            loginPreferencesRepository.preferencesFlow.collect { savedPreferences ->
                _uiState.update { state ->
                    state.copy(
                        username = if (
                            savedPreferences.isRememberMeChecked &&
                            state.username.isBlank()
                        ) {
                            savedPreferences.rememberedUsername
                        } else {
                            state.username
                        },
                        isRememberMeChecked = savedPreferences.isRememberMeChecked
                    )
                }
            }
        }
    }

    private suspend fun persistRememberMe(username: String) {
        if (_uiState.value.isRememberMeChecked) {
            loginPreferencesRepository.saveRememberedLogin(
                username = username,
                rememberMe = true
            )
        } else {
            loginPreferencesRepository.clearRememberedLogin()
        }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val loginPreferencesRepository: LoginPreferencesRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LoginViewModel(
                    authRepository = authRepository,
                    loginPreferencesRepository = loginPreferencesRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
