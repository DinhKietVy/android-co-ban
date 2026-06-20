package com.example.filemanagementapp.data.local.recent

import androidx.room.Entity

@Entity(
    tableName = "recently_opened",
    primaryKeys = ["username", "path"]
)
data class RecentOpenEntity(
    val username: String,
    val path: String,
    val openedAtEpochMillis: Long
)
