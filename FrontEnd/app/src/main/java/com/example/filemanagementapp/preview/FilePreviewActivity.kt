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
    private lateinit var previewVideo: VideoView
    private lateinit var previewTextScroll: ScrollView
    private lateinit var previewText: TextView
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
            }
        )

        setupActions()
        if (intent.getBooleanExtra(EXTRA_SHOW_AI_PANEL, false)) {
            showPanel(Panel.AI_ANALYSIS)
        } else {
            hidePanel()
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
        previewTextScroll = findViewById(R.id.previewTextScroll)
        previewText = findViewById(R.id.previewText)
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
        }

        popupView.findViewById<View>(R.id.menuDownload).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("download")
        }
        popupView.findViewById<View>(R.id.menuShare).setOnClickListener {
            popupWindow.dismiss()
            handleActionClick("share")
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

    override fun onDestroy() {
        super.onDestroy()
        if (::previewRenderHelper.isInitialized) {
            previewRenderHelper.cleanUp()
        }
    }

    companion object {
        const val RESULT_ACTION_FAVORITE = 101
        const val RESULT_ACTION_AI = 102
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
}
