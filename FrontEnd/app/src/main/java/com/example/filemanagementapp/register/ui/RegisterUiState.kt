package com.example.filemanagementapp.register.ui

import com.example.filemanagementapp.util.UiText

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullNameError: UiText? = null,
    val emailError: UiText? = null,
    val usernameError: UiText? = null,
    val passwordError: UiText? = null,
    val confirmPasswordError: UiText? = null,
    val termsError: UiText? = null,
    val isTermsAccepted: Boolean = false,
    val isRegisterLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false
)
