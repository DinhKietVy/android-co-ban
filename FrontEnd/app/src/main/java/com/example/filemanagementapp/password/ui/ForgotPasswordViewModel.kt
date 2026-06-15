package com.example.filemanagementapp.password.ui

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.example.filemanagementapp.util.UiText
import com.example.filemanagementapp.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ForgotPasswordEvent>()
    val events: SharedFlow<ForgotPasswordEvent> = _events.asSharedFlow()

    fun onEmailChanged(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = null
            )
        }
    }

    fun onOtpChanged(value: String) {
        _uiState.update {
            it.copy(
                otp = value.filter(Char::isDigit).take(6),
                otpError = null
            )
        }
    }

    fun onNewPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                newPassword = value,
                newPasswordError = null,
                confirmPasswordError = null
            )
        }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                confirmPassword = value,
                confirmPasswordError = null
            )
        }
    }

    fun sendResetEmail(isResend: Boolean = false) {
        val email = _uiState.value.email.trim()
        val emailError = when {
            email.isBlank() -> UiText.StringResource(R.string.error_empty_email)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> UiText.StringResource(R.string.error_invalid_email)
            else -> null
        }

        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSendingEmail = true,
                    emailError = null,
                    otpError = if (isResend) null else it.otpError
                )
            }

            authRepository.forgotPassword(email)
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(
                            step = ForgotPasswordStep.OTP,
                            email = email,
                            submittedEmail = email,
                            otp = "",
                            otpError = null,
                            isSendingEmail = false
                        )
                    }
                    _events.emit(ForgotPasswordEvent.ShowMessage(UiText.DynamicString(message)))
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSendingEmail = false,
                            emailError = throwable.message?.let { UiText.DynamicString(it) }
                                ?: UiText.StringResource(R.string.error_auth_send_otp_failed)
                        )
                    }
                }
        }
    }

    fun verifyOtp() {
        val state = _uiState.value
        val email = state.email.trim()
        val otp = state.otp.trim()
        val otpError = when {
            otp.isBlank() -> UiText.StringResource(R.string.error_otp_invalid_length) // Reuse or add error_empty_otp
            otp.length != 6 -> UiText.StringResource(R.string.error_otp_invalid_length)
            else -> null
        }

        if (otpError != null) {
            _uiState.update { it.copy(otpError = otpError) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isVerifyingOtp = true,
                    otpError = null
                )
            }

            authRepository.verifyResetCode(email = email, code = otp)
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(
                            step = ForgotPasswordStep.RESET,
                            isVerifyingOtp = false,
                            otpError = null
                        )
                    }
                    _events.emit(ForgotPasswordEvent.ShowMessage(UiText.DynamicString(message)))
                }
                .onFailure { throwable ->
                    handleOtpFailure(throwable.message ?: "Xac thuc ma OTP that bai")
                }
        }
    }

    fun resetPassword() {
        val state = _uiState.value
        val newPassword = state.newPassword
        val confirmPassword = state.confirmPassword

        val newPasswordError = when {
            newPassword.isBlank() -> UiText.StringResource(R.string.error_empty_new_password)
            newPassword.length < 6 -> UiText.StringResource(R.string.error_password_too_short)
            else -> null
        }
        val confirmPasswordError = when {
            confirmPassword.isBlank() -> UiText.StringResource(R.string.error_empty_confirm_new_password)
            confirmPassword != newPassword -> UiText.StringResource(R.string.error_password_mismatch)
            else -> null
        }

        if (newPasswordError != null || confirmPasswordError != null) {
            _uiState.update {
                it.copy(
                    newPasswordError = newPasswordError,
                    confirmPasswordError = confirmPasswordError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResettingPassword = true,
                    newPasswordError = null,
                    confirmPasswordError = null
                )
            }

            authRepository.resetPassword(
                email = state.email.trim(),
                code = state.otp.trim(),
                newPassword = newPassword
            ).onSuccess { message ->
                _uiState.update {
                    it.copy(
                        step = ForgotPasswordStep.SUCCESS,
                        isResettingPassword = false,
                        newPassword = "",
                        confirmPassword = "",
                        newPasswordError = null,
                        confirmPasswordError = null
                    )
                }
                _events.emit(ForgotPasswordEvent.ShowMessage(UiText.DynamicString(message)))
            }.onFailure { throwable ->
                handleResetFailure(throwable.message ?: "Dat lai mat khau that bai")
            }
        }
    }

    fun returnToEmailStep() {
        _uiState.update {
            it.copy(
                step = ForgotPasswordStep.EMAIL,
                otpError = null,
                newPasswordError = null,
                confirmPasswordError = null,
                isVerifyingOtp = false,
                isResettingPassword = false
            )
        }
    }

    fun returnToOtpStep() {
        _uiState.update {
            it.copy(
                step = ForgotPasswordStep.OTP,
                newPasswordError = null,
                confirmPasswordError = null,
                isResettingPassword = false
            )
        }
    }

    private fun handleOtpFailure(message: String) {
        _uiState.update { state ->
            when {
                message.contains("Email khong ton tai", ignoreCase = true) -> state.copy(
                    step = ForgotPasswordStep.EMAIL,
                    isVerifyingOtp = false,
                    emailError = UiText.StringResource(R.string.error_email_not_found),
                    otpError = null
                )

                else -> state.copy(
                    isVerifyingOtp = false,
                    otpError = UiText.DynamicString(message)
                )
            }
        }
    }

    private fun handleResetFailure(message: String) {
        _uiState.update { state ->
            when {
                message.contains("Ma xac nhan", ignoreCase = true) -> state.copy(
                    step = ForgotPasswordStep.OTP,
                    isResettingPassword = false,
                    otpError = UiText.StringResource(R.string.error_auth_verify_otp_failed),
                    newPasswordError = null,
                    confirmPasswordError = null
                )

                message.contains("Email khong ton tai", ignoreCase = true) -> state.copy(
                    step = ForgotPasswordStep.EMAIL,
                    isResettingPassword = false,
                    emailError = UiText.StringResource(R.string.error_email_not_found),
                    otpError = null,
                    newPasswordError = null,
                    confirmPasswordError = null
                )

                else -> state.copy(
                    isResettingPassword = false,
                    newPasswordError = UiText.DynamicString(message)
                )
            }
        }
    }

    class Factory(
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ForgotPasswordViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ForgotPasswordViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
