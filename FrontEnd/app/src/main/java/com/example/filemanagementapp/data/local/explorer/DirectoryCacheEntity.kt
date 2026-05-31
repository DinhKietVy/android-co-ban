package com.example.filemanagementapp.data.local.explorer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "directory_cache",
    indices = [Index(value = ["username", "folderPath"], unique = true)]
)
data class DirectoryCacheEntity(
    @PrimaryKey val cacheKey: String,
    val username: String,
    val folderPath: String,
    val currentFolder: String,
    val storageSummary: String,
    val payloadJson: String,
    val fetchedAt: Long
)
