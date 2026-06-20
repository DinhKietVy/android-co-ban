package com.example.filemanagementapp.data.local.recent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentOpenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentOpenEntity)

    @Query("SELECT * FROM recently_opened WHERE username = :username ORDER BY openedAtEpochMillis DESC LIMIT :limit")
    suspend fun getRecentOpens(username: String, limit: Int): List<RecentOpenEntity>
}
