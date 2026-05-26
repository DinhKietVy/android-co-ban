package com.example.filemanagementapp.login.data.repository

import com.example.filemanagementapp.login.data.model.AuthApiError
import com.example.filemanagementapp.login.data.model.AuthProvider
import com.example.filemanagementapp.login.data.model.GoogleAuthRequest
import com.example.filemanagementapp.login.data.model.LoginRequest
import com.example.filemanagementapp.login.data.model.LoginResponse
import com.example.filemanagementapp.login.data.model.LoginUser
import com.example.filemanagementapp.login.data.model.RegisterRequest
import com.example.filemanagementapp.login.data.network.AuthApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class AuthRepository(
    private val authApiService: AuthApiService,
    private val gson: Gson
) {
    suspend fun syncGoogleUser(googleUser: LoginUser): Result<LoginUser> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedUsername = (googleUser.email ?: googleUser.username)
                    .trim()
                    .lowercase()
                val response = authApiService.googleAuth(
                    GoogleAuthRequest(
                        username = normalizedUsername,
                        email = googleUser.email,
                        displayName = googleUser.displayName,
                        googleUid = googleUser.id
                    )
                )
                response.toAuthResult(defaultErrorMessage = "Dong bo tai khoan Google that bai")
                    .map { backendUser ->
                        backendUser.copy(
                            displayName = googleUser.displayName,
                            email = googleUser.email,
                            provider = AuthProvider.GOOGLE
                        )
                    }
            } catch (ioException: IOException) {
                Result.failure(
                    Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
                )
            } catch (exception: Exception) {
                Result.failure(Exception(exception.message ?: "Dong bo tai khoan Google that bai"))
            }
        }

    suspend fun register(
        username: String,
        fullName: String,
        email: String,
        password: String
    ): Result<LoginUser> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApiService.register(
                    RegisterRequest(
                        username = username,
                        fullName = fullName,
                        email = email,
                        password = password
                    )
                )
                response.toAuthResult(defaultErrorMessage = "Tao tai khoan that bai")
            } catch (ioException: IOException) {
                Result.failure(
                    Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
                )
            } catch (exception: Exception) {
                Result.failure(Exception(exception.message ?: "Tao tai khoan that bai"))
            }
        }

    suspend fun login(username: String, password: String): Result<LoginUser> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApiService.login(
                    LoginRequest(username = username, password = password)
                )
                response.toAuthResult(defaultErrorMessage = "Dang nhap that bai")
            } catch (ioException: IOException) {
                Result.failure(
                    Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
                )
            } catch (exception: Exception) {
                Result.failure(Exception(exception.message ?: "Dang nhap that bai"))
            }
        }

    private fun Response<LoginResponse>.toAuthResult(defaultErrorMessage: String): Result<LoginUser> {
        if (isSuccessful) {
            val user = body()?.data
            if (user?.username.isNullOrBlank()) {
                return Result.failure(Exception("Server khong tra ve thong tin tai khoan hop le"))
            }

            return Result.success(
                LoginUser(
                    id = user.id?.toString().orEmpty(),
                    username = user.username.orEmpty(),
                    displayName = user.fullName,
                    email = user.email,
                    provider = AuthProvider.PASSWORD
                )
            )
        }

        val parsedError = errorBody()?.charStream()?.use { reader ->
            runCatching { gson.fromJson(reader, AuthApiError::class.java) }.getOrNull()
        }
        val errorMessage = parsedError?.error
            ?: parsedError?.detail
            ?: message()
            ?: defaultErrorMessage
        return Result.failure(Exception(errorMessage))
    }
}
