package com.example.filemanagementapp.login.data.network

import com.example.filemanagementapp.login.data.model.GoogleAuthRequest
import com.example.filemanagementapp.login.data.model.LoginRequest
import com.example.filemanagementapp.login.data.model.LoginResponse
import com.example.filemanagementapp.login.data.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("api/users")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<LoginResponse>

    @POST("api/users/google-auth")
    suspend fun googleAuth(
        @Body request: GoogleAuthRequest
    ): Response<LoginResponse>

    @POST("api/users/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>
}
