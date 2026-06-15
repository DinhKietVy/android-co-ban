package com.example.filemanagementapp.password.ui

import com.example.filemanagementapp.util.UiText

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.EMAIL,
    val email: String = "",
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val submittedEmail: String = "",
    val emailError: UiText? = null,
    val otpError: UiText? = null,
    val newPasswordError: UiText? = null,
    val confirmPasswordError: UiText? = null,
    val isSendingEmail: Boolean = false,
    val isVerifyingOtp: Boolean = false,
    val isResettingPassword: Boolean = false
)

enum class ForgotPasswordStep {
    EMAIL,
    OTP,
    RESET,
    SUCCESS
}
