package com.example.filemanagementapp.preview

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
        showPanel(Panel.FILE_INFO)
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
    }

    private fun setupImagePreview() {
        findViewById<ImageView>(R.id.previewImage).load(PREVIEW_IMAGE_URL)
        findViewById<TextView>(R.id.ocrTextView).text = OCR_TEXT
        val objectTagsContainer = findViewById<LinearLayout>(R.id.objectTagsContainer)
        DETECTED_OBJECTS.forEach { (label, confidence) ->
            val chip = layoutInflater.inflate(android.R.layout.simple_list_item_1, objectTagsContainer, false) as TextView
            chip.text = "$label  $confidence%"
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
        private const val PREVIEW_IMAGE_URL =
            "https://images.unsplash.com/photo-1579808352667-5db8c02598ce?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwyfHxkb2N1bWVudCUyMHJlY2VpcHQlMjBpbnZvaWNlJTIwYnVzaW5lc3N8ZW58MXx8fHwxNzc5MjA5MTcyfDA&ixlib=rb-4.1.0&q=80&w=1080"
        private const val OCR_TEXT =
            "INVOICE\nDate: March 15, 2024\nInvoice #: INV-2024-0315\n\nBill To:\nAcme Corporation\n123 Business Street\nNew York, NY 10001\n\nDescription: Web Development Services\nHours: 40\nRate: \$150/hr\nTotal: \$6,000.00\n\nPayment Due: April 15, 2024"
        private val DETECTED_OBJECTS = listOf(
            "Document" to 98,
            "Text" to 95,
            "Paper" to 92,
            "Invoice" to 89,
            "Receipt" to 85
        )
        private const val MENU_DOWNLOAD = 1
        private const val MENU_SHARE = 2
        private const val MENU_RENAME = 3
        private const val MENU_MOVE = 4
        private const val MENU_FAVORITE = 5
        private const val MENU_AI = 6
        private const val MENU_DELETE = 7
    }
}
