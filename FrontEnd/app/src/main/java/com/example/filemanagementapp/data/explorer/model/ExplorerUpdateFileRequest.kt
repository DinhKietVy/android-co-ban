package com.example.filemanagementapp.data.explorer.model

data class ExplorerUpdateFileRequest(
    val username: String,
    val filePath: String,
    val content: String
)
