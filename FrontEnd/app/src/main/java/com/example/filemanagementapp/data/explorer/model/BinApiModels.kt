package com.example.filemanagementapp.data.explorer.model

data class BinListRequest(
    val username: String
)

data class BinFile(
    val name: String,
    val size: Long?,
    val deletedAt: String
)

data class BinData(
    val username: String,
    val files: List<BinFile>
)

data class BinListResponse(
    val message: String,
    val data: BinData
)

data class BinActionRequest(
    val username: String,
    val fileName: String
)
