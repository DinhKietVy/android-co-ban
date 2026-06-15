package com.example.filemanagementapp.data.auth.model

data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)
