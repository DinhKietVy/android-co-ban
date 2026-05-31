package com.example.filemanagementapp.data.local.ai

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiAnalysisLocalRepository(
    private val aiAnalysisCacheDao: AiAnalysisCacheDao,
    private val gson: Gson
) {
    suspend fun getAnalysisByPaths(
        username: String,
        filePaths: List<String>
    ): Map<String, AiAnalysisCache> = withContext(Dispatchers.IO) {
        if (filePaths.isEmpty()) {
            return@withContext emptyMap()
        }

        aiAnalysisCacheDao.getByPaths(username, filePaths)
            .associate { entity ->
                entity.filePath to entity.toDomainModel()
            }
    }

    suspend fun upsertAnalysis(record: AiAnalysisCache) = withContext(Dispatchers.IO) {
        aiAnalysisCacheDao.upsert(record.toEntity())
    }

    suspend fun updatePath(
        username: String,
        oldPath: String,
        newPath: String,
        isFolder: Boolean
    ) = withContext(Dispatchers.IO) {
        if (isFolder) {
            aiAnalysisCacheDao.replacePathPrefix(
                username = username,
                oldPrefix = oldPath,
                oldPrefixWithSlash = "$oldPath/%",
                newPrefix = newPath
            )
        } else {
            aiAnalysisCacheDao.updateExactPath(
                username = username,
                oldPath = oldPath,
                newPath = newPath
            )
        }
    }

    suspend fun deletePath(
        username: String,
        path: String,
        isFolder: Boolean
    ) = withContext(Dispatchers.IO) {
        if (isFolder) {
            aiAnalysisCacheDao.deleteByPrefix(
                username = username,
                pathPrefix = path,
                pathPrefixWithSlash = "$path/%"
            )
        } else {
            aiAnalysisCacheDao.deleteByPath(username, path)
        }
    }

    private fun AiAnalysisCacheEntity.toDomainModel(): AiAnalysisCache {
        return AiAnalysisCache(
            username = username,
            filePath = filePath,
            status = AiAnalysisStatus.fromStorageValue(status),
            tags = parseTags(tagsJson),
            ocrText = ocrText,
            previewImagePath = previewImagePath,
            analysisType = analysisType,
            modelSource = modelSource,
            analyzedAt = analyzedAt
        )
    }

    private fun AiAnalysisCache.toEntity(): AiAnalysisCacheEntity {
        return AiAnalysisCacheEntity(
            username,
            filePath,
            status.storageValue,
            gson.toJson(tags),
            ocrText,
            previewImagePath,
            analysisType,
            modelSource,
            analyzedAt
        )
    }

    private fun parseTags(tagsJson: String): List<String> {
        return runCatching {
            gson.fromJson<List<String>>(
                tagsJson,
                object : TypeToken<List<String>>() {}.type
            )
        }.getOrDefault(emptyList())
    }
}

data class AiAnalysisCache(
    val username: String,
    val filePath: String,
    val status: AiAnalysisStatus,
    val tags: List<String> = emptyList(),
    val ocrText: String? = null,
    val previewImagePath: String? = null,
    val analysisType: String? = null,
    val modelSource: String? = null,
    val analyzedAt: Long = System.currentTimeMillis()
)

enum class AiAnalysisStatus(val storageValue: String) {
    COMPLETED("completed"),
    PROCESSING("processing"),
    FAILED("failed");

    companion object {
        fun fromStorageValue(value: String): AiAnalysisStatus {
            return entries.firstOrNull { it.storageValue == value } ?: COMPLETED
        }
    }
}
