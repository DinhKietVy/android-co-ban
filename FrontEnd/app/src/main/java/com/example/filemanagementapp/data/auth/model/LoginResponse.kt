package com.example.filemanagementapp.data.auth.model

data class LoginResponse(
    val message: String? = null,
    val data: UserDto? = null
)

data class UserDto(
    val id: Int? = null,
    val username: String? = null,
    val fullName: String? = null,
    val email: String? = null
)
