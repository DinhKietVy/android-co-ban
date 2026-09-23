package com.example.filemanagementapp.data.explorer.model

import com.google.gson.annotations.SerializedName

data class ExplorerShareRequest(
    @SerializedName("ownerUsername") val ownerUsername: String,
    @SerializedName("targetUsername") val targetUsername: String,
    @SerializedName("filePath") val filePath: String,
    @SerializedName("permission") val permission: String
)

data class ExplorerUnshareRequest(
    @SerializedName("ownerUsername") val ownerUsername: String,
    @SerializedName("targetUsername") val targetUsername: String,
    @SerializedName("filePath") val filePath: String
)

data class ExplorerSharedFile(
    @SerializedName("file_path") val filePath: String,
    @SerializedName("ownerUsername") val ownerUsername: String? = null,
    @SerializedName("targetUsername") val targetUsername: String? = null,
    @SerializedName("permission") val permission: String? = null
)

data class ExplorerSharedResponse(
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: List<ExplorerSharedFile>
)

data class PublicLinkCreateRequest(
    @SerializedName("username") val username: String,
    @SerializedName("filePath") val filePath: String
)

data class PublicLinkCreateResponse(
    @SerializedName("token") val token: String,
    @SerializedName("message") val message: String
)

data class PublicLinkInfoResponse(
    @SerializedName("fileName") val fileName: String,
    @SerializedName("ownerUsername") val ownerUsername: String,
    @SerializedName("size") val size: Long,
    @SerializedName("filePath") val filePath: String,
    @SerializedName("permission") val permission: String? = null
)
