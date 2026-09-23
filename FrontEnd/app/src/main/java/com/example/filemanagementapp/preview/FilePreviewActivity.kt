package com.example.filemanagementapp.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.example.filemanagementapp.R
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.download.ExplorerDownloadService
import com.example.filemanagementapp.explorer.ExplorerItem
import com.example.filemanagementapp.explorer.FolderPickerAdapter
import com.example.filemanagementapp.explorer.FolderPickerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilePreviewActivity : AppCompatActivity() {
    private lateinit var scrimView: View
    private lateinit var bottomSheet: View
    private lateinit var fileInfoContent: View
    private lateinit var aiAnalysisContent: View
    private lateinit var sheetHeaderIcon: ImageView
    private lateinit var sheetTitleText: TextView
    private lateinit var fileInfoTab: LinearLayout
    private lateinit var aiAnalysisTab: LinearLayout
    private lateinit var aiAnalysisIconBg: View
    private lateinit var aiAnalysisIcon: ImageView
    private lateinit var aiAnalysisLabel: TextView
    private lateinit var objectTagsTitle: TextView
    private lateinit var titleText: TextView
    private lateinit var previewImage: ImageView
    private lateinit var previewVideo: PlayerView
    private lateinit var previewPdfRecycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var previewDocxWebView: android.webkit.WebView
    private lateinit var previewUnsupported: View
    private lateinit var previewLoadingIndicator: ProgressBar
    private lateinit var openExternallyButton: com.google.android.material.button.MaterialButton
    private lateinit var previewInfoNameValue: TextView
    private lateinit var previewInfoTypeValue: TextView
    private lateinit var previewInfoSizeValue: TextView
    private lateinit var previewInfoUploadedValue: TextView
    private lateinit var previewInfoModifiedValue: TextView
    private lateinit var showProcessedImageSwitch: androidx.appcompat.widget.SwitchCompat
    
    private lateinit var previewActionHandler: PreviewActionHandler
    private lateinit var previewRenderHelper: PreviewRenderHelper

    private lateinit var explorerRepository: ExplorerRepository
    private var explorerItem: ExplorerItem? = null
    private var username: String = ""

    private val ucropLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val resultUri = com.yalantis.ucrop.UCrop.getOutput(result.data!!)
            if (resultUri != null) {
                handleCroppedImage(resultUri)
            }
        } else if (result.resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR && result.data != null) {
            val cropError = com.yalantis.ucrop.UCrop.getError(result.data!!)
            android.widget.Toast.makeText(this, "Lỗi khi cắt ảnh: ${cropError?.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private val photoEditorLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val resultUri = result.data?.getParcelableExtra<Uri>(PhotoEditorActivity.EXTRA_OUTPUT_URI)
            if (resultUri != null) {
                handleCroppedImage(resultUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        explorerItem = intent.getSerializableExtra(EXTRA_EXPLORER_ITEM) as? ExplorerItem
        username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        
        explorerRepository = ExplorerRepository(
            appContext = applicationContext,
            explorerApiService = ExplorerNetworkModule.explorerApiService,
            gson = ExplorerNetworkModule.gson
        )
        enableEdgeToEdge()
        setContentView(R.layout.activity_file_preview)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        
        previewActionHandler = PreviewActionHandler(
            activity = this,
            username = username,
            explorerRepository = explorerRepository,
            cacheDir = cacheDir,
            onActionComplete = { resultCode, data ->
                if (data != null) {
                    setResult(resultCode, data)
                } else {
                    setResult(resultCode)
                }
                if (resultCode == RESULT_OK || resultCode == RESULT_ACTION_FAVORITE || resultCode == RESULT_ACTION_AI) {
                    finish()
                }
            }
        )

        val previewUrl = intent.getStringExtra(EXTRA_PREVIEW_URL)
        val analyzedImagePath = intent.getStringExtra(EXTRA_ANALYZED_IMAGE_PATH)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        val ocrText = intent.getStringExtra(EXTRA_OCR_TEXT).orEmpty()
        val aiTags = intent.getStringArrayListExtra(EXTRA_AI_TAGS).orEmpty()

        previewRenderHelper = PreviewRenderHelper(
            activity = this,
            item = explorerItem,
            previewUrl = previewUrl,
            analyzedImagePath = analyzedImagePath,
            fileName = fileName,
            ocrText = ocrText,
            aiTags = aiTags
        )

        val appDatabase = com.example.filemanagementapp.data.local.AppDatabase.getInstance(applicationContext)
        val aiAnalysisLocalRepository = com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository(
            appDatabase.aiAnalysisCacheDao(),
            ExplorerNetworkModule.gson
        )

        previewRenderHelper.setup(
            onOpenExternally = { view ->
                explorerItem?.let { previewActionHandler.openExternally(it, previewRenderHelper.previewLoadingIndicator) }
            },
            onExtract = {
                explorerItem?.let {
                    setResult(RESULT_ACTION_EXTRACT, Intent().putExtra(EXTRA_EXPLORER_ITEM, it))
                    finish()
                }
            },
            onAiDataChanged = { newOcrText, newTags ->
                explorerItem?.let { item ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val path = item.path
                        val existing = aiAnalysisLocalRepository.getAnalysisByPaths(username, listOf(path))[path]
                        if (existing != null) {
                            val updated = existing.copy(
                                ocrText = newOcrText,
                                tags = newTags
                            )
                            aiAnalysisLocalRepository.upsertAnalysis(updated)
                        } else {
                            val newRecord = com.example.filemanagementapp.data.local.ai.AiAnalysisCache(
                                username = username,
                                filePath = path,
                                status = com.example.filemanagementapp.data.local.ai.AiAnalysisStatus.COMPLETED,
                                tags = newTags,
                                ocrText = newOcrText,
                                previewImagePath = analyzedImagePath
                            )
                            aiAnalysisLocalRepository.upsertAnalysis(newRecord)
                        }
                    }
                }
            },
            onEditImage = { action ->
                val previewUrl = intent.getStringExtra(EXTRA_PREVIEW_URL)
                if (previewUrl != null) {
                    downloadAndEditImage(previewUrl, action)
                }
            },
            onSaveTextContent = { content ->
                updateFileContent(content)
            },
            onConvertPdfRequested = {
                convertFileToPdf()
            }
        )

        setupActions()
        if (intent.getBooleanExtra(EXTRA_SHOW_AI_PANEL, false)) {
            showPanel(Panel.AI_ANALYSIS)
        } else {
            hidePanel()
        }
    }

    private fun convertFileToPdf() {
        val item = explorerItem ?: return
        previewRenderHelper.previewLoadingIndicator.visibility = View.VISIBLE
        lifecycleScope.launch {
            explorerRepository.convertFile(
                username = username,
                filePath = item.path,
                targetFormat = "pdf"
            ).onSuccess { response ->
                android.widget.Toast.makeText(this@FilePreviewActivity, response.message ?: "Chuyển đổi thành công!", android.widget.Toast.LENGTH_SHORT).show()
                val convertedPath = response.data?.convertedFilePath
                if (convertedPath != null) {
                    // Update URL and reload preview
                    val newPreviewUrl = "${com.example.filemanagementapp.BuildConfig.API_BASE_URL}api/data/download?path=${Uri.encode(convertedPath)}&username=$username"
                    val newFileName = java.io.File(convertedPath).name
                    
                    previewRenderHelper = PreviewRenderHelper(
                        activity = this@FilePreviewActivity,
                        item = item, // keep original item for other actions, or null
                        previewUrl = newPreviewUrl,
                        analyzedImagePath = null,
                        fileName = newFileName,
                        ocrText = "",
                        aiTags = emptyList()
                    )
                    
                    previewRenderHelper.setup(
                        onOpenExternally = { view ->
                            explorerItem?.let { previewActionHandler.openExternally(it, previewRenderHelper.previewLoadingIndicator) }
                        },
                        onExtract = {},
                        onAiDataChanged = { _, _ -> },
                        onEditImage = {},
                        onSaveTextContent = {},
                        onConvertPdfRequested = {}
                    )
                }
            }.onFailure {
                previewRenderHelper.previewLoadingIndicator.visibility = View.GONE
                android.widget.Toast.makeText(this@FilePreviewActivity, "Lỗi chuyển đổi: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindViews() {
        scrimView = findViewById(R.id.scrimView)
        bottomSheet = findViewById(R.id.bottomSheet)
        fileInfoContent = findViewById(R.id.fileInfoContent)
        aiAnalysisContent = findViewById(R.id.aiAnalysisContent)
        sheetHeaderIcon = findViewById(R.id.sheetHeaderIcon)
        sheetTitleText = findViewById(R.id.sheetTitleText)
        fileInfoTab = findViewById(R.id.fileInfoTab)
        aiAnalysisTab = findViewById(R.id.aiAnalysisTab)
        aiAnalysisIconBg = findViewById(R.id.aiAnalysisIconBg)
        aiAnalysisIcon = findViewById(R.id.aiAnalysisIcon)
        aiAnalysisLabel = findViewById(R.id.aiAnalysisLabel)
        objectTagsTitle = findViewById(R.id.objectTagsTitle)
        titleText = findViewById(R.id.titleText)
        previewImage = findViewById(R.id.previewImage)
        previewVideo = findViewById(R.id.previewVideo)
        previewPdfRecycler = findViewById(R.id.previewPdfRecycler)
        previewDocxWebView = findViewById(R.id.previewDocxWebView)
        previewUnsupported = findViewById(R.id.previewUnsupported)
        previewLoadingIndicator = findViewById(R.id.previewLoadingIndicator)
        openExternallyButton = findViewById(R.id.openExternallyButton)
        previewInfoNameValue = findViewById(R.id.previewInfoNameValue)
        previewInfoTypeValue = findViewById(R.id.previewInfoTypeValue)
        previewInfoSizeValue = findViewById(R.id.previewInfoSizeValue)
        previewInfoUploadedValue = findViewById(R.id.previewInfoUploadedValue)
        previewInfoModifiedValue = findViewById(R.id.previewInfoModifiedValue)
        showProcessedImageSwitch = findViewById(R.id.showProcessedImageSwitch)
    }

    // Rendering logic moved to PreviewRenderHelper

    private fun setupActions() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.closeSheetButton).setOnClickListener { hidePanel() }
        scrimView.setOnClickListener { hidePanel() }
        fileInfoTab.setOnClickListener { togglePanel(Panel.FILE_INFO) }
        aiAnalysisTab.setOnClickListener { togglePanel(Panel.AI_ANALYSIS) }
        findViewById<ImageButton>(R.id.moreButton).setOnClickListener { anchor ->
            showOverflowMenu(anchor)
        }
        
        val isPublicRead = explorerItem?.permission == "PUBLIC_READ"
        if (isPublicRead) {
            aiAnalysisTab.visibility = View.GONE
            findViewById<View>(R.id.editFab)?.visibility = View.GONE
        }
        
        findViewById<View>(R.id.copyButton).setOnClickListener {
            val ocrText = intent.getStringExtra(EXTRA_OCR_TEXT).orEmpty()
            if (ocrText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Extracted Text", ocrText)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, R.string.preview_copy_success, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePanel(panel: Panel) {
        if (bottomSheet.visibility == View.VISIBLE && currentPanel == panel) {
            hidePanel()
        } else {
            showPanel(panel)
        }
    }

    private var currentPanel: Panel? = null
    
    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != ACTION_MEDIA_CONTROL) return
            val controlType = intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)
            when (controlType) {
                CONTROL_TYPE_PLAY -> {
                    if (::previewRenderHelper.isInitialized) {
                        previewRenderHelper.exoPlayer?.play()
                    }
                }
                CONTROL_TYPE_PAUSE -> {
                    if (::previewRenderHelper.isInitialized) {
                        previewRenderHelper.exoPlayer?.pause()
                    }
                }
                CONTROL_TYPE_REPLAY_10 -> {
                    if (::previewRenderHelper.isInitialized) {
                        previewRenderHelper.exoPlayer?.let { player ->
                            val currentPos = player.currentPosition
                            player.seekTo(maxOf(0, currentPos - 10000))
                        }
                    }
                }
                CONTROL_TYPE_FORWARD_10 -> {
                    if (::previewRenderHelper.isInitialized) {
                        previewRenderHelper.exoPlayer?.let { player ->
                            val currentPos = player.currentPosition
                            val duration = player.duration
                            if (duration > 0) {
                                player.seekTo(minOf(duration, currentPos + 10000))
                            } else {
                                player.seekTo(currentPos + 10000)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter(ACTION_MEDIA_CONTROL)
        androidx.core.content.ContextCompat.registerReceiver(this, pipReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipReceiver)
    }

    private fun showPanel(panel: Panel) {
        currentPanel = panel
        scrimView.visibility = View.VISIBLE
        scrimView.alpha = 1f
        bottomSheet.visibility = View.VISIBLE
        if (panel == Panel.FILE_INFO) {
            fileInfoContent.visibility = View.VISIBLE
            aiAnalysisContent.visibility = View.GONE
            sheetHeaderIcon.setImageResource(R.drawable.info)
            sheetHeaderIcon.imageTintList = getColorStateList(R.color.preview_primary)
            sheetTitleText.setText(R.string.preview_sheet_file_info)
        } else {
            fileInfoContent.visibility = View.GONE
            aiAnalysisContent.visibility = View.VISIBLE
            sheetHeaderIcon.setImageResource(R.drawable.sparkles)
            sheetHeaderIcon.imageTintList = getColorStateList(R.color.preview_primary)
            sheetTitleText.setText(R.string.preview_sheet_ai)
        }
        updateFooterState(panel)
    }

    private fun hidePanel() {
        currentPanel = null
        scrimView.visibility = View.GONE
        bottomSheet.visibility = View.GONE
        updateFooterState(null)
    }

    private fun updateFooterState(panel: Panel?) {
        val selectedBg = getDrawable(R.drawable.explorer_tag_background)
        fileInfoTab.getChildAt(0).background = if (panel == Panel.FILE_INFO) selectedBg else null
        aiAnalysisIconBg.background = if (panel == Panel.AI_ANALYSIS) selectedBg else null
        aiAnalysisIcon.imageTintList = getColorStateList(
            if (panel == Panel.AI_ANALYSIS) R.color.preview_primary else R.color.preview_text_secondary
        )
        aiAnalysisLabel.setTextColor(
            getColor(if (panel == Panel.AI_ANALYSIS) R.color.preview_primary else R.color.preview_text_secondary)
        )
    }

    private fun showOverflowMenu(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_preview_actions, null)
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12f
        }

        val item = explorerItem
        if (item != null) {
            val menuFavoriteLabel = popupView.findViewById<android.widget.TextView>(R.id.menuFavoriteLabel)
            menuFavoriteLabel.setText(if (item.isFavorite) R.string.preview_action_unfavorite else R.string.preview_action_favorite)
            
            val menuAiLabel = popupView.findViewById<android.widget.TextView>(R.id.menuAiLabel)
            menuAiLabel.setText(if (item.aiAnalyzed) R.string.preview_action_reanalyze_ai else R.string.preview_action_ai)
            
            val extension = item.name.substringAfterLast('.', "").lowercase()
            val isImage = extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
            val visibility = if (isImage) View.VISIBLE else View.GONE
            popupView.findViewById<View>(R.id.menuAi).visibility = visibility
            popupView.findViewById<View>(R.id.menuAiDivider)?.visibility = visibility
            
            if (item.permission == "PUBLIC_READ" || item.permission == "READ") {
                popupView.findViewById<View>(R.id.menuRename).visibility = View.GONE
                popupView.findViewById<View>(R.id.menuMove).visibility = View.GONE
                popupView.findViewById<View>(R.id.menuDelete).visibility = View.GONE
            }
            if (item.permission == "PUBLIC_READ") {
                popupView.findViewById<View>(R.id.menuShare).visibility = View.GONE
                popupView.findViewById<View>(R.id.menuFavorite).visibility = View.GONE
                popupView.findViewById<View>(R.id.menuAi).visibility = View.GONE
                popupView.findViewById<View>(R.id.menuAiDivider)?.visibility = View.GONE
            }
        }

        popupView.findViewById<View>(R.id.menuDownload).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("download")
        }
        popupView.findViewById<View>(R.id.menuShare).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("share")
        }
        popupView.findViewById<View>(R.id.menuOpenExternally)?.setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("open_externally")
        }
        popupView.findViewById<View>(R.id.menuRename).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("rename")
        }
        popupView.findViewById<View>(R.id.menuMove).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("move")
        }
        popupView.findViewById<View>(R.id.menuFavorite).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("favorite")
        }
        popupView.findViewById<View>(R.id.menuAi).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("ai")
        }
        popupView.findViewById<View>(R.id.menuDelete).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("delete")
        }

        popupWindow.showAsDropDown(anchor, -24, 8, android.view.Gravity.END)
    }

    private fun handleActionClick(action: String) {
        val item = explorerItem ?: return
        when (action) {
            "download" -> previewActionHandler.download(item)
            "share" -> previewActionHandler.share(item)
            "open_externally" -> previewActionHandler.openExternally(item, previewRenderHelper.previewLoadingIndicator)
            "rename" -> previewActionHandler.rename(item)
            "move" -> previewActionHandler.move(item)
            "favorite" -> {
                setResult(RESULT_ACTION_FAVORITE, Intent().putExtra(EXTRA_EXPLORER_ITEM, item))
                finish()
            }
            "delete" -> previewActionHandler.delete(item)
            "ai" -> {
                setResult(RESULT_ACTION_AI, Intent().putExtra(EXTRA_EXPLORER_ITEM, item))
                finish()
            }
        }
    }

    private enum class Panel {
        FILE_INFO,
        AI_ANALYSIS
    }


    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        val linearLayout = findViewById<LinearLayout>(R.id.previewImage).parent.parent.parent as? LinearLayout // the one with paddingBottom
        val cardView = findViewById<View>(R.id.previewImage).parent.parent as? com.google.android.material.card.MaterialCardView

        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            findViewById<View>(R.id.topAppBar).visibility = View.GONE
            findViewById<View>(R.id.footerBar).visibility = View.GONE
            
            // Adjust layout for fullscreen
            linearLayout?.setPadding(0, 0, 0, 0)
            cardView?.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            cardView?.strokeWidth = 0

            // Fullscreen mode
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
                window.insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
        } else {
            findViewById<View>(R.id.topAppBar).visibility = View.VISIBLE
            findViewById<View>(R.id.footerBar).visibility = View.VISIBLE
            
            // Restore layout
            val dp120 = (120 * resources.displayMetrics.density).toInt()
            linearLayout?.setPadding(0, 0, 0, dp120)
            cardView?.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            val dp1 = (1 * resources.displayMetrics.density).toInt()
            cardView?.strokeWidth = dp1

            // Exit fullscreen
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::previewRenderHelper.isInitialized) {
            previewRenderHelper.exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::previewRenderHelper.isInitialized) {
            previewRenderHelper.cleanUp()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val extension = explorerItem?.name?.substringAfterLast('.', "")?.lowercase()
        val isVideo = extension in listOf("mp4", "mkv", "webm", "avi")
        val isAudio = extension in listOf("mp3", "wav", "ogg", "m4a")
        
        // If it's a video or audio, enter PiP automatically when user goes to home
        if (isVideo || isAudio) {
            val params = android.app.PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        
        val visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        
        findViewById<View>(R.id.topAppBar)?.visibility = visibility
        findViewById<View>(R.id.footerBar)?.visibility = visibility
        
        val extension = explorerItem?.name?.substringAfterLast('.', "")?.lowercase()
        val isImage = extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val isText = extension in listOf("txt", "json", "xml", "md", "csv", "kt", "java", "py", "html", "css", "js", "sh")
        
        findViewById<View>(R.id.editFab)?.visibility = if (isInPictureInPictureMode) {
            View.GONE
        } else {
            if (isImage || isText) View.VISIBLE else View.GONE
        }
        
        if (isInPictureInPictureMode) {
            hidePanel()
            if (::previewRenderHelper.isInitialized) {
                previewRenderHelper.previewVideo.useController = false
            }
        } else {
            if (::previewRenderHelper.isInitialized) {
                previewRenderHelper.previewVideo.useController = true
            }
        }
    }

    fun updatePictureInPictureActions(isPlaying: Boolean) {
        val replayIntent = Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_REPLAY_10)
        val replayPendingIntent = android.app.PendingIntent.getBroadcast(
            this, CONTROL_TYPE_REPLAY_10, replayIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val replayIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_replay_10)
        val replayAction = android.app.RemoteAction(replayIcon, "Replay 10s", "Replay 10s", replayPendingIntent)
        
        val playPauseIconId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseControlType = if (isPlaying) CONTROL_TYPE_PAUSE else CONTROL_TYPE_PLAY
        
        val playPauseIntent = Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, playPauseControlType)
        val playPausePendingIntent = android.app.PendingIntent.getBroadcast(
            this, playPauseControlType, playPauseIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = android.graphics.drawable.Icon.createWithResource(this, playPauseIconId)
        val playPauseAction = android.app.RemoteAction(playPauseIcon, playPauseTitle, playPauseTitle, playPausePendingIntent)
        
        val forwardIntent = Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_FORWARD_10)
        val forwardPendingIntent = android.app.PendingIntent.getBroadcast(
            this, CONTROL_TYPE_FORWARD_10, forwardIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val forwardIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_forward_10)
        val forwardAction = android.app.RemoteAction(forwardIcon, "Forward 10s", "Forward 10s", forwardPendingIntent)
        
        try {
            val params = android.app.PictureInPictureParams.Builder()
                .setActions(listOf(replayAction, playPauseAction, forwardAction))
                .build()
            setPictureInPictureParams(params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateFileContent(content: String) {
        val item = explorerItem ?: return
        val username = intent.getStringExtra("extra_username") ?: return
        
        previewRenderHelper.previewLoadingIndicator.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = explorerRepository.updateFileContent(username, item.path, content)
            previewRenderHelper.previewLoadingIndicator.visibility = View.GONE
            if (result.isSuccess) {
                android.widget.Toast.makeText(this@FilePreviewActivity, "Đã lưu thành công", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this@FilePreviewActivity, "Lỗi khi lưu: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_MEDIA_CONTROL = "media_control"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val CONTROL_TYPE_PLAY = 1
        const val CONTROL_TYPE_PAUSE = 2
        const val CONTROL_TYPE_REPLAY_10 = 3
        const val CONTROL_TYPE_FORWARD_10 = 4
        
        const val RESULT_ACTION_FAVORITE = 101
        const val RESULT_ACTION_AI = 102
        const val RESULT_ACTION_EXTRACT = 103
        const val EXTRA_EXPLORER_ITEM = "extra_explorer_item"
        private const val EXTRA_USERNAME = "extra_username"
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_PREVIEW_URL = "extra_preview_url"
        private const val EXTRA_ANALYZED_IMAGE_PATH = "extra_analyzed_image_path"
        private const val EXTRA_OCR_TEXT = "extra_ocr_text"
        private const val EXTRA_AI_TAGS = "extra_ai_tags"
        private const val EXTRA_SHOW_AI_PANEL = "extra_show_ai_panel"

        fun newIntent(
            context: Context,
            item: ExplorerItem,
            username: String,
            analyzedImagePath: String?,
            ocrText: String?,
            aiTags: List<String>,
            showAiPanel: Boolean = false
        ): Intent {
            val extension = item.name.substringAfterLast('.', "").trim().uppercase()
            val filteredAiTags = aiTags.filter { 
                it.isNotBlank() && 
                !it.equals(extension, ignoreCase = true) &&
                !it.equals("AI analyzed", ignoreCase = true) &&
                !it.equals("Đã phân tích bởi AI", ignoreCase = true)
            }
            return Intent(context, FilePreviewActivity::class.java).apply {
                putExtra(EXTRA_EXPLORER_ITEM, item)
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_FILE_NAME, item.name)
                putExtra(EXTRA_PREVIEW_URL, item.previewUrl)
                putExtra(EXTRA_ANALYZED_IMAGE_PATH, analyzedImagePath)
                putExtra(EXTRA_OCR_TEXT, ocrText)
                putStringArrayListExtra(EXTRA_AI_TAGS, ArrayList(filteredAiTags))
                putExtra(EXTRA_SHOW_AI_PANEL, showAiPanel)
            }
        }
    }

    private fun downloadAndEditImage(url: String, action: String) {
        previewRenderHelper.previewLoadingIndicator.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("ngrok-skip-browser-warning", "69420")
                    .build()
                val response = ExplorerNetworkModule.okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                
                val sourceFile = File(cacheDir, "temp_edit_original.jpg")
                val destFile = File(cacheDir, "temp_edit_result.jpg")
                
                val bytes = response.body?.bytes() ?: throw Exception("Empty body")
                sourceFile.writeBytes(bytes)
                
                withContext(Dispatchers.Main) {
                    previewRenderHelper.previewLoadingIndicator.visibility = View.GONE
                    
                    if (action == "crop") {
                        val options = com.yalantis.ucrop.UCrop.Options()
                        options.setCompressionQuality(90)
                        options.setHideBottomControls(false)
                        options.setFreeStyleCropEnabled(true)
                        
                        val ucropIntent = com.yalantis.ucrop.UCrop.of(Uri.fromFile(sourceFile), Uri.fromFile(destFile))
                            .withOptions(options)
                            .getIntent(this@FilePreviewActivity)
                            
                        ucropLauncher.launch(ucropIntent)
                    } else if (action == "draw") {
                        val editorIntent = Intent(this@FilePreviewActivity, PhotoEditorActivity::class.java).apply {
                            putExtra(PhotoEditorActivity.EXTRA_SOURCE_URI, Uri.fromFile(sourceFile))
                            putExtra(PhotoEditorActivity.EXTRA_DEST_URI, Uri.fromFile(destFile))
                        }
                        photoEditorLauncher.launch(editorIntent)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    previewRenderHelper.previewLoadingIndicator.visibility = View.GONE
                    android.widget.Toast.makeText(this@FilePreviewActivity, "Không thể tải ảnh: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleCroppedImage(uri: Uri) {
        val item = explorerItem ?: return
        previewRenderHelper.previewLoadingIndicator.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val targetPath = item.path.substringBeforeLast('/', "")
                val result = explorerRepository.uploadFile(
                    username = username,
                    targetPath = targetPath,
                    fileUri = uri,
                    overrideFileName = item.name
                )
                
                withContext(Dispatchers.Main) {
                    previewRenderHelper.previewLoadingIndicator.visibility = View.GONE
                    if (result.isSuccess) {
                        android.widget.Toast.makeText(this@FilePreviewActivity, "Đã lưu đè ảnh thành công", android.widget.Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        android.widget.Toast.makeText(this@FilePreviewActivity, "Lỗi khi lưu: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    previewRenderHelper.previewLoadingIndicator.visibility = View.GONE
                    android.widget.Toast.makeText(this@FilePreviewActivity, "Lỗi khi upload: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
