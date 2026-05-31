package com.example.filemanagementapp.data.ai.network

import com.example.filemanagementapp.data.ai.model.AiPredictImageResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AiApiService {
    @Multipart
    @POST("predict-image")
    suspend fun predictImage(
        @Part("username") username: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<AiPredictImageResponse>
}
