package com.example.filemanagementapp.data.auth.model

data class RegisterRequest(
    val username: String,
    val fullName: String,
    val email: String,
    val password: String
)
