package com.example.filemanagementapp.data.auth.repository

import com.example.filemanagementapp.data.auth.model.ApiMessageResponse
import com.example.filemanagementapp.data.auth.model.AuthApiError
import com.example.filemanagementapp.data.auth.model.AuthProvider
import com.example.filemanagementapp.data.auth.model.ForgotPasswordRequest
import com.example.filemanagementapp.data.auth.model.GoogleAuthRequest
import com.example.filemanagementapp.data.auth.model.LoginRequest
import com.example.filemanagementapp.data.auth.model.LoginResponse
import com.example.filemanagementapp.data.auth.model.LoginUser
import com.example.filemanagementapp.data.auth.model.ResetPasswordRequest
import com.example.filemanagementapp.data.auth.model.RegisterRequest
import com.example.filemanagementapp.data.auth.model.VerifyResetCodeRequest
import com.example.filemanagementapp.data.auth.model.ChangePasswordRequest
import com.example.filemanagementapp.data.auth.model.UpdateProfileRequest
import com.example.filemanagementapp.data.auth.network.AuthApiService
import com.example.filemanagementapp.data.auth.network.SessionCookieJar
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

    suspend fun autoLogin(): Result<LoginUser> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApiService.autoLogin()
                response.toAuthResult(defaultErrorMessage = "Phien dang nhap da het han")
            } catch (ioException: IOException) {
                Result.failure(
                    Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
                )
            } catch (exception: Exception) {
                Result.failure(Exception(exception.message ?: "Khong the tu dong dang nhap"))
            }
        }

    fun clearLocalSession() {
        SessionCookieJar.clear()
    }

    suspend fun forgotPassword(email: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                authApiService.forgotPassword(ForgotPasswordRequest(email = email))
                    .toMessageResult(defaultErrorMessage = "Gui ma dat lai mat khau that bai")
            } catch (ioException: IOException) {
                Result.failure(
                    Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
                )
            } catch (exception: Exception) {
                Result.failure(
                    Exception(exception.message ?: "Gui ma dat lai mat khau that bai")
                )
            }
        }

    suspend fun verifyResetCode(email: String, code: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                authApiService.verifyResetCode(
                    VerifyResetCodeRequest(email = email, code = code)
                ).toMessageResult(defaultErrorMessage = "Xac thuc ma OTP that bai")
            } catch (ioException: IOException) {
                Result.failure(
                    Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
                )
            } catch (exception: Exception) {
                Result.failure(Exception(exception.message ?: "Xac thuc ma OTP that bai"))
            }
        }

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                authApiService.resetPassword(
                    ResetPasswordRequest(
                        email = email,
                        code = code,
                        newPassword = newPassword
                    )
                ).toMessageResult(defaultErrorMessage = "Dat lai mat khau that bai")
            } catch (ioException: IOException) {
                Result.failure(
                    Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
                )
            } catch (exception: Exception) {
                Result.failure(Exception(exception.message ?: "Dat lai mat khau that bai"))
            }
        }

    suspend fun updateProfile(fullName: String, email: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                authApiService.updateProfile(
                    UpdateProfileRequest(
                        fullName = fullName,
                        email = email
                    )
                ).toMessageResult(defaultErrorMessage = "Cap nhat thong tin that bai")
            } catch (ioException: IOException) {
                Result.failure(
                    Exception("Khong the ket noi toi server. Kiem tra backend local va mang cua may ao.")
                )
            } catch (exception: Exception) {
                Result.failure(Exception(exception.message ?: "Cap nhat thong tin that bai"))
            }
        }

    suspend fun changePassword(request: ChangePasswordRequest): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApiService.changePassword(request)
                if (response.isSuccessful) {
                    val message = response.body()?.message ?: "Password changed successfully"
                    Result.success(message)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = try {
                        gson.fromJson(errorBody, AuthApiError::class.java).error
                    } catch (e: Exception) {
                        "Unknown error occurred"
                    }
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: IOException) {
                Result.failure(Exception("Network error. Please check your connection."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteAccount(): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApiService.deleteAccount()
                if (response.isSuccessful) {
                    val message = response.body()?.message ?: "Account deleted successfully"
                    Result.success(message)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = try {
                        gson.fromJson(errorBody, AuthApiError::class.java).error
                    } catch (e: Exception) {
                        "Unknown error occurred"
                    }
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: IOException) {
                Result.failure(Exception("Network error. Please check your connection."))
            } catch (e: Exception) {
                Result.failure(e)
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

    private fun Response<ApiMessageResponse>.toMessageResult(defaultErrorMessage: String): Result<String> {
        if (isSuccessful) {
            val responseMessage = body()?.message
            return Result.success(responseMessage ?: defaultErrorMessage)
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
