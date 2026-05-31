package com.example.filemanagementapp.data.local.ai

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "ai_analysis_cache",
    primaryKeys = ["username", "file_path"]
)
data class AiAnalysisCacheEntity(
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "tags_json")
    val tagsJson: String,
    @ColumnInfo(name = "ocr_text")
    val ocrText: String?,
    @ColumnInfo(name = "preview_image_path")
    val previewImagePath: String?,
    @ColumnInfo(name = "analysis_type")
    val analysisType: String?,
    @ColumnInfo(name = "model_source")
    val modelSource: String?,
    @ColumnInfo(name = "analyzed_at")
    val analyzedAt: Long
)
