package com.example.filemanagementapp.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R

class SearchActivity : AppCompatActivity() {
    private lateinit var adapter: SearchResultAdapter
    private lateinit var recentSearchSection: View
    private lateinit var resultBannerText: TextView
    private lateinit var actionSheet: View
    private lateinit var scrimView: View
    private lateinit var sheetThumb: View
    private lateinit var sheetThumbIcon: ImageView
    private lateinit var sheetFileName: TextView
    private lateinit var sheetFileMeta: TextView
    private lateinit var categoryChipRow: LinearLayout
    private lateinit var recentSearchChipRow: LinearLayout
    private lateinit var searchEditText: EditText
    private lateinit var actionList: LinearLayout

    private var currentSort = Sort.NAME
    private var selectedCategory = CATEGORY_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupRecycler()
        setupHeader()
        renderCategoryChips()
        renderRecentSearches()
        renderActionSheetActions()
        submitResults("")
    }

    private fun bindViews() {
        recentSearchSection = findViewById(R.id.recentSearchSection)
        resultBannerText = findViewById(R.id.resultBannerText)
        actionSheet = findViewById(R.id.actionSheet)
        scrimView = findViewById(R.id.scrimView)
        sheetThumb = findViewById(R.id.sheetThumb)
        sheetThumbIcon = findViewById(R.id.sheetThumbIcon)
        sheetFileName = findViewById(R.id.sheetFileName)
        sheetFileMeta = findViewById(R.id.sheetFileMeta)
        categoryChipRow = findViewById(R.id.categoryChipRow)
        recentSearchChipRow = findViewById(R.id.recentSearchChipRow)
        searchEditText = findViewById(R.id.searchEditText)
        actionList = findViewById(R.id.actionList)
    }

    private fun setupRecycler() {
        val recyclerView = findViewById<RecyclerView>(R.id.resultsRecyclerView)
        adapter = SearchResultAdapter(emptyList(), ::showActionSheet)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupHeader() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.filterButton).setOnClickListener(::showSortMenu)
        findViewById<ImageButton>(R.id.closeSheetButton).setOnClickListener { hideActionSheet() }
        scrimView.setOnClickListener { hideActionSheet() }
        searchEditText.doAfterTextChanged { editable ->
            submitResults(editable?.toString().orEmpty())
        }
    }

    private fun renderCategoryChips() {
        val categories = listOf(
            CATEGORY_ALL to getString(R.string.search_category_all),
            CATEGORY_IMAGES to getString(R.string.search_category_images),
            CATEGORY_DOCUMENTS to getString(R.string.search_category_documents),
            CATEGORY_PDF to getString(R.string.search_category_pdf),
            CATEGORY_VIDEOS to getString(R.string.search_category_videos),
            CATEGORY_OCR to getString(R.string.search_category_ocr),
            CATEGORY_AI_OBJECTS to getString(R.string.search_category_ai_objects),
            CATEGORY_FAVORITES to getString(R.string.search_category_favorites)
        )
        categoryChipRow.removeAllViews()
        categories.forEach { (id, label) ->
            val chip = layoutInflater.inflate(
                android.R.layout.simple_list_item_1,
                categoryChipRow,
                false
            ) as TextView
            chip.text = label
            chip.textSize = 14f
            chip.setPadding(28, 18, 28, 18)
            chip.setOnClickListener {
                selectedCategory = id
                renderCategoryChips()
                submitResults(searchEditText.text?.toString().orEmpty())
            }
            styleChip(chip, selectedCategory == id)
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
            categoryChipRow.addView(chip)
        }
    }

    private fun renderRecentSearches() {
        val searches = listOf("invoice", "car", "meeting notes", "receipt")
        recentSearchChipRow.removeAllViews()
        searches.forEach { search ->
            val chip = layoutInflater.inflate(
                android.R.layout.simple_list_item_1,
                recentSearchChipRow,
                false
            ) as TextView
            chip.text = search
            chip.textSize = 14f
            chip.setPadding(24, 14, 24, 14)
            chip.background = getDrawable(R.drawable.explorer_tag_background)
            chip.setTextColor(getColor(R.color.search_text_primary))
            chip.setOnClickListener { searchEditText.setText(search) }
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
            recentSearchChipRow.addView(chip)
        }
    }

    private fun renderActionSheetActions() {
        val actions = listOf(
            SearchAction(R.drawable.external_link, getString(R.string.search_action_open), false),
            SearchAction(R.drawable.download, getString(R.string.preview_action_download), false),
            SearchAction(R.drawable.share_2, getString(R.string.preview_action_share), false),
            SearchAction(R.drawable.star, getString(R.string.preview_action_favorite), false),
            SearchAction(R.drawable.trash_2, getString(R.string.preview_action_delete), true),
            SearchAction(R.drawable.sparkles, getString(R.string.preview_action_ai), false)
        )
        actionList.removeAllViews()
        actions.forEach { action ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 18, 20, 18)
                setOnClickListener { hideActionSheet() }
            }
            val icon = ImageView(this).apply {
                setImageResource(action.iconRes)
                imageTintList = getColorStateList(
                    if (action.destructive) R.color.search_destructive else R.color.search_text_primary
                )
            }
            val label = TextView(this).apply {
                text = action.label
                textSize = 14f
                setTextColor(
                    getColor(if (action.destructive) R.color.search_destructive else R.color.search_text_primary)
                )
                setPadding(16, 0, 0, 0)
            }
            row.addView(icon)
            row.addView(label)
            actionList.addView(row)
        }
    }

    private fun submitResults(query: String) {
        val filtered = filterItems(query)
        adapter.submitItems(filtered)
        recentSearchSection.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
        if (query.isBlank()) {
            resultBannerText.visibility = View.GONE
        } else {
            resultBannerText.visibility = View.VISIBLE
            resultBannerText.text = "${filtered.size} results for \"$query\""
        }
    }

    private fun filterItems(query: String): List<SearchItem> {
        val base = allItems().filter { item ->
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                (item.ocrText?.contains(query, ignoreCase = true) == true) ||
                item.tags.any { it.contains(query, ignoreCase = true) }

            val matchesCategory = when (selectedCategory) {
                CATEGORY_ALL -> true
                CATEGORY_IMAGES -> item.category == SearchItem.Category.IMAGE
                CATEGORY_DOCUMENTS -> item.category == SearchItem.Category.DOCUMENT
                CATEGORY_PDF -> item.category == SearchItem.Category.PDF
                CATEGORY_VIDEOS -> false
                CATEGORY_OCR -> !item.ocrText.isNullOrBlank()
                CATEGORY_AI_OBJECTS -> item.tags.any { it != "AI analyzed" }
                CATEGORY_FAVORITES -> item.tags.any { it.equals("AI analyzed", true) }
                else -> true
            }
            matchesQuery && matchesCategory
        }

        return when (currentSort) {
            Sort.NAME -> base.sortedBy { it.name }
            Sort.DATE -> base.sortedByDescending { it.date }
            Sort.SIZE -> base.sortedByDescending { it.size }
        }
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_SORT_NAME, 0, R.string.search_sort_name)
        popup.menu.add(0, MENU_SORT_DATE, 1, R.string.search_sort_date)
        popup.menu.add(0, MENU_SORT_SIZE, 2, R.string.search_sort_size)
        popup.setOnMenuItemClickListener {
            currentSort = when (it.itemId) {
                MENU_SORT_NAME -> Sort.NAME
                MENU_SORT_DATE -> Sort.DATE
                else -> Sort.SIZE
            }
            submitResults(searchEditText.text?.toString().orEmpty())
            true
        }
        popup.show()
    }

    private fun showActionSheet(item: SearchItem) {
        val style = when (item.category) {
            SearchItem.Category.PDF ->
                Triple(R.drawable.file_text, R.color.recent_pdf_bg, R.color.recent_pdf)

            SearchItem.Category.IMAGE ->
                Triple(R.drawable.image_icon, R.color.recent_image_bg, R.color.recent_image)

            SearchItem.Category.DOCUMENT ->
                Triple(R.drawable.file_text, R.color.recent_doc_bg, R.color.recent_doc)
        }
        sheetThumb.backgroundTintList = getColorStateList(style.second)
        sheetThumbIcon.setImageResource(style.first)
        sheetThumbIcon.imageTintList = getColorStateList(style.third)
        sheetFileName.text = item.name
        sheetFileMeta.text = "${item.type} • ${item.size}"
        scrimView.visibility = View.VISIBLE
        actionSheet.visibility = View.VISIBLE
    }

    private fun hideActionSheet() {
        scrimView.visibility = View.GONE
        actionSheet.visibility = View.GONE
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        chip.background = getDrawable(R.drawable.explorer_tag_background)
        chip.setTextColor(getColor(if (selected) R.color.search_primary else R.color.search_text_primary))
        chip.backgroundTintList = getColorStateList(
            if (selected) R.color.search_primary_soft else R.color.search_surface
        )
    }

    private fun allItems(): List<SearchItem> = listOf(
        SearchItem(
            1,
            getString(R.string.search_file_name_1),
            "PDF",
            "2.4 MB",
            "Mar 15, 2024",
            SearchItem.Category.PDF,
            getString(R.string.search_ocr_1),
            listOf("Invoice", "Receipt", "AI analyzed")
        ),
        SearchItem(
            2,
            getString(R.string.search_file_name_2),
            "Image",
            "4.1 MB",
            "Mar 10, 2024",
            SearchItem.Category.IMAGE,
            null,
            listOf("Car", "Vehicle", "AI analyzed")
        ),
        SearchItem(
            3,
            getString(R.string.search_file_name_3),
            "Document",
            "156 KB",
            "Mar 8, 2024",
            SearchItem.Category.DOCUMENT,
            getString(R.string.search_ocr_3),
            listOf("Document", "Meeting")
        ),
        SearchItem(
            4,
            getString(R.string.search_file_name_4),
            "PDF",
            "890 KB",
            "Feb 28, 2024",
            SearchItem.Category.PDF,
            getString(R.string.search_ocr_4),
            listOf("Receipt", "Laptop", "AI analyzed")
        )
    )

    private data class SearchAction(
        val iconRes: Int,
        val label: String,
        val destructive: Boolean
    )

    private enum class Sort {
        NAME,
        DATE,
        SIZE
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, SearchActivity::class.java)
        }

        private const val CATEGORY_ALL = "all"
        private const val CATEGORY_IMAGES = "images"
        private const val CATEGORY_DOCUMENTS = "documents"
        private const val CATEGORY_PDF = "pdf"
        private const val CATEGORY_VIDEOS = "videos"
        private const val CATEGORY_OCR = "ocr"
        private const val CATEGORY_AI_OBJECTS = "ai-objects"
        private const val CATEGORY_FAVORITES = "favorites"

        private const val MENU_SORT_NAME = 1
        private const val MENU_SORT_DATE = 2
        private const val MENU_SORT_SIZE = 3
    }
}
