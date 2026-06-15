package com.example.filemanagementapp.login.ui

import com.example.filemanagementapp.util.UiText

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val usernameError: UiText? = null,
    val passwordError: UiText? = null,
    val isLoginLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isRememberMeChecked: Boolean = false
)
