package com.example.filemanagementapp.login.ui

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val isLoginLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isRememberMeChecked: Boolean = false
)
