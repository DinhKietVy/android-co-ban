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
    private lateinit var previewUnsupported: LinearLayout
    private lateinit var openExternallyButton: com.google.android.material.button.MaterialButton
    private lateinit var previewInfoNameValue: TextView
    private lateinit var previewInfoTypeValue: TextView
    private lateinit var previewInfoSizeValue: TextView
    private lateinit var previewInfoUploadedValue: TextView
    private lateinit var previewInfoModifiedValue: TextView
    private lateinit var showProcessedImageSwitch: androidx.appcompat.widget.SwitchCompat

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
        setupImagePreview()
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
        previewUnsupported = findViewById(R.id.previewUnsupported)
        openExternallyButton = findViewById(R.id.openExternallyButton)
        previewInfoNameValue = findViewById(R.id.previewInfoNameValue)
        previewInfoTypeValue = findViewById(R.id.previewInfoTypeValue)
        previewInfoSizeValue = findViewById(R.id.previewInfoSizeValue)
        previewInfoUploadedValue = findViewById(R.id.previewInfoUploadedValue)
        previewInfoModifiedValue = findViewById(R.id.previewInfoModifiedValue)
        showProcessedImageSwitch = findViewById(R.id.showProcessedImageSwitch)
    }

    private fun setupImagePreview() {
        val previewUrl = intent.getStringExtra(EXTRA_PREVIEW_URL)
        val analyzedImagePath = intent.getStringExtra(EXTRA_ANALYZED_IMAGE_PATH)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        val ocrText = intent.getStringExtra(EXTRA_OCR_TEXT).orEmpty()
        val aiTags = intent.getStringArrayListExtra(EXTRA_AI_TAGS).orEmpty()
        titleText.text = fileName.ifBlank { getString(R.string.preview_file_name) }
        previewInfoNameValue.text = fileName.ifBlank { getString(R.string.preview_file_name) }
        
        val type = explorerItem?.type?.name ?: fileName.substringAfterLast('.', "Unknown").uppercase()
        previewInfoTypeValue.text = type
        
        previewInfoSizeValue.text = explorerItem?.size ?: getString(R.string.preview_file_size)
        val modified = explorerItem?.modified ?: getString(R.string.preview_modified_date)
        previewInfoUploadedValue.text = modified
        previewInfoModifiedValue.text = modified

        val originalPreviewSource = previewUrl?.let { Uri.parse(it) }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val isImage = extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val isVideo = extension in listOf("mp4", "mkv", "webm", "avi")
        val isAudio = extension in listOf("mp3", "wav", "ogg", "m4a")
        val isText = extension in listOf("txt", "json", "xml", "md", "csv", "kt", "java", "py", "html", "css", "js", "sh")

        previewImage.visibility = View.GONE
        previewVideo.visibility = View.GONE
        previewTextScroll.visibility = View.GONE
        previewUnsupported.visibility = View.GONE

        showProcessedImageSwitch.isEnabled = (isImage || (extension.isBlank() && originalPreviewSource != null)) && !analyzedImagePath.isNullOrEmpty()

        if (isImage || (extension.isBlank() && originalPreviewSource != null)) {
            previewImage.visibility = View.VISIBLE
            
            fun loadImage(sourceUri: Uri?) {
                if (sourceUri == null) {
                    previewImage.setImageResource(R.drawable.explorer_file_preview_placeholder)
                } else {
                    previewImage.load(sourceUri) {
                        crossfade(true)
                        placeholder(R.drawable.explorer_file_preview_placeholder)
                        error(R.drawable.explorer_file_preview_placeholder)
                    }
                }
            }
            
            loadImage(originalPreviewSource)
            
            showProcessedImageSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !analyzedImagePath.isNullOrEmpty()) {
                    loadImage(Uri.fromFile(java.io.File(analyzedImagePath)))
                } else {
                    loadImage(originalPreviewSource)
                }
            }
        } else if (isVideo || isAudio) {
            previewVideo.visibility = View.VISIBLE
            val mediaController = android.widget.MediaController(this)
            mediaController.setAnchorView(previewVideo)
            previewVideo.setMediaController(mediaController)
            if (originalPreviewSource != null) {
                previewVideo.setVideoURI(originalPreviewSource)
                previewVideo.setOnPreparedListener { it.start() }
            }
        } else if (isText) {
            previewTextScroll.visibility = View.VISIBLE
            previewText.text = getString(R.string.preview_loading_text)
            lifecycleScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        java.net.URL(previewUrl).readText()
                    }
                    previewText.text = content
                } catch (e: Exception) {
                    previewText.text = getString(R.string.preview_error_loading_text) + "\n" + e.message
                }
            }
        } else {
            previewUnsupported.visibility = View.VISIBLE
            openExternallyButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(previewUrl), "*/*")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this@FilePreviewActivity, "No app found to open this file.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        findViewById<TextView>(R.id.ocrTextView).text = ocrText.ifBlank {
            getString(R.string.preview_ai_empty)
        }
        val objectTagsContainer = findViewById<LinearLayout>(R.id.objectTagsContainer)
        objectTagsContainer.removeAllViews()
        if (aiTags.isEmpty()) {
            objectTagsTitle.visibility = View.GONE
            objectTagsContainer.visibility = View.GONE
        } else {
            objectTagsTitle.visibility = View.VISIBLE
            objectTagsContainer.visibility = View.VISIBLE
        }
        aiTags.forEach { tag ->
            val chip = layoutInflater.inflate(android.R.layout.simple_list_item_1, objectTagsContainer, false) as TextView
            chip.text = tag
            chip.setTextColor(getColor(R.color.preview_text_primary))
            chip.textSize = 13f
            chip.background = getDrawable(R.drawable.explorer_tag_background)
            chip.setPadding(16, 12, 16, 12)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            chip.layoutParams = params
            objectTagsContainer.addView(chip)
        }
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.closeSheetButton).setOnClickListener { hidePanel() }
        scrimView.setOnClickListener { hidePanel() }
        fileInfoTab.setOnClickListener { togglePanel(Panel.FILE_INFO) }
        aiAnalysisTab.setOnClickListener { togglePanel(Panel.AI_ANALYSIS) }
        findViewById<ImageButton>(R.id.moreButton).setOnClickListener { anchor ->
            showOverflowMenu(anchor)
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
            "download" -> {
                ExplorerDownloadService.start(this, item.name, item.previewUrl.orEmpty())
                android.widget.Toast.makeText(this, R.string.explorer_download_started, android.widget.Toast.LENGTH_SHORT).show()
            }
            "share" -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.previewUrl)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.preview_action_share)))
            }
            "rename" -> showRenameDialog(item)
            "move" -> showMoveDialog(item)
            "favorite" -> {
                setResult(RESULT_ACTION_FAVORITE, Intent().putExtra(EXTRA_EXPLORER_ITEM, item))
                finish()
            }
            "delete" -> showDeleteDialog(item)
            "ai" -> {
                setResult(RESULT_ACTION_AI, Intent().putExtra(EXTRA_EXPLORER_ITEM, item))
                finish()
            }
        }
    }

    private fun showRenameDialog(item: ExplorerItem) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(item.name)
            setSelection(item.name.length)
            hint = getString(R.string.explorer_dialog_rename_hint)
            setPadding(64, 50, 64, 0)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.explorer_dialog_rename_title)
            .setView(input)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                val newName = input.text?.toString().orEmpty()
                lifecycleScope.launch {
                    explorerRepository.renameItem(username, item, newName)
                        .onSuccess { 
                            setResult(RESULT_OK)
                            finish()
                        }
                        .onFailure { throwable ->
                            android.widget.Toast.makeText(this@FilePreviewActivity, throwable.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .show()
    }

    private fun showDeleteDialog(item: ExplorerItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.explorer_dialog_delete_title)
            .setMessage(getString(R.string.explorer_dialog_delete_message, item.name))
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                lifecycleScope.launch {
                    explorerRepository.deleteItem(username, item)
                        .onSuccess {
                            setResult(RESULT_OK)
                            finish()
                        }
                        .onFailure { throwable ->
                            android.widget.Toast.makeText(this@FilePreviewActivity, throwable.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .show()
    }

    private fun showMoveDialog(item: ExplorerItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_folder_picker, null)
        val pathText = dialogView.findViewById<TextView>(R.id.folderPickerPathText)
        val upButton = dialogView.findViewById<TextView>(R.id.folderPickerUpButton)
        val loadingView = dialogView.findViewById<ProgressBar>(R.id.folderPickerLoading)
        val emptyView = dialogView.findViewById<TextView>(R.id.folderPickerEmptyText)
        val invalidHintText = dialogView.findViewById<TextView>(R.id.folderPickerInvalidHintText)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderPickerRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)

        var currentPath = ""
        var loadJob: Job? = null
        lateinit var loadFolderPickerPath: (String) -> Unit
        var positiveButton: Button? = null
        val adapter = FolderPickerAdapter { folder -> loadFolderPickerPath(folder.path) }
        recyclerView.adapter = adapter

        fun renderFolderPickerPath() {
            pathText.text = if (currentPath.isBlank()) {
                getString(R.string.explorer_folder_picker_current_root)
            } else {
                getString(R.string.explorer_folder_picker_current, currentPath)
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
            loadJob = lifecycleScope.launch {
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
                        emptyView.text = getString(R.string.explorer_folder_picker_empty)
                    }
                    .onFailure {
                        adapter.submitItems(emptyList())
                        emptyView.visibility = View.VISIBLE
                        emptyView.text = getString(R.string.explorer_folder_picker_loading_error)
                    }
                loadingView.visibility = View.GONE
                recyclerView.alpha = 1f
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.explorer_dialog_move_title)
            .setView(dialogView)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_folder_picker_move_here) { _, _ ->
                lifecycleScope.launch {
                    explorerRepository.moveItem(username, item, currentPath)
                        .onSuccess {
                            setResult(RESULT_OK)
                            finish()
                        }
                        .onFailure { throwable ->
                            android.widget.Toast.makeText(this@FilePreviewActivity, throwable.message, android.widget.Toast.LENGTH_SHORT).show()
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

    private enum class Panel {
        FILE_INFO,
        AI_ANALYSIS
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
            return Intent(context, FilePreviewActivity::class.java).apply {
                putExtra(EXTRA_EXPLORER_ITEM, item)
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_FILE_NAME, item.name)
                putExtra(EXTRA_PREVIEW_URL, item.previewUrl)
                putExtra(EXTRA_ANALYZED_IMAGE_PATH, analyzedImagePath)
                putExtra(EXTRA_OCR_TEXT, ocrText)
                putStringArrayListExtra(EXTRA_AI_TAGS, ArrayList(aiTags))
                putExtra(EXTRA_SHOW_AI_PANEL, showAiPanel)
            }
        }
    }
}
