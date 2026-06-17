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
    private lateinit var quickAccessContainer: HorizontalScrollView
    private lateinit var quickAccessRow: LinearLayout
    private lateinit var recentTabButton: TextView
    private lateinit var favoritesTabButton: TextView
    private lateinit var emptyStateContainer: View
    private lateinit var explorerRepository: com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
    private lateinit var actionSheetController: FileActionSheetController

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_recent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appDatabase = AppDatabase.getInstance(requireContext())
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
                    )
                )
            )
        )[RecentViewModel::class.java]

        val recyclerView = view.findViewById<RecyclerView>(R.id.recentRecyclerView)
        quickAccessContainer = view.findViewById(R.id.quickAccessContainer)
        quickAccessRow = view.findViewById(R.id.quickAccessRow)
        recentTabButton = view.findViewById(R.id.recentTabButton)
        favoritesTabButton = view.findViewById(R.id.favoritesTabButton)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)

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
        quickAccessContainer.isVisible = state.activeTab == RecentTab.RECENT && state.quickAccess.isNotEmpty()
        renderQuickAccess(state.quickAccess)
        adapter.submitItems(state.listItems)
        emptyStateContainer.isVisible = state.listItems.isEmpty()
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
        items.forEach { item ->
            val chip = TextView(context).apply {
                text = item.name.substringBeforeLast('.')
                setTextColor(ContextCompat.getColor(context, R.color.recent_text_secondary))
                textSize = 11f
                maxLines = 1
                background = ContextCompat.getDrawable(context, R.drawable.explorer_tag_background)
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.recent_surface)
                setPadding(20.dp(), 28.dp(), 20.dp(), 28.dp())
                setOnClickListener { handleItemClick(item) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12.dp() }
            }
            quickAccessRow.addView(chip)
        }
    }

    private fun handleItemClick(item: RecentItem) {
        if (item.kind == RecentItem.Kind.FOLDER) {
            navigationViewModel.requestOpenFolder(item.path)
            return
        }
        startActivity(
            FilePreviewActivity.newIntent(
                context = requireContext(),
                                item = item.toExplorerItem(),
                                username = username,
                                analyzedImagePath = null,
                                ocrText = null,
                                aiTags = emptyList(),
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
                showAi = item.kind == RecentItem.Kind.FILE
            )
        )
    }

    override fun onOpen(item: ExplorerItem) {
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
        val recentItem = viewModel.uiState.value.listItems
            .mapNotNull { if (it is RecentListItem.Entry) it.item else null }
            .find { it.id == item.id } ?: return
            
        startActivity(
            FilePreviewActivity.newIntent(
                context = requireContext(),
                item = recentItem.toExplorerItem(),
                username = username,
                analyzedImagePath = null,
                ocrText = recentItem.ocrSnippet,
                aiTags = recentItem.aiTags,
                showAiPanel = true
            )
        )
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
