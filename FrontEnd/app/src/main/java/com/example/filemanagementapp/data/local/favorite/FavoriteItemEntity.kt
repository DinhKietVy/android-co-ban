package com.example.filemanagementapp.data.local.favorite

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "favorite_items",
    primaryKeys = ["username", "item_path"]
)
data class FavoriteItemEntity(
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "item_path")
    val itemPath: String,
    @ColumnInfo(name = "item_type")
    val itemType: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
