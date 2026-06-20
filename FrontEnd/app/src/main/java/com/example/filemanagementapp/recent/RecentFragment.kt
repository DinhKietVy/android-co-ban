package com.example.filemanagementapp.recent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import coil.load
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.local.AppDatabase
import com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository
import com.example.filemanagementapp.data.local.explorer.DirectoryCacheLocalRepository
import com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.main.MainNavigationViewModel
import com.example.filemanagementapp.preview.FilePreviewActivity
import com.example.filemanagementapp.download.ExplorerDownloadService
import kotlinx.coroutines.launch

import com.example.filemanagementapp.explorer.ExplorerItem
import com.example.filemanagementapp.explorer.FileActionSheetController

class RecentFragment : Fragment(), FileActionSheetController.Callbacks {
    private val username: String
        get() = arguments?.getString(ARG_USERNAME).orEmpty()

    private lateinit var viewModel: RecentViewModel
    private val navigationViewModel: MainNavigationViewModel by activityViewModels()
    private lateinit var adapter: RecentAdapter
    private lateinit var quickAccessSection: LinearLayout
    private lateinit var quickAccessRow: LinearLayout
    private lateinit var recentTabButton: TextView
    private lateinit var favoritesTabButton: TextView
    private lateinit var emptyStateContainer: View
    private lateinit var emptyStateIcon: android.widget.ImageView
    private lateinit var emptyStateTitle: TextView
    private lateinit var emptyStateSubtitle: TextView
    private lateinit var aiLoadingOverlay: View
    private lateinit var aiLoadingAnimation: com.airbnb.lottie.LottieAnimationView
    private lateinit var explorerRepository: com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
    private lateinit var recentOpenLocalRepository: com.example.filemanagementapp.data.local.recent.RecentOpenLocalRepository
    private lateinit var actionSheetController: FileActionSheetController

    private val previewLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == com.example.filemanagementapp.preview.FilePreviewActivity.RESULT_ACTION_FAVORITE) {
            val item = result.data?.getSerializableExtra(com.example.filemanagementapp.preview.FilePreviewActivity.EXTRA_EXPLORER_ITEM) as? ExplorerItem
            if (item != null) onFavorite(item)
        }
        if (result.resultCode == com.example.filemanagementapp.preview.FilePreviewActivity.RESULT_ACTION_AI) {
            val item = result.data?.getSerializableExtra(com.example.filemanagementapp.preview.FilePreviewActivity.EXTRA_EXPLORER_ITEM) as? ExplorerItem
            if (item != null) onAnalyzeAi(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_recent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appDatabase = AppDatabase.getInstance(requireContext())
        recentOpenLocalRepository = com.example.filemanagementapp.data.local.recent.RecentOpenLocalRepository(appDatabase.recentOpenDao())

        viewModel = ViewModelProvider(
            this,
            RecentViewModel.Factory(
                username = username,
                repository = RecentRepository(
                    directoryCacheLocalRepository = DirectoryCacheLocalRepository(
                        directoryCacheDao = appDatabase.directoryCacheDao(),
                        gson = ExplorerNetworkModule.gson
                    ),
                    favoriteLocalRepository = FavoriteLocalRepository(
                        favoriteItemDao = appDatabase.favoriteItemDao()
                    ),
                    aiAnalysisLocalRepository = AiAnalysisLocalRepository(
                        aiAnalysisCacheDao = appDatabase.aiAnalysisCacheDao(),
                        gson = ExplorerNetworkModule.gson
                    ),
                    recentOpenLocalRepository = recentOpenLocalRepository
                )
            )
        )[RecentViewModel::class.java]

        val recyclerView = view.findViewById<RecyclerView>(R.id.recentRecyclerView)
        quickAccessSection = view.findViewById(R.id.quickAccessSection)
        quickAccessRow = view.findViewById(R.id.quickAccessRow)
        recentTabButton = view.findViewById(R.id.recentTabButton)
        favoritesTabButton = view.findViewById(R.id.favoritesTabButton)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        emptyStateIcon = view.findViewById(R.id.emptyStateIcon)
        emptyStateTitle = view.findViewById(R.id.emptyStateTitle)
        emptyStateSubtitle = view.findViewById(R.id.emptyStateSubtitle)
        aiLoadingOverlay = view.findViewById(R.id.aiLoadingOverlay)
        aiLoadingAnimation = view.findViewById(R.id.aiLoadingAnimation)

        adapter = RecentAdapter(
            items = emptyList(),
            onItemClick = ::handleItemClick,
            onMoreClick = ::handleMoreClick
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        recentTabButton.setOnClickListener { viewModel.setTab(RecentTab.RECENT) }
        favoritesTabButton.setOnClickListener { viewModel.setTab(RecentTab.FAVORITES) }

        explorerRepository = com.example.filemanagementapp.data.explorer.repository.ExplorerRepository(
            appContext = requireContext().applicationContext,
            explorerApiService = ExplorerNetworkModule.explorerApiService,
            gson = ExplorerNetworkModule.gson
        )

        actionSheetController = FileActionSheetController(
            rootView = view,
            lifecycleOwner = viewLifecycleOwner,
            explorerRepository = explorerRepository,
            username = username,
            currentFolderProvider = { "" },
            callbacks = this
        )

        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: RecentUiState) {
        renderTabState(state.activeTab)
        quickAccessSection.isVisible = state.activeTab == RecentTab.RECENT && state.quickAccess.isNotEmpty()
        renderQuickAccess(state.quickAccess)
        adapter.submitItems(state.listItems)
        emptyStateContainer.isVisible = state.listItems.isEmpty()
        
        if (state.listItems.isEmpty()) {
            if (state.activeTab == RecentTab.RECENT) {
                emptyStateIcon.setImageResource(R.drawable.clock)
                emptyStateTitle.setText(R.string.recent_empty_title)
                emptyStateSubtitle.setText(R.string.recent_empty_subtitle)
            } else {
                emptyStateIcon.setImageResource(R.drawable.star)
                emptyStateTitle.setText(R.string.favorite_empty_title)
                emptyStateSubtitle.setText(R.string.favorite_empty_subtitle)
            }
        }
    }

    private fun renderTabState(activeTab: RecentTab) {
        val context = requireContext()
        val selectedText = ContextCompat.getColor(context, R.color.recent_primary)
        val unselectedText = ContextCompat.getColor(context, R.color.recent_text_secondary)
        val isRecent = activeTab == RecentTab.RECENT

        recentTabButton.setTextColor(if (isRecent) selectedText else unselectedText)
        favoritesTabButton.setTextColor(if (isRecent) unselectedText else selectedText)

        val recentContainer = recentTabButton.parent as? com.google.android.material.card.MaterialCardView
        val favoritesContainer = favoritesTabButton.parent as? com.google.android.material.card.MaterialCardView
        val selectedBg = ContextCompat.getColor(context, R.color.recent_surface)
        recentContainer?.setCardBackgroundColor(
            if (isRecent) selectedBg else android.graphics.Color.TRANSPARENT
        )
        favoritesContainer?.setCardBackgroundColor(
            if (isRecent) android.graphics.Color.TRANSPARENT else selectedBg
        )
        recentContainer?.cardElevation = if (isRecent) 2f else 0f
        favoritesContainer?.cardElevation = if (isRecent) 0f else 2f
    }

    private fun renderQuickAccess(items: List<RecentItem>) {
        val context = requireContext()
        quickAccessRow.removeAllViews()
        val inflater = LayoutInflater.from(context)
        items.forEach { item ->
            val itemView = inflater.inflate(R.layout.item_quick_access, quickAccessRow, false)
            val card = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.quickAccessCard)
            val icon = itemView.findViewById<android.widget.ImageView>(R.id.quickAccessIcon)
            val preview = itemView.findViewById<android.widget.ImageView>(R.id.quickAccessImagePreview)
            val nameText = itemView.findViewById<android.widget.TextView>(R.id.quickAccessName)

            nameText.text = item.name.substringBeforeLast('.')

            if (item.isImagePreviewable && !item.previewUrl.isNullOrBlank()) {
                icon.visibility = View.GONE
                preview.visibility = View.VISIBLE
                card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                preview.load(item.previewUrl) {
                    crossfade(true)
                    setHeader("ngrok-skip-browser-warning", "69420")
                    placeholder(R.drawable.explorer_file_preview_placeholder)
                    error(R.drawable.explorer_file_preview_placeholder)
                }
            } else {
                preview.visibility = View.GONE
                icon.visibility = View.VISIBLE
                if (item.kind == RecentItem.Kind.FOLDER) {
                    card.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF8E1"))
                    icon.setImageResource(R.drawable.folder)
                    icon.setColorFilter(android.graphics.Color.parseColor("#FFB300"))
                } else {
                    if (item.name.endsWith(".pdf", ignoreCase = true)) {
                        card.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                        icon.setImageResource(R.drawable.file_text)
                        icon.setColorFilter(android.graphics.Color.parseColor("#E53935"))
                    } else if (item.name.endsWith(".doc", ignoreCase = true) || item.name.endsWith(".docx", ignoreCase = true)) {
                        card.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F4FD"))
                        icon.setImageResource(R.drawable.file_text)
                        icon.setColorFilter(android.graphics.Color.parseColor("#1E88E5"))
                    } else if (item.fileType == RecentItem.FileType.VIDEO || item.name.endsWith(".mp4", ignoreCase = true)) {
                        card.setCardBackgroundColor(android.graphics.Color.parseColor("#F3E5F5"))
                        icon.setImageResource(R.drawable.film)
                        icon.setColorFilter(android.graphics.Color.parseColor("#8E24AA"))
                    } else if (item.fileType == RecentItem.FileType.AUDIO || item.name.endsWith(".mp3", ignoreCase = true) || item.name.endsWith(".wav", ignoreCase = true)) {
                        card.setCardBackgroundColor(android.graphics.Color.parseColor("#E0F2F1"))
                        icon.setImageResource(R.drawable.file_audio)
                        icon.setColorFilter(android.graphics.Color.parseColor("#00897B"))
                    } else {
                        card.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                        icon.setImageResource(R.drawable.file_text)
                        icon.setColorFilter(android.graphics.Color.parseColor("#43A047"))
                    }
                }
            }

            itemView.setOnClickListener { handleItemClick(item) }
            quickAccessRow.addView(itemView)
        }
    }

    private fun handleItemClick(item: RecentItem) {
        lifecycleScope.launch {
            recentOpenLocalRepository.recordOpen(username, item.path)
        }
        if (item.kind == RecentItem.Kind.FOLDER) {
            navigationViewModel.requestOpenFolder(item.path)
            return
        }
        previewLauncher.launch(
            FilePreviewActivity.newIntent(
                context = requireContext(),
                item = item.toExplorerItem(),
                username = username,
                analyzedImagePath = item.analyzedImagePath,
                ocrText = item.ocrSnippet,
                aiTags = item.aiTags,
                showAiPanel = false
            )
        )
    }

    private fun handleMoreClick(item: RecentItem) {
        actionSheetController.show(
            item.toExplorerItem(),
            FileActionSheetController.ActionConfig(
                showOpen = true,
                showDownload = item.kind == RecentItem.Kind.FILE,
                showRename = true,
                showMove = true,
                showFavorite = true,
                showDelete = true,
                showAi = item.isImagePreviewable
            )
        )
    }

    override fun onOpen(item: ExplorerItem) {
        lifecycleScope.launch {
            recentOpenLocalRepository.recordOpen(username, item.path)
        }
        if (item.type == ExplorerItem.Type.FOLDER) {
            Toast.makeText(requireContext(), R.string.error_ai_only_file, Toast.LENGTH_SHORT).show()
            return
        }
        val previewUrl = item.previewUrl
        if (previewUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.error_ai_no_file_path, Toast.LENGTH_SHORT).show()
            return
        }
        val recentItem = viewModel.uiState.value.listItems
            .mapNotNull { if (it is RecentListItem.Entry) it.item else null }
            .find { it.id == item.id } ?: return
        handleItemClick(recentItem)
    }

    override fun onDownload(item: ExplorerItem) {
        val downloadUrl = item.previewUrl ?: return
        ExplorerDownloadService.start(
            context = requireContext(),
            fileName = item.name,
            downloadUrl = downloadUrl
        )
        Toast.makeText(requireContext(), R.string.explorer_download_started, Toast.LENGTH_SHORT).show()
    }

    override fun onRename(item: ExplorerItem, newName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            explorerRepository.renameItem(username, item, newName).onSuccess { message ->
                val appDatabase = AppDatabase.getInstance(requireContext())
                appDatabase.directoryCacheDao()
                    .deleteByFolder(username, item.path.substringBeforeLast('/', ""))
                    
                val parentFolder = item.path.substringBeforeLast('/', "")
                val newPath = if (parentFolder.isEmpty()) newName else "$parentFolder/$newName"
                val isFolder = item.type == ExplorerItem.Type.FOLDER
                
                val favoriteLocalRepository = com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository(appDatabase.favoriteItemDao())
                favoriteLocalRepository.updatePath(username, item.path, newPath, isFolder)
                
                val aiAnalysisLocalRepository = com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository(appDatabase.aiAnalysisCacheDao(), com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson)
                aiAnalysisLocalRepository.updatePath(username, item.path, newPath, isFolder)
                
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.load()
            }.onFailure { throwable ->
                Toast.makeText(requireContext(), throwable.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMove(item: ExplorerItem, targetPath: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val normalizedTarget = targetPath.trim().trim('/')
            explorerRepository.moveItem(username, item, normalizedTarget).onSuccess { message ->
                val appDatabase = AppDatabase.getInstance(requireContext())
                appDatabase.directoryCacheDao()
                    .deleteByFolder(username, item.path.substringBeforeLast('/', ""))
                    
                val itemName = item.name
                val newPath = if (normalizedTarget.isEmpty()) itemName else "$normalizedTarget/$itemName"
                val isFolder = item.type == ExplorerItem.Type.FOLDER
                
                val favoriteLocalRepository = com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository(appDatabase.favoriteItemDao())
                favoriteLocalRepository.updatePath(username, item.path, newPath, isFolder)
                
                val aiAnalysisLocalRepository = com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository(appDatabase.aiAnalysisCacheDao(), com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.gson)
                aiAnalysisLocalRepository.updatePath(username, item.path, newPath, isFolder)
                    
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.load()
            }.onFailure { throwable ->
                Toast.makeText(requireContext(), throwable.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onFavorite(item: ExplorerItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository(
                AppDatabase.getInstance(requireContext()).favoriteItemDao()
            )
            val isFavorite = repo.toggleFavorite(username, item)
            Toast.makeText(
                requireContext(),
                if (isFavorite) R.string.msg_added_favorites else R.string.msg_removed_favorites,
                Toast.LENGTH_SHORT
            ).show()
            viewModel.load()
        }
    }

    override fun onDelete(item: ExplorerItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            explorerRepository.createFolder(username = username, targetPath = "", folderName = "trash")
            explorerRepository.moveItem(username, item, "trash").onSuccess {
                AppDatabase.getInstance(requireContext()).favoriteItemDao()
                    .deleteByPath(username, item.path)
                Toast.makeText(requireContext(), R.string.msg_moved_to_trash, Toast.LENGTH_SHORT).show()
                viewModel.load()
            }.onFailure { throwable ->
                Toast.makeText(requireContext(), throwable.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onAnalyzeAi(item: ExplorerItem) {
        if (item.type != ExplorerItem.Type.FILE) {
            Toast.makeText(requireContext(), R.string.error_ai_only_file, Toast.LENGTH_SHORT).show()
            return
        }
        val previewUrl = item.previewUrl
        if (previewUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.error_ai_no_file_path, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), getString(R.string.search_ai_scheduled, item.name), Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            aiLoadingOverlay.visibility = android.view.View.VISIBLE
            aiLoadingAnimation.playAnimation()

            val aiRepo = com.example.filemanagementapp.data.ai.repository.AiAnalysisRepository(
                requireContext().applicationContext,
                com.example.filemanagementapp.data.ai.network.AiNetworkModule.aiApiService,
                com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule.okHttpClient,
                com.example.filemanagementapp.data.ai.network.AiNetworkModule.gson
            )
            val appDb = com.example.filemanagementapp.data.local.AppDatabase.getInstance(requireContext())
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
                viewModel.load()
                aiLoadingOverlay.visibility = android.view.View.GONE
                aiLoadingAnimation.cancelAnimation()
            }.onFailure { throwable ->
                aiLoadingOverlay.visibility = android.view.View.GONE
                aiLoadingAnimation.cancelAnimation()
                Toast.makeText(requireContext(), throwable.message ?: "Analysis failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ARG_USERNAME = "arg_username"

        fun newInstance(username: String): RecentFragment {
            return RecentFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                }
            }
        }
    }
}
