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

data class ExplorerConvertRequest(
    val username: String,
    val filePath: String,
    val targetFormat: String
)

data class ExplorerConvertResponse(
    val message: String? = null,
    val data: ExplorerConvertResponseData? = null,
    val error: String? = null,
    val detail: String? = null
)

data class ExplorerConvertResponseData(
    val originalPath: String,
    val newPath: String,
    val convertedFilePath: String
)

data class ExplorerCompressRequest(
    val username: String,
    val targetPath: String,
    val zipName: String,
    val items: List<String>
)

data class ExplorerCompressResponse(
    val message: String,
    val data: ExplorerCompressResponseData?
)

data class ExplorerCompressResponseData(
    val zipFilePath: String
)

data class ExplorerExtractRequest(
    val username: String,
    val filePath: String,
    val extractToPath: String
)

data class ExplorerExtractResponse(
    val message: String,
    val data: ExplorerExtractResponseData?
)

data class ExplorerExtractResponseData(
    val extractPath: String
)
