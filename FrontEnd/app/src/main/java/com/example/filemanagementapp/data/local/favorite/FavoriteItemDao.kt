package com.example.filemanagementapp.data.local.favorite

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteItemDao {
    @Query(
        """
        SELECT * FROM favorite_items
        WHERE username = :username
        AND item_path IN (:paths)
        """
    )
    suspend fun getByPaths(
        username: String,
        paths: List<String>
    ): List<FavoriteItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteItemEntity)

    @Query(
        """
        DELETE FROM favorite_items
        WHERE username = :username
        AND item_path = :path
        """
    )
    suspend fun deleteByPath(
        username: String,
        path: String
    )

    @Query(
        """
        DELETE FROM favorite_items
        WHERE username = :username
        AND (item_path = :pathPrefix OR item_path LIKE :pathPrefixWithSlash)
        """
    )
    suspend fun deleteByPrefix(
        username: String,
        pathPrefix: String,
        pathPrefixWithSlash: String
    )

    @Query(
        """
        UPDATE favorite_items
        SET item_path = :newPath
        WHERE username = :username
        AND item_path = :oldPath
        """
    )
    suspend fun updateExactPath(
        username: String,
        oldPath: String,
        newPath: String
    )

    @Query(
        """
        UPDATE favorite_items
        SET item_path = :newPrefix || SUBSTR(item_path, LENGTH(:oldPrefix) + 1)
        WHERE username = :username
        AND (item_path = :oldPrefix OR item_path LIKE :oldPrefixWithSlash)
        """
    )
    suspend fun replacePathPrefix(
        username: String,
        oldPrefix: String,
        oldPrefixWithSlash: String,
        newPrefix: String
    )
}
