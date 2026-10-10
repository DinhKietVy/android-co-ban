package com.example.filemanagementapp.data.ai.network

import com.example.filemanagementapp.data.ai.model.AiPredictImageResponse
import com.example.filemanagementapp.data.ai.model.SearchRequest
import com.example.filemanagementapp.data.ai.model.AiSearchResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

import com.example.filemanagementapp.data.ai.model.ChatApiRequest
import com.example.filemanagementapp.data.ai.model.ChatApiResponse
import com.example.filemanagementapp.data.ai.model.ChatSessionsResponse
import com.example.filemanagementapp.data.ai.model.SessionMessagesResponse
import com.example.filemanagementapp.data.ai.model.CreateSessionRequest
import com.example.filemanagementapp.data.ai.model.CreateSessionResponse
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Path

interface AiApiService {
    @Multipart
    @POST("predict-image")
    suspend fun predictImage(
        @Part("username") username: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<AiPredictImageResponse>

    @POST("api/ai/search")
    suspend fun searchWithAi(
        @Body request: SearchRequest
    ): Response<AiSearchResponse>

    @POST("api/ai/chat")
    suspend fun sendChatMessage(
        @Body request: ChatApiRequest
    ): Response<ChatApiResponse>

    @GET("api/ai/sessions/{username}")
    suspend fun getSessions(
        @Path("username") username: String
    ): Response<ChatSessionsResponse>

    @GET("api/ai/sessions/{sessionId}/messages")
    suspend fun getSessionMessages(
        @Path("sessionId") sessionId: Int
    ): Response<SessionMessagesResponse>

    @POST("api/ai/sessions")
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): Response<CreateSessionResponse>

    @DELETE("api/ai/sessions/{sessionId}")
    suspend fun deleteSession(
        @Path("sessionId") sessionId: Int
    ): Response<Map<String, Any>>
}
