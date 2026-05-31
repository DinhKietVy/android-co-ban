package com.example.filemanagementapp.data.auth.model

data class GoogleAuthRequest(
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val googleUid: String
)
