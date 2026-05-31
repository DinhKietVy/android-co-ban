package com.example.filemanagementapp.login.data.model

data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
)
