package com.example.filemanagementapp.register.ui

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullNameError: String? = null,
    val emailError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val termsError: String? = null,
    val isTermsAccepted: Boolean = false,
    val isRegisterLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false
)
