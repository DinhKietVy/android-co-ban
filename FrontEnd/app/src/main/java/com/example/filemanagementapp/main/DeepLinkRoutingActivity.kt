package com.example.filemanagementapp.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.filemanagementapp.BuildConfig
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import com.example.filemanagementapp.login.LoginActivity
import com.example.filemanagementapp.preview.FilePreviewActivity
import kotlinx.coroutines.launch

class DeepLinkRoutingActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var explorerRepository: ExplorerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reusing the layout for a simple spinner
        setContentView(R.layout.activity_public_link)

        progressBar = findViewById(R.id.publicLinkLoading)
        progressBar.visibility = View.VISIBLE
        findViewById<View>(R.id.publicLinkIcon).visibility = View.GONE
        findViewById<View>(R.id.publicLinkTitle).visibility = View.GONE
        findViewById<View>(R.id.publicLinkCloseButton).visibility = View.GONE
        findViewById<View>(R.id.publicLinkDownloadButton).visibility = View.GONE

        val apiService = com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.explorerApiService
        val gson = com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson
        explorerRepository = ExplorerRepository(this.applicationContext, apiService, gson)

        val action = intent.action
        val data = intent.data

        if (Intent.ACTION_VIEW == action && data != null) {
            val token = data.getQueryParameter("token")
            val owner = data.getQueryParameter("owner")
            val path = data.getQueryParameter("path")

            if (!token.isNullOrEmpty()) {
                handlePublicLink(token)
            } else if (!owner.isNullOrEmpty() && !path.isNullOrEmpty()) {
                handleInternalLink(owner, path)
            } else {
                showErrorAndFinish("Link không hợp lệ")
            }
        } else {
            finish()
        }
    }

    private fun handlePublicLink(token: String) {
        lifecycleScope.launch {
            val result = explorerRepository.getPublicLinkInfo(token)
            if (result.isSuccess) {
                val info = result.getOrNull()
                if (info != null) {
                    val previewUrl = "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/public-link/$token/download"
                    val ext = info.fileName.substringAfterLast('.', "").lowercase()
                    val type = if (ext.isNotEmpty()) ExplorerItem.Type.FILE else ExplorerItem.Type.FOLDER
                    
                    val item = ExplorerItem(
                        id = token,
                        name = info.fileName,
                        path = info.filePath,
                        type = type,
                        modified = "",
                        permission = "PUBLIC_READ",
                        previewUrl = previewUrl,
                        sizeBytes = info.size
                    )
                    
                    launchPreviewActivity(item)
                }
            } else {
                showErrorAndFinish("Link không tồn tại hoặc đã hết hạn")
            }
        }
    }

    private fun handleInternalLink(owner: String, path: String) {
        lifecycleScope.launch {
            val result = explorerRepository.getFileInfo(owner, path)
            if (result.isSuccess) {
                val info = result.getOrNull()
                if (info != null) {
                    val ext = info.fileName.substringAfterLast('.', "").lowercase()
                    val type = if (ext.isNotEmpty()) ExplorerItem.Type.FILE else ExplorerItem.Type.FOLDER
                    
                    val item = ExplorerItem(
                        id = "$owner:$path",
                        name = info.fileName,
                        path = info.filePath,
                        type = type,
                        modified = "",
                        permission = info.permission ?: "READ",
                        sizeBytes = info.size
                    )
                    
                    launchPreviewActivity(item)
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Bạn không có quyền truy cập file này hoặc file không tồn tại"
                if (errorMsg.contains("401") || errorMsg.contains("403") || errorMsg.contains("cập")) {
                    Toast.makeText(this@DeepLinkRoutingActivity, "Vui lòng đăng nhập để xem file này", Toast.LENGTH_LONG).show()
                    val loginIntent = Intent(this@DeepLinkRoutingActivity, LoginActivity::class.java)
                    startActivity(loginIntent)
                    finish()
                } else {
                    showErrorAndFinish(errorMsg)
                }
            }
        }
    }

    private fun launchPreviewActivity(item: ExplorerItem) {
        val intent = Intent(this, FilePreviewActivity::class.java).apply {
            putExtra(FilePreviewActivity.EXTRA_EXPLORER_ITEM, item)
        }
        startActivity(intent)
        finish()
    }

    private fun showErrorAndFinish(msg: String) {
        progressBar.visibility = View.GONE
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
