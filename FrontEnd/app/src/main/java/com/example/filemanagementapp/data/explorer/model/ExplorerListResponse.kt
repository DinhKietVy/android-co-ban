package com.example.filemanagementapp.data.explorer.model

data class ExplorerListResponse(
    val message: String? = null,
    val data: ExplorerDirectoryPayload? = null,
    val error: String? = null,
    val detail: String? = null
)

data class ExplorerDirectoryPayload(
    val username: String? = null,
    val currentFolder: String? = null,
    val folders: List<ExplorerFolderDto> = emptyList(),
    val files: List<ExplorerFileDto> = emptyList()
)

data class ExplorerFolderDto(
    val name: String? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null
)

data class ExplorerFileDto(
    val name: String? = null,
    val size: Long? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null
)
