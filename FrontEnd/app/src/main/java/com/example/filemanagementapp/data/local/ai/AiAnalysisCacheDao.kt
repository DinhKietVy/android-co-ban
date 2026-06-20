package com.example.filemanagementapp.data.local.ai

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AiAnalysisCacheDao {
    @Query(
        """
        SELECT * FROM ai_analysis_cache
        WHERE username = :username
        """
    )
    suspend fun getAllForUser(username: String): List<AiAnalysisCacheEntity>

    @Query(
        """
        SELECT * FROM ai_analysis_cache
        WHERE username = :username
        AND file_path IN (:filePaths)
        """
    )
    suspend fun getByPaths(
        username: String,
        filePaths: List<String>
    ): List<AiAnalysisCacheEntity>

    @Upsert
    suspend fun upsert(entity: AiAnalysisCacheEntity)

    @Query(
        """
        UPDATE ai_analysis_cache
        SET file_path = :newPath
        WHERE username = :username
        AND file_path = :oldPath
        """
    )
    suspend fun updateExactPath(
        username: String,
        oldPath: String,
        newPath: String
    )

    @Query(
        """
        UPDATE ai_analysis_cache
        SET file_path = :newPrefix || SUBSTR(file_path, LENGTH(:oldPrefix) + 1)
        WHERE username = :username
        AND (file_path = :oldPrefix OR file_path LIKE :oldPrefixWithSlash)
        """
    )
    suspend fun replacePathPrefix(
        username: String,
        oldPrefix: String,
        oldPrefixWithSlash: String,
        newPrefix: String
    )

    @Query(
        """
        DELETE FROM ai_analysis_cache
        WHERE username = :username
        AND file_path = :path
        """
    )
    suspend fun deleteByPath(
        username: String,
        path: String
    )

    @Query(
        """
        DELETE FROM ai_analysis_cache
        WHERE username = :username
        AND (file_path = :pathPrefix OR file_path LIKE :pathPrefixWithSlash)
        """
    )
    suspend fun deleteByPrefix(
        username: String,
        pathPrefix: String,
        pathPrefixWithSlash: String
    )
}
