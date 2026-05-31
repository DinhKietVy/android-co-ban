package com.example.filemanagementapp.data.auth.model

data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
)
