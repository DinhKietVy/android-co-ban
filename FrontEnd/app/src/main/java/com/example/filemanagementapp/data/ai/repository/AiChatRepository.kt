package com.example.filemanagementapp.data.ai.repository

import com.example.filemanagementapp.data.ai.model.ChatApiRequest
import com.example.filemanagementapp.data.ai.model.ChatApiResponse
import com.example.filemanagementapp.data.ai.model.ChatMessageItem
import com.example.filemanagementapp.data.ai.model.ChatSessionItem
import com.example.filemanagementapp.data.ai.model.CreateSessionRequest
import com.example.filemanagementapp.data.ai.network.AiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiChatRepository(
    private val aiApiService: AiApiService
) {
    suspend fun sendMessage(
        username: String,
        prompt: String,
        sessionId: Int? = null,
        filePath: String? = null
    ): Result<ChatApiResponse> = withContext(Dispatchers.IO) {
        try {
            val request = ChatApiRequest(
                username = username,
                prompt = prompt,
                sessionId = sessionId,
                filePath = filePath
            )
            val response = aiApiService.sendChatMessage(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body.error ?: "Lỗi xử lý tin nhắn từ AI"))
                }
            } else {
                Result.failure(Exception("AI Chat failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessions(username: String): Result<List<ChatSessionItem>> = withContext(Dispatchers.IO) {
        try {
            val response = aiApiService.getSessions(username)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(body.data ?: emptyList())
            } else {
                Result.failure(Exception("Không thể tải danh sách phiên chat: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessionMessages(sessionId: Int): Result<List<ChatMessageItem>> = withContext(Dispatchers.IO) {
        try {
            val response = aiApiService.getSessionMessages(sessionId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(body.data ?: emptyList())
            } else {
                Result.failure(Exception("Không thể tải tin nhắn phiên: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSession(username: String, title: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = aiApiService.createSession(CreateSessionRequest(username = username, title = title))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success && body.data != null) {
                    Result.success(body.data.sessionId)
                } else {
                    Result.failure(Exception(body.error ?: "Không thể tạo phiên chat mới"))
                }
            } else {
                Result.failure(Exception("Lỗi tạo phiên chat: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = aiApiService.deleteSession(sessionId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Lỗi xóa phiên chat: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
