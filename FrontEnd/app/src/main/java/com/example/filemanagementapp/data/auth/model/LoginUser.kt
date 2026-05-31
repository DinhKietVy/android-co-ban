package com.example.filemanagementapp.data.auth.model

data class LoginUser(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val email: String? = null,
    val provider: AuthProvider
)

enum class AuthProvider {
    PASSWORD,
    GOOGLE
}
