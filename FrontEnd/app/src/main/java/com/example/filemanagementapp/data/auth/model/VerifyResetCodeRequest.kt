package com.example.filemanagementapp.data.auth.model

data class VerifyResetCodeRequest(
    val email: String,
    val code: String
)
