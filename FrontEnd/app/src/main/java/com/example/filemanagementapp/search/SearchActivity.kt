package com.example.filemanagementapp.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.auth.local.LoginPreferencesRepository
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository
import com.example.filemanagementapp.data.local.explorer.DirectoryCacheLocalRepository
import com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository
import com.example.filemanagementapp.data.local.search.SearchHistoryPreferencesRepository
import com.example.filemanagementapp.explorer.ExplorerItem
import com.example.filemanagementapp.explorer.FileActionSheetController
import com.example.filemanagementapp.preview.FilePreviewActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity(), FileActionSheetController.Callbacks {
    private lateinit var adapter: SearchResultAdapter
    private lateinit var recentSearchSection: View
    private lateinit var resultBannerText: TextView
    private lateinit var categoryChipRow: LinearLayout
    private lateinit var recentSearchChipRow: LinearLayout
    private lateinit var searchEditText: EditText
    
    private lateinit var viewModel: SearchViewModel
    private lateinit var actionSheetController: FileActionSheetController
    private lateinit var explorerRepository: ExplorerRepository
    
    private var username: String = ""

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
        
        lifecycleScope.launch {
            val loginPrefs = LoginPreferencesRepository(applicationContext)
            val prefs = loginPrefs.preferencesFlow.first()
            username = prefs.rememberedUsername
            if (username.isBlank()) {
                finish()
                return@launch
            }

            initViewModelAndRepositories(username)
            setupHeader()
            observeViewModel()
        }
    }

    private fun bindViews() {
        recentSearchSection = findViewById(R.id.recentSearchSection)
        resultBannerText = findViewById(R.id.resultBannerText)
        categoryChipRow = findViewById(R.id.categoryChipRow)
        recentSearchChipRow = findViewById(R.id.recentSearchChipRow)
        searchEditText = findViewById(R.id.searchEditText)
    }

    private fun setupRecycler() {
        val recyclerView = findViewById<RecyclerView>(R.id.resultsRecyclerView)
        adapter = SearchResultAdapter(emptyList(), ::showActionSheet)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun initViewModelAndRepositories(username: String) {
        val appContext = applicationContext
        val appDatabase = com.example.filemanagementapp.data.local.AppDatabase.getInstance(appContext)
        val directoryCache = DirectoryCacheLocalRepository(appDatabase.directoryCacheDao(), com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson)
        val aiAnalysis = AiAnalysisLocalRepository(appDatabase.aiAnalysisCacheDao(), com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson)
        val favoriteLocal = FavoriteLocalRepository(appDatabase.favoriteItemDao())
        val searchHistory = SearchHistoryPreferencesRepository(appContext)
        val searchRepo = SearchRepository(directoryCache, aiAnalysis, favoriteLocal)
        
        explorerRepository = com.example.filemanagementapp.data.explorer.repository.ExplorerRepository(
            appContext = applicationContext,
            explorerApiService = com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.explorerApiService,
            gson = com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson
        )

        actionSheetController = FileActionSheetController(
            rootView = findViewById(android.R.id.content),
            lifecycleOwner = this,
            explorerRepository = explorerRepository,
            username = username,
            currentFolderProvider = { "" },
            callbacks = this
        )

        val factory = SearchViewModel.Factory(username, searchRepo, searchHistory, favoriteLocal)
        viewModel = ViewModelProvider(this, factory)[SearchViewModel::class.java]
    }

    private fun setupHeader() {
        findViewById<ImageButton>(R.id.filterButton).setOnClickListener(::showSortMenu)
        searchEditText.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty()
            viewModel.updateQuery(query)
        }
        
        // Save search query when pressing enter or equivalent
        searchEditText.setOnEditorActionListener { _, _, _ ->
            val query = searchEditText.text.toString()
            if (query.isNotBlank()) {
                viewModel.addRecentSearch(query)
            }
            false
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitItems(state.filteredItems)
                    
                    recentSearchSection.visibility = if (state.query.isBlank()) View.VISIBLE else View.GONE
                    
                    if (state.query.isBlank()) {
                        resultBannerText.visibility = View.GONE
                    } else {
                        resultBannerText.visibility = View.VISIBLE
                        resultBannerText.text = getString(R.string.search_results_banner, state.filteredItems.size, state.query)
                    }
                    
                    renderCategoryChips(state.selectedCategory)
                    renderRecentSearches(state.recentSearches)
                }
            }
        }
    }

    private fun renderCategoryChips(selectedCategory: String) {
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
                viewModel.selectCategory(id)
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

    private fun renderRecentSearches(searches: List<String>) {
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
            chip.setOnClickListener { 
                searchEditText.setText(search)
                searchEditText.setSelection(search.length)
                viewModel.addRecentSearch(search)
            }
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
            recentSearchChipRow.addView(chip)
        }
    }

    private fun showSortMenu(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_search_sort, null)
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val currentSort = viewModel.uiState.value.currentSort

        val checkSortName = popupView.findViewById<ImageView>(R.id.checkSortName)
        val checkSortDate = popupView.findViewById<ImageView>(R.id.checkSortDate)
        val checkSortSize = popupView.findViewById<ImageView>(R.id.checkSortSize)

        checkSortName?.visibility = if (currentSort == SearchViewModel.Sort.NAME) View.VISIBLE else View.INVISIBLE
        checkSortDate?.visibility = if (currentSort == SearchViewModel.Sort.DATE) View.VISIBLE else View.INVISIBLE
        checkSortSize?.visibility = if (currentSort == SearchViewModel.Sort.SIZE) View.VISIBLE else View.INVISIBLE

        popupView.findViewById<View>(R.id.menuSortName).setOnClickListener {
            viewModel.selectSort(SearchViewModel.Sort.NAME)
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.menuSortDate).setOnClickListener {
            viewModel.selectSort(SearchViewModel.Sort.DATE)
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.menuSortSize).setOnClickListener {
            viewModel.selectSort(SearchViewModel.Sort.SIZE)
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(anchor, 0, 8)
    }

    private fun showActionSheet(item: SearchItem) {
        actionSheetController.show(
            item.rawItem,
            FileActionSheetController.ActionConfig(
                showRename = false,
                showMove = false
            )
        )
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        chip.background = getDrawable(R.drawable.explorer_tag_background)
        chip.setTextColor(getColor(if (selected) R.color.search_primary else R.color.search_text_primary))
        chip.backgroundTintList = getColorStateList(
            if (selected) R.color.search_primary_soft else R.color.search_surface
        )
    }

    // --- FileActionSheetController.Callbacks ---

    override fun onOpen(item: ExplorerItem) {
        if (item.type == ExplorerItem.Type.FOLDER) {
            Toast.makeText(this, getString(R.string.search_cannot_open_folder), Toast.LENGTH_SHORT).show()
        } else {
            startActivity(
                FilePreviewActivity.newIntent(
                    context = this,
                    item = item,
                    username = username,
                    analyzedImagePath = null,
                    ocrText = null,
                    aiTags = emptyList(),
                    showAiPanel = false
                )
            )
        }
    }

    override fun onDownload(item: ExplorerItem) {
        Toast.makeText(this, getString(R.string.search_downloading, item.name), Toast.LENGTH_SHORT).show()
    }

    override fun onRename(item: ExplorerItem, newName: String) {
        // Hidden
    }

    override fun onMove(item: ExplorerItem, targetPath: String) {
        // Hidden
    }

    override fun onFavorite(item: ExplorerItem) {
        val searchItem = viewModel.uiState.value.allItems.find { it.rawItem.path == item.path }
        if (searchItem != null) {
            viewModel.toggleFavorite(searchItem)
        }
    }

    override fun onDelete(item: ExplorerItem) {
        lifecycleScope.launch {
            explorerRepository.deleteItem(username, item)
            viewModel.loadFiles() // Refresh search results
            Toast.makeText(this@SearchActivity, getString(R.string.search_deleted, item.name), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAnalyzeAi(item: ExplorerItem) {
        Toast.makeText(this, getString(R.string.search_ai_scheduled, item.name), Toast.LENGTH_SHORT).show()
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
