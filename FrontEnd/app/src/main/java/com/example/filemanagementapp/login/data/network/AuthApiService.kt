package com.example.filemanagementapp.login.data.network

import com.example.filemanagementapp.login.data.model.ApiMessageResponse
import com.example.filemanagementapp.login.data.model.ForgotPasswordRequest
import com.example.filemanagementapp.login.data.model.GoogleAuthRequest
import com.example.filemanagementapp.login.data.model.LoginRequest
import com.example.filemanagementapp.login.data.model.LoginResponse
import com.example.filemanagementapp.login.data.model.ResetPasswordRequest
import com.example.filemanagementapp.login.data.model.RegisterRequest
import com.example.filemanagementapp.login.data.model.VerifyResetCodeRequest
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
