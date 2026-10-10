package com.example.filemanagementapp.data.ai.model

data class ChatHistoryPart(
    val text: String
)

data class ChatHistoryTurn(
    val role: String,
    val parts: List<ChatHistoryPart>
)

data class ChatApiRequest(
    val username: String,
    val prompt: String,
    val sessionId: Int? = null,
    val filePath: String? = null,
    val history: List<ChatHistoryTurn>? = null
)

data class ChatData(
    val role: String? = null,
    val text: String? = null,
    val actionsExecuted: List<String>? = null,
    val affectedItems: List<String>? = null
)

data class ChatApiResponse(
    val success: Boolean,
    val sessionId: Int? = null,
    val data: ChatData? = null,
    val error: String? = null
)

data class CreateSessionRequest(
    val username: String,
    val title: String? = null,
    val aiService: String? = "openai"
)

data class CreateSessionResponse(
    val success: Boolean,
    val data: CreateSessionData? = null,
    val error: String? = null
)

data class CreateSessionData(
    val sessionId: Int
)

data class ChatSessionItem(
    val id: Int,
    val title: String?,
    val aiService: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class ChatSessionsResponse(
    val success: Boolean,
    val data: List<ChatSessionItem>? = null,
    val error: String? = null
)

data class ChatMessageItem(
    val id: Int,
    val role: String,
    val content: String,
    val filePath: String? = null,
    val actionsExecuted: List<String>? = null,
    val affectedItems: List<String>? = null,
    val createdAt: String? = null
)

data class SessionMessagesResponse(
    val success: Boolean,
    val data: List<ChatMessageItem>? = null,
    val error: String? = null
)
