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
import androidx.core.content.ContextCompat
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
    private lateinit var emptyStateContainer: View
    private lateinit var aiLoadingOverlay: View
    private lateinit var aiLoadingAnimation: com.airbnb.lottie.LottieAnimationView
    
    private lateinit var viewModel: SearchViewModel
    private lateinit var actionSheetController: FileActionSheetController
    private lateinit var explorerRepository: ExplorerRepository
    private lateinit var recentOpenLocalRepository: com.example.filemanagementapp.data.local.recent.RecentOpenLocalRepository
    
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
        
        username = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        if (username.isBlank()) {
            finish()
            return
        }

        initViewModelAndRepositories(username)
        setupHeader()
        observeViewModel()
    }

    private fun bindViews() {
        recentSearchSection = findViewById(R.id.recentSearchSection)
        resultBannerText = findViewById(R.id.resultBannerText)
        categoryChipRow = findViewById(R.id.categoryChipRow)
        recentSearchChipRow = findViewById(R.id.recentSearchChipRow)
        searchEditText = findViewById(R.id.searchEditText)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        aiLoadingOverlay = findViewById(R.id.aiLoadingOverlay)
        aiLoadingAnimation = findViewById(R.id.aiLoadingAnimation)
    }

    private fun setupRecycler() {
        val recyclerView = findViewById<RecyclerView>(R.id.resultsRecyclerView)
        adapter = SearchResultAdapter(
            items = emptyList(),
            onItemClick = { item -> onOpen(item.rawItem) },
            onMoreClick = ::showActionSheet
        )
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
        
        recentOpenLocalRepository = com.example.filemanagementapp.data.local.recent.RecentOpenLocalRepository(appDatabase.recentOpenDao())
        
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

    private val micActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                searchEditText.setText(spokenText)
                searchEditText.setSelection(spokenText.length)
                viewModel.updateQuery(spokenText)
                viewModel.addRecentSearch(spokenText)
            }
        }
    }

    private fun setupHeader() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.filterButton).setOnClickListener(::showSortMenu)
        
        findViewById<ImageButton>(R.id.micButton).setOnClickListener {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_hint))
            }
            try {
                micActivityLauncher.launch(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Voice search is not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        searchEditText.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty()
            if (query.isBlank()) {
                viewModel.updateQuery("")
            }
        }
        
        // Execute search when pressing enter or equivalent
        searchEditText.setOnEditorActionListener { v, _, _ ->
            val query = searchEditText.text.toString()
            viewModel.updateQuery(query)
            if (query.isNotBlank()) {
                viewModel.addRecentSearch(query)
            }
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
            true
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitItems(state.filteredItems)
                    
                    val isSearching = state.query.isNotBlank()
                    
                    recentSearchSection.visibility = if (!isSearching && state.recentSearches.isNotEmpty()) View.VISIBLE else View.GONE
                    
                    if (!isSearching) {
                        resultBannerText.visibility = View.GONE
                        emptyStateContainer.visibility = View.GONE
                    } else {
                        resultBannerText.visibility = View.VISIBLE
                        val bannerSuffix = if (state.query.isNotBlank()) state.query else when (state.selectedCategory) {
                            CATEGORY_IMAGES -> getString(R.string.search_category_images)
                            CATEGORY_DOCUMENTS -> getString(R.string.search_category_documents)
                            CATEGORY_PDF -> getString(R.string.search_category_pdf)
                            CATEGORY_VIDEOS -> getString(R.string.search_category_videos)
                            CATEGORY_OCR -> getString(R.string.search_category_ocr)
                            CATEGORY_AI_OBJECTS -> getString(R.string.search_category_ai_objects)
                            CATEGORY_FAVORITES -> getString(R.string.search_category_favorites)
                            else -> ""
                        }
                        resultBannerText.text = getString(R.string.search_results_banner, state.filteredItems.size, bannerSuffix)
                        emptyStateContainer.visibility = if (state.filteredItems.isEmpty()) View.VISIBLE else View.GONE
                    }
                    
                    renderCategoryChips(state.selectedCategory)
                    renderRecentSearches(state.recentSearches)
                }
            }
        }
    }

    private fun renderCategoryChips(selectedCategory: String) {
        val categories = listOf(
            Triple(CATEGORY_ALL, getString(R.string.search_category_all), null),
            Triple(CATEGORY_IMAGES, getString(R.string.search_category_images), R.drawable.image_icon),
            Triple(CATEGORY_DOCUMENTS, getString(R.string.search_category_documents), R.drawable.file_text),
            Triple(CATEGORY_PDF, getString(R.string.search_category_pdf), R.drawable.file_text),
            Triple(CATEGORY_VIDEOS, getString(R.string.search_category_videos), R.drawable.film),
            Triple(CATEGORY_OCR, getString(R.string.search_category_ocr), R.drawable.scan),
            Triple(CATEGORY_AI_OBJECTS, getString(R.string.search_category_ai_objects), R.drawable.sparkles),
            Triple(CATEGORY_FAVORITES, getString(R.string.search_category_favorites), R.drawable.star)
        )
        categoryChipRow.removeAllViews()
        categories.forEach { (id, label, iconRes) ->
            val chip = layoutInflater.inflate(
                android.R.layout.simple_list_item_1,
                categoryChipRow,
                false
            ) as TextView
            chip.text = label
            chip.textSize = 14f
            chip.setPadding(32, 18, 32, 18)
            chip.compoundDrawablePadding = 16
            
            if (iconRes != null) {
                val drawable = ContextCompat.getDrawable(this, iconRes)
                // Optionally resize the drawable if needed, but intrinsic bounds should be fine
                chip.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
            } else {
                chip.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            }
            
            chip.setOnClickListener {
                viewModel.selectCategory(id)
            }
            styleChip(chip, selectedCategory == id)
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 16 // converted to dp? The existing one used 8 (px?), actually LinearLayout params margin is in px if directly assigned integer, but wait...
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
                viewModel.updateQuery(search)
                viewModel.addRecentSearch(search)
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
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
                showDownload = item.rawItem.type == ExplorerItem.Type.FILE,
                showRename = false,
                showMove = false,
                showAi = item.rawItem.isImagePreviewable
            )
        )
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        val bgDrawable = android.graphics.drawable.GradientDrawable()
        bgDrawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        bgDrawable.cornerRadius = 999f * resources.displayMetrics.density
        
        if (selected) {
            bgDrawable.setColor(getColor(R.color.search_primary_soft))
            bgDrawable.setStroke(0, android.graphics.Color.TRANSPARENT)
        } else {
            bgDrawable.setColor(getColor(R.color.search_surface))
            bgDrawable.setStroke((1 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#E0E0E0"))
        }
        
        chip.background = bgDrawable
        chip.setTextColor(getColor(if (selected) R.color.search_primary else R.color.search_text_primary))
        chip.compoundDrawableTintList = getColorStateList(
            if (selected) R.color.search_primary else R.color.search_text_primary
        )
    }

    // --- FileActionSheetController.Callbacks ---

    override fun onOpen(item: ExplorerItem) {
        lifecycleScope.launch {
            recentOpenLocalRepository.recordOpen(username, item.path)
        }
        if (item.type == ExplorerItem.Type.FOLDER) {
            val resultIntent = android.content.Intent().apply {
                putExtra(EXTRA_RESULT_FOLDER_PATH, item.path)
            }
            setResult(android.app.Activity.RESULT_OK, resultIntent)
            finish()
        } else {
            startActivity(
                FilePreviewActivity.newIntent(
                    context = this,
                    item = item,
                    username = username,
                    analyzedImagePath = item.analyzedImagePath,
                    ocrText = item.ocrSnippet,
                    aiTags = item.tags,
                    showAiPanel = false
                )
            )
        }
    }

    override fun onDownload(item: ExplorerItem) {
        val downloadUrl = item.previewUrl
        if (downloadUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.explorer_download_start_failed, Toast.LENGTH_SHORT).show()
            return
        }
        com.example.filemanagementapp.download.ExplorerDownloadService.start(
            context = this,
            fileName = item.name,
            downloadUrl = downloadUrl
        )
        Toast.makeText(this, R.string.explorer_download_started, Toast.LENGTH_SHORT).show()
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
        if (item.type != ExplorerItem.Type.FILE) {
            Toast.makeText(this, R.string.error_ai_only_file, Toast.LENGTH_SHORT).show()
            return
        }
        val previewUrl = item.previewUrl
        if (previewUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.error_ai_no_file_path, Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, getString(R.string.search_ai_scheduled, item.name), Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            aiLoadingOverlay.visibility = android.view.View.VISIBLE
            aiLoadingAnimation.playAnimation()

            val aiRepo = com.example.filemanagementapp.data.ai.repository.AiAnalysisRepository(
                applicationContext,
                com.example.filemanagementapp.data.ai.network.AiNetworkModule.aiApiService,
                com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.okHttpClient,
                com.example.filemanagementapp.data.ai.network.AiNetworkModule.gson
            )
            val appDb = com.example.filemanagementapp.data.local.AppDatabase.getInstance(this@SearchActivity)
            val aiLocal = com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository(
                appDb.aiAnalysisCacheDao(),
                com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson
            )
            
            aiRepo.analyzeImageFile(username, item.name, previewUrl).onSuccess { result ->
                aiLocal.upsertAnalysis(
                    com.example.filemanagementapp.data.local.ai.AiAnalysisCache(
                        username = username,
                        filePath = item.path,
                        status = com.example.filemanagementapp.data.local.ai.AiAnalysisStatus.COMPLETED,
                        tags = result.tags,
                        ocrText = result.texts.joinToString("\n"),
                        previewImagePath = result.previewImagePath,
                        analysisType = "ocr",
                        modelSource = "predict-image"
                    )
                )
                viewModel.loadFiles()
                aiLoadingOverlay.visibility = android.view.View.GONE
                aiLoadingAnimation.cancelAnimation()
            }.onFailure { throwable ->
                aiLoadingOverlay.visibility = android.view.View.GONE
                aiLoadingAnimation.cancelAnimation()
                Toast.makeText(this@SearchActivity, throwable.message ?: "Analysis failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_RESULT_FOLDER_PATH = "extra_result_folder_path"

        fun newIntent(context: Context, username: String): Intent {
            return Intent(context, SearchActivity::class.java).apply {
                putExtra(EXTRA_USERNAME, username)
            }
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
