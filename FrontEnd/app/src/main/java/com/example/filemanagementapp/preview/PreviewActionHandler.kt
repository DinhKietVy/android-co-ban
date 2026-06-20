package com.example.filemanagementapp.preview

import android.content.Intent
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.download.ExplorerDownloadService
import com.example.filemanagementapp.explorer.ExplorerItem
import com.example.filemanagementapp.explorer.FolderPickerAdapter
import com.example.filemanagementapp.explorer.FolderPickerEntry
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewActionHandler(
    private val activity: AppCompatActivity,
    private val username: String,
    private val explorerRepository: ExplorerRepository,
    private val cacheDir: java.io.File,
    private val onActionComplete: (Int, Intent?) -> Unit
) {

    fun download(item: ExplorerItem) {
        ExplorerDownloadService.start(activity, item.name, item.previewUrl.orEmpty())
        android.widget.Toast.makeText(activity, R.string.explorer_download_started, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun share(item: ExplorerItem) {
        activity.lifecycleScope.launch {
            try {
                val localFile = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    item.name
                )
                val fileToShare = if (localFile.exists()) {
                    localFile
                } else {
                    withContext(Dispatchers.IO) {
                        val request = okhttp3.Request.Builder().url(item.previewUrl!!).build()
                        val response = ExplorerNetworkModule.okHttpClient.newCall(request).execute()
                        val bytes = response.body?.bytes() ?: throw Exception("Failed to download")
                        val cacheFile = java.io.File(cacheDir, item.name)
                        cacheFile.writeBytes(bytes)
                        cacheFile
                    }
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    fileToShare
                )
                val mimeType = java.net.URLConnection.guessContentTypeFromName(item.name) ?: "*/*"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.preview_action_share)))
            } catch (e: Exception) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.previewUrl)
                }
                activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.preview_action_share)))
            }
        }
    }

    fun openExternally(item: ExplorerItem, previewLoadingIndicator: View) {
        val previewUrl = item.previewUrl ?: return
        previewLoadingIndicator.visibility = View.VISIBLE
        activity.lifecycleScope.launch {
            try {
                val fileBytes = withContext(Dispatchers.IO) {
                    val request = okhttp3.Request.Builder()
                        .url(previewUrl)
                        .addHeader("ngrok-skip-browser-warning", "69420")
                        .build()
                    val response = ExplorerNetworkModule.okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.bytes() ?: throw Exception("Empty response")
                }

                val tempFile = java.io.File(cacheDir, "external_open_${item.name}")
                withContext(Dispatchers.IO) {
                    tempFile.writeBytes(fileBytes)
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    tempFile
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val mimeType = activity.contentResolver.getType(uri) ?: "*/*"
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                withContext(Dispatchers.Main) {
                    try {
                        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.preview_open_externally)))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(activity, "No app found to open this file.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(activity, "Error downloading file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    previewLoadingIndicator.visibility = View.GONE
                }
            }
        }
    }

    fun rename(item: ExplorerItem) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_input, null)
        val inputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialogInputLayout)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialogInputEditText)

        inputLayout.hint = activity.getString(R.string.explorer_dialog_rename_hint)
        val isFile = item.type == ExplorerItem.Type.FILE
        val nameWithoutExt = if (isFile) item.name.substringBeforeLast('.', item.name) else item.name
        val extension = if (isFile && item.name.contains('.')) "." + item.name.substringAfterLast('.', "") else ""

        input.setText(nameWithoutExt)
        input.setSelection(nameWithoutExt.length)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.explorer_dialog_rename_title)
            .setView(view)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                val newNameInput = input.text?.toString().orEmpty()
                if (newNameInput.isBlank()) return@setPositiveButton
                val newName = newNameInput + extension
                activity.lifecycleScope.launch {
                    explorerRepository.renameItem(username, item, newName)
                        .onSuccess {
                            val appDatabase = com.example.filemanagementapp.data.local.AppDatabase.getInstance(activity)
                            val parentFolder = item.path.substringBeforeLast('/', "")
                            appDatabase.directoryCacheDao().deleteByFolder(username, parentFolder)

                            val newPath = if (parentFolder.isEmpty()) newName else "$parentFolder/$newName"
                            val isFolder = item.type == ExplorerItem.Type.FOLDER

                            val favoriteLocalRepository = com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository(appDatabase.favoriteItemDao())
                            favoriteLocalRepository.updatePath(username, item.path, newPath, isFolder)

                            val aiAnalysisLocalRepository = com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository(appDatabase.aiAnalysisCacheDao(), com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson)
                            aiAnalysisLocalRepository.updatePath(username, item.path, newPath, isFolder)

                            onActionComplete(AppCompatActivity.RESULT_OK, null)
                        }
                        .onFailure { throwable ->
                            android.widget.Toast.makeText(activity, throwable.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .show()
    }

    fun delete(item: ExplorerItem) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.explorer_dialog_delete_title)
            .setMessage(activity.getString(R.string.explorer_dialog_delete_message, item.name))
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                activity.lifecycleScope.launch {
                    explorerRepository.deleteItem(username, item)
                        .onSuccess {
                            onActionComplete(AppCompatActivity.RESULT_OK, null)
                        }
                        .onFailure { throwable ->
                            android.widget.Toast.makeText(activity, throwable.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .show()
    }

    fun move(item: ExplorerItem) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_folder_picker, null)
        val pathText = dialogView.findViewById<TextView>(R.id.folderPickerPathText)
        val upButton = dialogView.findViewById<TextView>(R.id.folderPickerUpButton)
        val loadingView = dialogView.findViewById<ProgressBar>(R.id.folderPickerLoading)
        val emptyView = dialogView.findViewById<TextView>(R.id.folderPickerEmptyText)
        val invalidHintText = dialogView.findViewById<TextView>(R.id.folderPickerInvalidHintText)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderPickerRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(activity)

        var currentPath = ""
        var loadJob: Job? = null
        lateinit var loadFolderPickerPath: (String) -> Unit
        var positiveButton: Button? = null
        val adapter = FolderPickerAdapter { folder -> loadFolderPickerPath(folder.path) }
        recyclerView.adapter = adapter

        fun renderFolderPickerPath() {
            pathText.text = if (currentPath.isBlank()) {
                activity.getString(R.string.explorer_folder_picker_current_root)
            } else {
                activity.getString(R.string.explorer_folder_picker_current, currentPath)
            }
            upButton.visibility = if (currentPath.isBlank()) View.INVISIBLE else View.VISIBLE
        }

        fun isInvalidTarget(target: String): Boolean {
            val normalizedTarget = target.trim('/').lowercase()
            val sourcePath = item.path.trim('/').lowercase()
            return normalizedTarget == sourcePath || (normalizedTarget.isNotBlank() && normalizedTarget.startsWith("$sourcePath/"))
        }

        loadFolderPickerPath = { targetPath ->
            currentPath = targetPath
            renderFolderPickerPath()
            val invalidTarget = isInvalidTarget(targetPath)
            invalidHintText.visibility = if (invalidTarget) View.VISIBLE else View.GONE
            positiveButton?.isEnabled = !invalidTarget
            loadJob?.cancel()
            loadJob = activity.lifecycleScope.launch {
                loadingView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                recyclerView.alpha = 0.5f
                explorerRepository.listDirectory(username = username, folderPath = targetPath)
                    .onSuccess { directory ->
                        val folders = directory.items
                            .filter { it.type == ExplorerItem.Type.FOLDER }
                            .map { folder ->
                                FolderPickerEntry(
                                    item = folder,
                                    isEnabled = !isInvalidTarget(folder.path)
                                )
                            }
                        adapter.submitItems(folders)
                        emptyView.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
                        emptyView.text = activity.getString(R.string.explorer_folder_picker_empty)
                    }
                    .onFailure {
                        adapter.submitItems(emptyList())
                        emptyView.visibility = View.VISIBLE
                        emptyView.text = activity.getString(R.string.explorer_folder_picker_loading_error)
                    }
                loadingView.visibility = View.GONE
                recyclerView.alpha = 1f
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.explorer_dialog_move_title)
            .setView(dialogView)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_folder_picker_move_here) { _, _ ->
                activity.lifecycleScope.launch {
                    explorerRepository.moveItem(username, item, currentPath)
                        .onSuccess {
                            val appDatabase = com.example.filemanagementapp.data.local.AppDatabase.getInstance(activity)
                            val parentFolder = item.path.substringBeforeLast('/', "")
                            appDatabase.directoryCacheDao().deleteByFolder(username, parentFolder)

                            val normalizedTarget = currentPath.trim().trim('/')
                            val itemName = item.name
                            val newPath = if (normalizedTarget.isEmpty()) itemName else "$normalizedTarget/$itemName"
                            val isFolder = item.type == ExplorerItem.Type.FOLDER

                            val favoriteLocalRepository = com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository(appDatabase.favoriteItemDao())
                            favoriteLocalRepository.updatePath(username, item.path, newPath, isFolder)

                            val aiAnalysisLocalRepository = com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository(appDatabase.aiAnalysisCacheDao(), com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson)
                            aiAnalysisLocalRepository.updatePath(username, item.path, newPath, isFolder)

                            onActionComplete(AppCompatActivity.RESULT_OK, null)
                        }
                        .onFailure { throwable ->
                            android.widget.Toast.makeText(activity, throwable.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .show()

        positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)

        upButton.setOnClickListener {
            if (currentPath.isBlank()) return@setOnClickListener
            loadFolderPickerPath(currentPath.substringBeforeLast('/', ""))
        }

        dialog.setOnDismissListener { loadJob?.cancel() }

        loadFolderPickerPath(currentPath)
    }
}
