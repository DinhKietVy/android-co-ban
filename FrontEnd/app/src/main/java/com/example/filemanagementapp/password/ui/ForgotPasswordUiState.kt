package com.example.filemanagementapp.password.ui

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.EMAIL,
    val email: String = "",
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val submittedEmail: String = "",
    val emailError: String? = null,
    val otpError: String? = null,
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null,
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
