package com.example.filemanagementapp.data.ai.repository

import android.content.Context
import android.util.Base64
import com.example.filemanagementapp.data.ai.model.AiPredictImageResponse
import com.example.filemanagementapp.data.ai.network.AiApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.UUID

class AiAnalysisRepository(
    private val context: Context,
    private val aiApiService: AiApiService,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun analyzeImageFile(
        username: String,
        fileName: String,
        downloadUrl: String
    ): Result<AiAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val fileBytes = downloadFile(downloadUrl)
                ?: return@withContext Result.failure(Exception("Khong the tai file de phan tich AI"))

            val tempFile = File.createTempFile("ai-upload-", "-$fileName", context.cacheDir).apply {
                writeBytes(fileBytes)
            }

            val usernameBody = username.toRequestBody("text/plain".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "file",
                fileName,
                tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            val response = aiApiService.predictImage(usernameBody, filePart)
            tempFile.delete()
            response.toAnalysisResult(username, fileName)
        } catch (ioException: IOException) {
            Result.failure(Exception("Khong the ket noi toi AI server. Kiem tra AI service va mang."))
        } catch (exception: Exception) {
            Result.failure(Exception(exception.message ?: "Khong the phan tich file voi AI"))
        }
    }

    private fun downloadFile(downloadUrl: String): ByteArray? {
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }
            return response.body?.bytes()
        }
    }

    private fun Response<AiPredictImageResponse>.toAnalysisResult(
        username: String,
        fileName: String
    ): Result<AiAnalysisResult> {
        if (!isSuccessful) {
            val parsedError = errorBody()?.charStream()?.use { reader ->
                runCatching { gson.fromJson(reader, AiPredictImageResponse::class.java) }.getOrNull()
            }
            val errorMessage = parsedError?.error ?: message() ?: "AI server tra ve loi"
            return Result.failure(Exception(errorMessage))
        }

        val payload = body() ?: return Result.failure(Exception("AI server khong tra ve du lieu hop le"))
        if (!payload.error.isNullOrBlank()) {
            return Result.failure(Exception(payload.error))
        }

        val cleanTexts = payload.texts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            
        val cleanTags = payload.tags
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val base64 = payload.image_base64
        val previewImagePath = if (!base64.isNullOrBlank()) {
            try {
                val targetDir = File(context.cacheDir, "ai-preview")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val safeName = fileName.substringBeforeLast('.', fileName)
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                val targetFile = File(targetDir, "${safeName}_${UUID.randomUUID()}.jpg")
                targetFile.writeBytes(Base64.decode(base64, Base64.DEFAULT))
                targetFile.absolutePath
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return Result.success(
            AiAnalysisResult(
                texts = cleanTexts,
                previewImagePath = previewImagePath,
                tags = cleanTags
            )
        )
    }
}

data class AiAnalysisResult(
    val texts: List<String>,
    val previewImagePath: String?,
    val tags: List<String>
)
