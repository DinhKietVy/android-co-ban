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
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.example.filemanagementapp.R
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
    private lateinit var previewInfoNameValue: TextView
    private lateinit var previewInfoTypeValue: TextView
    private lateinit var previewInfoSizeValue: TextView
    private lateinit var previewInfoUploadedValue: TextView
    private lateinit var previewInfoModifiedValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        showPanel(
            if (intent.getBooleanExtra(EXTRA_SHOW_AI_PANEL, false)) {
                Panel.AI_ANALYSIS
            } else {
                Panel.FILE_INFO
            }
        )
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
        previewInfoNameValue = findViewById(R.id.previewInfoNameValue)
        previewInfoTypeValue = findViewById(R.id.previewInfoTypeValue)
        previewInfoSizeValue = findViewById(R.id.previewInfoSizeValue)
        previewInfoUploadedValue = findViewById(R.id.previewInfoUploadedValue)
        previewInfoModifiedValue = findViewById(R.id.previewInfoModifiedValue)
    }

    private fun setupImagePreview() {
        val previewUrl = intent.getStringExtra(EXTRA_PREVIEW_URL)
        val analyzedImagePath = intent.getStringExtra(EXTRA_ANALYZED_IMAGE_PATH)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        val ocrText = intent.getStringExtra(EXTRA_OCR_TEXT).orEmpty()
        val aiTags = intent.getStringArrayListExtra(EXTRA_AI_TAGS).orEmpty()
        titleText.text = fileName.ifBlank { getString(R.string.preview_file_name) }
        previewInfoNameValue.text = fileName.ifBlank { getString(R.string.preview_file_name) }
        previewInfoTypeValue.text = fileName.substringAfterLast('.', "Unknown").uppercase()
        previewInfoSizeValue.text = intent.getStringExtra(EXTRA_FILE_SIZE).orEmpty()
            .ifBlank { getString(R.string.preview_file_size) }
        val modified = intent.getStringExtra(EXTRA_FILE_MODIFIED).orEmpty()
        previewInfoUploadedValue.text = modified.ifBlank { getString(R.string.preview_upload_date) }
        previewInfoModifiedValue.text = modified.ifBlank { getString(R.string.preview_modified_date) }

        val resolvedPreviewSource = analyzedImagePath
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.fromFile(File(it)) }
            ?: previewUrl
        if (resolvedPreviewSource == null) {
            previewImage.setImageResource(R.drawable.explorer_file_preview_placeholder)
        } else {
            previewImage.load(resolvedPreviewSource) {
                crossfade(true)
                placeholder(R.drawable.explorer_file_preview_placeholder)
                error(R.drawable.explorer_file_preview_placeholder)
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
        val popup = PopupMenu(this, anchor)
        popup.menu.apply {
            add(0, MENU_DOWNLOAD, 0, R.string.preview_action_download)
            add(0, MENU_SHARE, 1, R.string.preview_action_share)
            add(0, MENU_RENAME, 2, R.string.preview_action_rename)
            add(0, MENU_MOVE, 3, R.string.preview_action_move)
            add(0, MENU_FAVORITE, 4, R.string.preview_action_favorite)
            add(0, MENU_AI, 5, R.string.preview_action_ai)
            add(0, MENU_DELETE, 6, R.string.preview_action_delete)
        }
        popup.setOnMenuItemClickListener(::handleMenuAction)
        popup.show()
    }

    private fun handleMenuAction(item: MenuItem): Boolean = true

    private enum class Panel {
        FILE_INFO,
        AI_ANALYSIS
    }

    companion object {
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_PREVIEW_URL = "extra_preview_url"
        private const val EXTRA_ANALYZED_IMAGE_PATH = "extra_analyzed_image_path"
        private const val EXTRA_OCR_TEXT = "extra_ocr_text"
        private const val EXTRA_AI_TAGS = "extra_ai_tags"
        private const val EXTRA_FILE_SIZE = "extra_file_size"
        private const val EXTRA_FILE_MODIFIED = "extra_file_modified"
        private const val EXTRA_SHOW_AI_PANEL = "extra_show_ai_panel"
        private const val MENU_DOWNLOAD = 1
        private const val MENU_SHARE = 2
        private const val MENU_RENAME = 3
        private const val MENU_MOVE = 4
        private const val MENU_FAVORITE = 5
        private const val MENU_AI = 6
        private const val MENU_DELETE = 7

        fun newIntent(
            context: Context,
            fileName: String,
            previewUrl: String?,
            analyzedImagePath: String?,
            ocrText: String?,
            aiTags: List<String>,
            fileSize: String? = null,
            modified: String? = null,
            showAiPanel: Boolean = false
        ): Intent {
            return Intent(context, FilePreviewActivity::class.java).apply {
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_PREVIEW_URL, previewUrl)
                putExtra(EXTRA_ANALYZED_IMAGE_PATH, analyzedImagePath)
                putExtra(EXTRA_OCR_TEXT, ocrText)
                putStringArrayListExtra(EXTRA_AI_TAGS, ArrayList(aiTags))
                putExtra(EXTRA_FILE_SIZE, fileSize)
                putExtra(EXTRA_FILE_MODIFIED, modified)
                putExtra(EXTRA_SHOW_AI_PANEL, showAiPanel)
            }
        }
    }
}
