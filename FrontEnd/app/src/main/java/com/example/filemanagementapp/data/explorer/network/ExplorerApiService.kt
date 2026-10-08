package com.example.filemanagementapp.data.explorer.network

import com.example.filemanagementapp.data.explorer.model.ExplorerCreateFolderRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerDeleteFileRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerDeleteFolderRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerListRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerListResponse
import com.example.filemanagementapp.data.explorer.model.ExplorerMoveFileRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerMoveFolderRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerMutationResponse
import com.example.filemanagementapp.data.explorer.model.ExplorerRenameFileRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerRenameFolderRequest
import com.example.filemanagementapp.data.explorer.model.ExplorerUpdateFileRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

interface ExplorerApiService {
    @POST("api/data/list")
    suspend fun listDirectory(
        @Body request: ExplorerListRequest
    ): Response<ExplorerListResponse>

    @POST("api/data/folder")
    suspend fun createFolder(
        @Body request: ExplorerCreateFolderRequest
    ): Response<ExplorerMutationResponse>

    @POST("api/data/rename-file")
    suspend fun renameFile(
        @Body request: ExplorerRenameFileRequest
    ): Response<ExplorerMutationResponse>

    @POST("api/data/rename-folder")
    suspend fun renameFolder(
        @Body request: ExplorerRenameFolderRequest
    ): Response<ExplorerMutationResponse>

    @POST("api/data/move-file")
    suspend fun moveFile(
        @Body request: ExplorerMoveFileRequest
    ): Response<ExplorerMutationResponse>

    @POST("api/data/move-folder")
    suspend fun moveFolder(
        @Body request: ExplorerMoveFolderRequest
    ): Response<ExplorerMutationResponse>

    @HTTP(method = "DELETE", path = "api/data/file", hasBody = true)
    suspend fun deleteFile(
        @Body request: ExplorerDeleteFileRequest
    ): Response<ExplorerMutationResponse>

    @HTTP(method = "DELETE", path = "api/data/folder", hasBody = true)
    suspend fun deleteFolder(
        @Body request: ExplorerDeleteFolderRequest
    ): Response<ExplorerMutationResponse>

    @Multipart
    @POST("api/data/upload")
    suspend fun uploadFile(
        @Part("username") username: RequestBody,
        @Part("targetPath") targetPath: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ExplorerMutationResponse>

    @PUT("api/data/file-content")
    suspend fun updateFileContent(
        @Body request: ExplorerUpdateFileRequest
    ): Response<ExplorerMutationResponse>

    @POST("api/data/convert")
    suspend fun convertFile(
        @Body request: com.example.filemanagementapp.data.explorer.model.ExplorerConvertRequest
    ): Response<com.example.filemanagementapp.data.explorer.model.ExplorerConvertResponse>

    @POST("api/data/compress")
    suspend fun compressFiles(
        @Body request: com.example.filemanagementapp.data.explorer.model.ExplorerCompressRequest
    ): Response<com.example.filemanagementapp.data.explorer.model.ExplorerCompressResponse>

    @POST("api/data/extract")
    suspend fun extractFile(
        @Body request: com.example.filemanagementapp.data.explorer.model.ExplorerExtractRequest
    ): Response<com.example.filemanagementapp.data.explorer.model.ExplorerExtractResponse>

    @POST("api/data/share")
    suspend fun shareFile(
        @Body request: com.example.filemanagementapp.data.explorer.model.ExplorerShareRequest
    ): Response<ExplorerMutationResponse>

    @HTTP(method = "DELETE", path = "api/data/share", hasBody = true)
    suspend fun unshareFile(
        @Body request: com.example.filemanagementapp.data.explorer.model.ExplorerUnshareRequest
    ): Response<ExplorerMutationResponse>

    @retrofit2.http.GET("api/data/share/by-owner")
    suspend fun getSharedByOwner(
        @retrofit2.http.Query("ownerUsername") ownerUsername: String,
        @retrofit2.http.Query("filePath") filePath: String? = null
    ): Response<com.example.filemanagementapp.data.explorer.model.ExplorerSharedResponse>

    @retrofit2.http.GET("api/data/share/to-me")
    suspend fun getSharedToMe(
        @retrofit2.http.Query("targetUsername") targetUsername: String
    ): Response<com.example.filemanagementapp.data.explorer.model.ExplorerSharedResponse>

    @retrofit2.http.GET("api/data/file-info")
    suspend fun getFileInfo(
        @retrofit2.http.Query("owner") owner: String,
        @retrofit2.http.Query("path") path: String
    ): Response<com.example.filemanagementapp.data.explorer.model.PublicLinkInfoResponse>

    @POST("api/public-link/create")
    suspend fun createPublicLink(
        @Body request: com.example.filemanagementapp.data.explorer.model.PublicLinkCreateRequest
    ): Response<com.example.filemanagementapp.data.explorer.model.PublicLinkCreateResponse>

    @POST("api/public-link/delete")
    suspend fun deletePublicLink(
        @Body request: com.example.filemanagementapp.data.explorer.model.PublicLinkDeleteRequest
    ): Response<ExplorerMutationResponse>

    @retrofit2.http.GET("api/public-link/{token}/info")
    suspend fun getPublicLinkInfo(
        @retrofit2.http.Path("token") token: String
    ): Response<com.example.filemanagementapp.data.explorer.model.PublicLinkInfoResponse>
}
