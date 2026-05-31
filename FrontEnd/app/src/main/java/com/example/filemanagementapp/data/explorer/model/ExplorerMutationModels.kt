package com.example.filemanagementapp.data.explorer.model

data class ExplorerRenameFileRequest(
    val username: String,
    val filePath: String,
    val newFileName: String
)

data class ExplorerRenameFolderRequest(
    val username: String,
    val folderPath: String,
    val newFolderName: String
)

data class ExplorerMoveFileRequest(
    val username: String,
    val sourceFilePath: String,
    val targetFolderPath: String
)

data class ExplorerMoveFolderRequest(
    val username: String,
    val sourceFolderPath: String,
    val targetFolderPath: String
)

data class ExplorerDeleteFileRequest(
    val username: String,
    val filePath: String
)

data class ExplorerDeleteFolderRequest(
    val username: String,
    val folderPath: String
)

data class ExplorerCreateFolderRequest(
    val username: String,
    val targetPath: String,
    val folderName: String
)

data class ExplorerMutationResponse(
    val message: String? = null,
    val error: String? = null,
    val detail: String? = null
)
