package com.example.filemanagementapp.data.local.explorer

import com.example.filemanagementapp.data.explorer.repository.ExplorerDirectoryData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectoryCacheLocalRepository(
    private val directoryCacheDao: DirectoryCacheDao,
    private val gson: Gson
) {
    suspend fun getCachedDirectory(
        username: String,
        folderPath: String
    ): CachedDirectorySnapshot? = withContext(Dispatchers.IO) {
        directoryCacheDao.getByFolder(
            username = username,
            folderPath = normalizePath(folderPath)
        )?.toSnapshot()
    }

    suspend fun getAllCachedDirectories(
        username: String
    ): List<ExplorerDirectoryData> = withContext(Dispatchers.IO) {
        directoryCacheDao.getAllForUser(username).mapNotNull { entity ->
            runCatching {
                gson.fromJson(entity.payloadJson, ExplorerDirectoryData::class.java)
            }.getOrNull()
        }
    }

    suspend fun upsertDirectory(
        username: String,
        folderPath: String,
        directory: ExplorerDirectoryData
    ) = withContext(Dispatchers.IO) {
        directoryCacheDao.upsert(
            DirectoryCacheEntity(
                cacheKey = buildCacheKey(username, folderPath),
                username = username,
                folderPath = normalizePath(folderPath),
                currentFolder = directory.currentFolder,
                storageSummary = directory.storageSummary,
                payloadJson = gson.toJson(directory),
                fetchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun invalidateDirectory(
        username: String,
        folderPath: String
    ) = withContext(Dispatchers.IO) {
        directoryCacheDao.deleteByFolder(
            username = username,
            folderPath = normalizePath(folderPath)
        )
    }

    private fun DirectoryCacheEntity.toSnapshot(): CachedDirectorySnapshot? {
        val directory = runCatching {
            gson.fromJson(payloadJson, ExplorerDirectoryData::class.java)
        }.getOrNull() ?: return null

        return CachedDirectorySnapshot(
            directory = directory,
            fetchedAt = fetchedAt
        )
    }

    private fun buildCacheKey(username: String, folderPath: String): String {
        return "$username::${normalizePath(folderPath)}"
    }

    private fun normalizePath(folderPath: String): String {
        return folderPath.trim().trim('/')
    }
}

data class CachedDirectorySnapshot(
    val directory: ExplorerDirectoryData,
    val fetchedAt: Long
)
