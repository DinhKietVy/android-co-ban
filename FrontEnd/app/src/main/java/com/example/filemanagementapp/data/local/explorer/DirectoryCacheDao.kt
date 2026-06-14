package com.example.filemanagementapp.data.local.explorer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DirectoryCacheDao {
    @Query(
        """
        SELECT * FROM directory_cache
        WHERE username = :username AND folderPath = :folderPath
        LIMIT 1
        """
    )
    suspend fun getByFolder(username: String, folderPath: String): DirectoryCacheEntity?

    @Query(
        """
        SELECT * FROM directory_cache
        WHERE username = :username
        """
    )
    suspend fun getAllForUser(username: String): List<DirectoryCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DirectoryCacheEntity)

    @Query(
        """
        DELETE FROM directory_cache
        WHERE username = :username AND folderPath = :folderPath
        """
    )
    suspend fun deleteByFolder(username: String, folderPath: String)
}
