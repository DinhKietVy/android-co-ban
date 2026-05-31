package com.example.filemanagementapp.login.data.model

data class VerifyResetCodeRequest(
    val email: String,
    val code: String
)
