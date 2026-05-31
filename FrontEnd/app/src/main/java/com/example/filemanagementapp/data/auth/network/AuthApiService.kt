package com.example.filemanagementapp.data.auth.network

import com.example.filemanagementapp.data.auth.model.ApiMessageResponse
import com.example.filemanagementapp.data.auth.model.ForgotPasswordRequest
import com.example.filemanagementapp.data.auth.model.GoogleAuthRequest
import com.example.filemanagementapp.data.auth.model.LoginRequest
import com.example.filemanagementapp.data.auth.model.LoginResponse
import com.example.filemanagementapp.data.auth.model.ResetPasswordRequest
import com.example.filemanagementapp.data.auth.model.RegisterRequest
import com.example.filemanagementapp.data.auth.model.VerifyResetCodeRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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

    @GET("api/users/auto-login")
    suspend fun autoLogin(): Response<LoginResponse>

    @POST("api/users/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<ApiMessageResponse>

    @POST("api/users/verify-reset-code")
    suspend fun verifyResetCode(
        @Body request: VerifyResetCodeRequest
    ): Response<ApiMessageResponse>

    @POST("api/users/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<ApiMessageResponse>
}
