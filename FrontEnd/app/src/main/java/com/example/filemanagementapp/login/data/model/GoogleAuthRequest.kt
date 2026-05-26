package com.example.filemanagementapp.login.data.model

data class GoogleAuthRequest(
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val googleUid: String
)
