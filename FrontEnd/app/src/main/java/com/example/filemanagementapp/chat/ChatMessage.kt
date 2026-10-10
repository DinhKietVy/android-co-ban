package com.example.filemanagementapp.chat

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val filePath: String? = null,
    val actionsExecuted: List<String>? = null,
    val affectedItems: List<String>? = null,
    val isTyping: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
