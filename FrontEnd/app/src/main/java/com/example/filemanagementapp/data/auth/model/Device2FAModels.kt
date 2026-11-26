package com.example.filemanagementapp.data.auth.model

data class VerifyDeviceOtpRequest(
    val tempToken: String,
    val otpCode: String,
    val trustDevice: Boolean = true
)

data class TrustedDeviceDto(
    val id: Int,
    val fingerprint: String,
    val device_name: String? = null,
    val ip_address: String? = null,
    val created_at: String? = null,
    val last_used_at: String? = null
)

data class TrustedDevicesResponse(
    val message: String? = null,
    val data: List<TrustedDeviceDto> = emptyList()
)

sealed interface LoginOutcome {
    data class Authenticated(val user: LoginUser) : LoginOutcome
    data class OtpRequired(val tempToken: String, val message: String) : LoginOutcome
}
