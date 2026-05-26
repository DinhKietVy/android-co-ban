package com.example.filemanagementapp.login.data.model

data class RegisterRequest(
    val username: String,
    val fullName: String,
    val email: String,
    val password: String
)
