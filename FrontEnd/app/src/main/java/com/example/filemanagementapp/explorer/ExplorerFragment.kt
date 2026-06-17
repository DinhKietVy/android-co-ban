package com.example.filemanagementapp.explorer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.ai.network.AiNetworkModule
import com.example.filemanagementapp.data.ai.repository.AiAnalysisRepository
import com.example.filemanagementapp.data.local.AppDatabase
import com.example.filemanagementapp.data.local.ai.AiAnalysisLocalRepository
import com.example.filemanagementapp.data.local.explorer.DirectoryCacheLocalRepository
import com.example.filemanagementapp.data.local.favorite.FavoriteLocalRepository
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.download.ExplorerDownloadProgress
import com.example.filemanagementapp.download.ExplorerDownloadProgressStore
import com.example.filemanagementapp.download.ExplorerDownloadService
import com.example.filemanagementapp.explorer.ui.ExplorerUiEvent
import com.example.filemanagementapp.explorer.ui.ExplorerUiState
import com.example.filemanagementapp.explorer.ui.ExplorerViewModel
import com.example.filemanagementapp.explorer.ui.SortOption
import com.example.filemanagementapp.main.MainNavigationViewModel
import com.example.filemanagementapp.preview.FilePreviewActivity
import com.example.filemanagementapp.search.SearchActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ExplorerFragment : Fragment(), FileActionSheetController.Callbacks {
    private val username: String
        get() = requireArguments().getString(ARG_USERNAME).orEmpty()

    private lateinit var viewModel: ExplorerViewModel
    private val navigationViewModel: MainNavigationViewModel by activityViewModels()
    private lateinit var explorerRepository: ExplorerRepository
    private lateinit var actionSheetController: FileActionSheetController
    private lateinit var breadcrumbAdapter: ExplorerBreadcrumbAdapter
    private lateinit var explorerAdapter: ExplorerAdapter
    private lateinit var homeIcon: ImageView
    private lateinit var homeSeparator: ImageView
    private lateinit var headerSearchButton: ImageButton
    private lateinit var headerMoreButton: ImageButton
    private lateinit var breadcrumbRecyclerView: RecyclerView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var aiLoadingOverlay: View
    private lateinit var aiLoadingAnimation: LottieAnimationView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyStateContainer: View
    private lateinit var storageValueText: TextView
    private lateinit var sortAndSelectContainer: View
    private lateinit var sortByText: TextView
    private lateinit var selectModeText: TextView
    private lateinit var createFab: FloatingActionButton
    private lateinit var downloadProgressCard: View
    private lateinit var downloadProgressRing: com.google.android.material.progressindicator.CircularProgressIndicator
    private lateinit var downloadProgressPercentText: TextView
    private lateinit var downloadProgressFileName: TextView
    private lateinit var downloadProgressMetaText: TextView
    private lateinit var selectionActionCard: View
    private lateinit var selectionSummaryText: TextView
    private lateinit var moveSelectedButton: View
    private lateinit var deleteSelectedButton: View
    private var currentDownloadProgress: ExplorerDownloadProgress? = null
    private var pendingDownloadItem: ExplorerItem? = null
    private val uploadFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.uploadFile(it)
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val item = pendingDownloadItem
        pendingDownloadItem = null
        if (item == null) {
            return@registerForActivityResult
        }

        if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startDownload(item)
        } else {
            Toast.makeText(
                requireContext(),
                R.string.explorer_download_notification_permission_denied,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private val searchActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val folderPath = result.data?.getStringExtra(com.example.filemanagementapp.search.SearchActivity.EXTRA_RESULT_FOLDER_PATH)
            if (folderPath != null) {
                viewModel.loadDirectory(folderPath)
            }
        }
    }
    
    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            android.app.Activity.RESULT_OK -> {
                viewModel.refreshCurrentDirectory()
            }
            FilePreviewActivity.RESULT_ACTION_FAVORITE -> {
                val item = result.data?.getSerializableExtra(FilePreviewActivity.EXTRA_EXPLORER_ITEM) as? ExplorerItem
                if (item != null) viewModel.toggleFavorite(item)
            }
            FilePreviewActivity.RESULT_ACTION_AI -> {
                val item = result.data?.getSerializableExtra(FilePreviewActivity.EXTRA_EXPLORER_ITEM) as? ExplorerItem
                if (item != null) viewModel.analyzeItem(item)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_explorer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        explorerRepository = ExplorerRepository(
            appContext = requireContext().applicationContext,
            explorerApiService = ExplorerNetworkModule.explorerApiService,
            gson = ExplorerNetworkModule.gson
        )
        val appDatabase = AppDatabase.getInstance(requireContext())
        viewModel = ViewModelProvider(
            this,
            ExplorerViewModel.Factory(
                username = username,
                repository = explorerRepository,
                aiAnalysisRepository = AiAnalysisRepository(
                    context = requireContext().applicationContext,
                    aiApiService = AiNetworkModule.aiApiService,
                    okHttpClient = ExplorerNetworkModule.okHttpClient,
                    gson = AiNetworkModule.gson
                ),
                aiAnalysisLocalRepository = AiAnalysisLocalRepository(
                    aiAnalysisCacheDao = appDatabase.aiAnalysisCacheDao(),
                    gson = ExplorerNetworkModule.gson
                ),
                favoriteLocalRepository = FavoriteLocalRepository(
                    favoriteItemDao = appDatabase.favoriteItemDao()
                ),
                directoryCacheLocalRepository = DirectoryCacheLocalRepository(
                    directoryCacheDao = appDatabase.directoryCacheDao(),
                    gson = ExplorerNetworkModule.gson
                ),
                settingsPreferencesRepository = com.example.filemanagementapp.data.local.profile.SettingsPreferencesRepository(requireContext())
            )
        )[ExplorerViewModel::class.java]

        homeIcon = view.findViewById(R.id.homeBreadcrumbIcon)
        homeSeparator = view.findViewById(R.id.homeBreadcrumbSeparator)
        headerSearchButton = view.findViewById(R.id.explorerHeaderSearchButton)
        headerMoreButton = view.findViewById(R.id.explorerHeaderMoreButton)
        breadcrumbRecyclerView = view.findViewById(R.id.breadcrumbRecyclerView)
        swipeRefreshLayout = view.findViewById(R.id.explorerSwipeRefresh)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        recyclerView = view.findViewById(R.id.explorerRecyclerView)
        loadingIndicator = view.findViewById(R.id.explorerLoadingIndicator)
        aiLoadingOverlay = view.findViewById(R.id.aiLoadingOverlay)
        aiLoadingAnimation = view.findViewById(R.id.aiLoadingAnimation)
        storageValueText = view.findViewById(R.id.explorerStorageValueText)
        sortAndSelectContainer = view.findViewById(R.id.sortAndSelectContainer)
        sortByText = view.findViewById(R.id.sortByText)
        selectModeText = view.findViewById(R.id.selectModeText)
        createFab = view.findViewById(R.id.createFab)
        downloadProgressCard = view.findViewById(R.id.downloadProgressCard)
        downloadProgressRing = view.findViewById(R.id.downloadProgressRing)
        downloadProgressPercentText = view.findViewById(R.id.downloadProgressPercentText)
        downloadProgressFileName = view.findViewById(R.id.downloadProgressFileName)
        downloadProgressMetaText = view.findViewById(R.id.downloadProgressMetaText)
        selectionActionCard = view.findViewById(R.id.selectionActionCard)
        selectionSummaryText = view.findViewById(R.id.selectionSummaryText)
        moveSelectedButton = view.findViewById(R.id.moveSelectedButton)
        deleteSelectedButton = view.findViewById(R.id.deleteSelectedButton)

        actionSheetController = FileActionSheetController(
            rootView = view,
            lifecycleOwner = viewLifecycleOwner,
            explorerRepository = explorerRepository,
            username = username,
            currentFolderProvider = { viewModel.uiState.value.currentFolder },
            callbacks = this
        )

        breadcrumbAdapter = ExplorerBreadcrumbAdapter { breadcrumb ->
            viewModel.loadDirectory(breadcrumb.path)
        }
        breadcrumbRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        breadcrumbRecyclerView.adapter = breadcrumbAdapter

        explorerAdapter = ExplorerAdapter(
            onItemClick = { item -> onOpen(item) },
            onMoreClick = { item -> 
                actionSheetController.show(
                    item = item,
                    config = FileActionSheetController.ActionConfig(
                        showOpen = item.type == ExplorerItem.Type.FOLDER,
                        showDownload = item.type == ExplorerItem.Type.FILE,
                        showRename = true,
                        showMove = true,
                        showFavorite = true,
                        showDelete = true,
                        showAi = item.type == ExplorerItem.Type.FILE
                    )
                )
            },
            onItemSelectionToggle = { item -> viewModel.toggleItemSelection(item) },
            onItemLongPress = { item -> viewModel.startSelection(item) }
        )
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.adapter = explorerAdapter

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshCurrentDirectory()
        }

        homeIcon.setOnClickListener {
            viewModel.loadRootDirectory()
        }
        headerSearchButton.setOnClickListener {
            searchActivityLauncher.launch(SearchActivity.newIntent(requireContext(), username))
        }
        headerMoreButton.setOnClickListener {
            showOverflowMenu(it, viewModel.uiState.value)
        }
        selectModeText.setOnClickListener {
            viewModel.toggleSelectionMode()
        }
        createFab.setOnClickListener {
            showCreateActionsSheet()
        }
        moveSelectedButton.setOnClickListener {
            showMoveSelectedDialog()
        }
        deleteSelectedButton.setOnClickListener {
            showDeleteSelectedDialog()
        }

        observeUiState()

        if (savedInstanceState == null) {
            viewModel.loadRootDirectory()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ExplorerUiEvent.ShowMessage -> {
                            Toast.makeText(requireContext(), event.message.asString(requireContext()), Toast.LENGTH_SHORT).show()
                        }

                        is ExplorerUiEvent.OpenPreview -> {
                            previewLauncher.launch(
                                FilePreviewActivity.newIntent(
                                    context = requireContext(),
                                    item = event.item,
                                    username = event.username,
                                    analyzedImagePath = event.analyzedImagePath,
                                    ocrText = event.ocrText,
                                    aiTags = event.aiTags,
                                    showAiPanel = event.showAiPanel
                                )
                            )
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigationViewModel.openFolderRequests.collect { folderPath ->
                    viewModel.loadDirectory(folderPath)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ExplorerDownloadProgressStore.progress.collect { progress ->
                    currentDownloadProgress = progress
                    renderDownloadProgressCard(
                        progress = progress,
                        hasSelectionCard = viewModel.uiState.value.isSelectionMode &&
                            viewModel.uiState.value.selectedPaths.isNotEmpty()
                    )
                }
            }
        }
    }

    private fun render(state: ExplorerUiState) {
        loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        aiLoadingOverlay.visibility = if (state.isAnalyzingAi) View.VISIBLE else View.GONE
        if (state.isAnalyzingAi) {
            aiLoadingAnimation.playAnimation()
        } else {
            aiLoadingAnimation.cancelAnimation()
        }
        recyclerView.alpha = if (state.isLoading || state.isAnalyzingAi) 0.5f else 1f
        swipeRefreshLayout.isRefreshing = state.isRefreshing
        swipeRefreshLayout.isEnabled = !state.isAnalyzingAi && !state.isSelectionMode
        storageValueText.text = state.storageSummary
        val isListEmpty = state.items.isEmpty()
        
        emptyStateContainer.visibility = if (isListEmpty && !state.isLoading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isListEmpty && !state.isLoading) View.GONE else View.VISIBLE
        
        sortAndSelectContainer.visibility = if (isListEmpty) View.GONE else View.VISIBLE
        updateListLayoutManager(state.displayMode)
        val sortLabel = when (state.sortOption) {
            SortOption.NAME -> getString(R.string.explorer_sort_name)
            SortOption.DATE_MODIFIED -> getString(R.string.explorer_sort_date)
            SortOption.SIZE -> getString(R.string.explorer_sort_size)
        }
        sortByText.visibility = View.VISIBLE
        sortByText.text = sortLabel
        selectModeText.visibility = if (state.items.isEmpty()) View.GONE else View.VISIBLE
        createFab.visibility = if (state.isSelectionMode) View.GONE else View.VISIBLE
        selectionActionCard.visibility =
            if (state.isSelectionMode && state.selectedPaths.isNotEmpty()) View.VISIBLE else View.GONE
        renderDownloadProgressCard(
            progress = currentDownloadProgress,
            hasSelectionCard = selectionActionCard.visibility == View.VISIBLE
        )
        selectionSummaryText.text = getString(
            R.string.explorer_selected_count,
            state.selectedPaths.size
        )
        selectModeText.text = if (state.isSelectionMode) {
            if (state.selectedPaths.isEmpty()) {
                getString(R.string.explorer_select_done)
            } else {
                getString(R.string.explorer_select_done_count, state.selectedPaths.size)
            }
        } else {
            getString(R.string.explorer_select)
        }
        explorerAdapter.submitItems(
            newItems = state.items,
            isSelectionMode = state.isSelectionMode,
            selectedPaths = state.selectedPaths,
            displayMode = state.displayMode
        )
        breadcrumbAdapter.submitItems(state.breadcrumbs)
        homeSeparator.visibility = if (state.breadcrumbs.isEmpty()) View.GONE else View.VISIBLE

        state.errorMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onOpen(item: ExplorerItem) {
        when (item.type) {
            ExplorerItem.Type.FOLDER -> viewModel.loadDirectory(item.path)
            ExplorerItem.Type.FILE -> openFilePreview(item, showAiPanel = false)
        }
    }

    override fun onDownload(item: ExplorerItem) {
        if (item.type == ExplorerItem.Type.FOLDER) {
            Toast.makeText(requireContext(), R.string.explorer_download_folder_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        if (item.previewUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.explorer_download_start_failed, Toast.LENGTH_SHORT).show()
            return
        }
        if (currentDownloadProgress != null) {
            Toast.makeText(requireContext(), R.string.explorer_download_already_running, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingDownloadItem = item
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startDownload(item)
    }

    override fun onRename(item: ExplorerItem, newName: String) {
        viewModel.renameItem(item, newName)
    }

    override fun onMove(item: ExplorerItem, targetPath: String) {
        viewModel.moveItem(item, targetPath)
    }

    override fun onFavorite(item: ExplorerItem) {
        viewModel.toggleFavorite(item)
    }

    override fun onDelete(item: ExplorerItem) {
        viewModel.deleteItem(item)
    }

    override fun onAnalyzeAi(item: ExplorerItem) {
        viewModel.analyzeItem(item)
    }

    private fun startDownload(item: ExplorerItem) {
        val downloadUrl = item.previewUrl ?: return
        ExplorerDownloadService.start(
            context = requireContext(),
            fileName = item.name,
            downloadUrl = downloadUrl
        )
        Toast.makeText(requireContext(), R.string.explorer_download_started, Toast.LENGTH_SHORT).show()
    }

    private fun renderDownloadProgressCard(
        progress: ExplorerDownloadProgress?,
        hasSelectionCard: Boolean
    ) {
        if (progress == null) {
            downloadProgressCard.visibility = View.GONE
            return
        }

        downloadProgressCard.visibility = View.VISIBLE
        val layoutParams = downloadProgressCard.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.bottomMargin = if (hasSelectionCard) 112.dp() else 24.dp()
        downloadProgressCard.layoutParams = layoutParams

        downloadProgressRing.max = 100
        downloadProgressRing.progress = progress.progressPercent
        downloadProgressPercentText.text = getString(
            R.string.explorer_download_progress_percent,
            progress.progressPercent
        )
        downloadProgressFileName.text = progress.fileName
        downloadProgressMetaText.text = if (progress.totalBytes != null && progress.totalBytes > 0L) {
            getString(
                R.string.explorer_download_progress_meta,
                Formatter.formatShortFileSize(requireContext(), progress.downloadedBytes),
                Formatter.formatShortFileSize(requireContext(), progress.totalBytes)
            )
        } else {
            getString(R.string.explorer_download_preparing)
        }
    }

    private fun openFilePreview(item: ExplorerItem, showAiPanel: Boolean) {
        if (item.type != ExplorerItem.Type.FILE) {
            return
        }
        previewLauncher.launch(
            FilePreviewActivity.newIntent(
                context = requireContext(),
                item = item,
                username = username,
                analyzedImagePath = item.analyzedImagePath,
                ocrText = item.ocrSnippet,
                aiTags = item.tags,
                showAiPanel = showAiPanel
            )
        )
    }

    private fun showOverflowMenu(anchor: View, state: ExplorerUiState) {
        val popupView = layoutInflater.inflate(R.layout.popup_explorer_overflow, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
        }

        popupView.findViewById<ImageView>(R.id.checkSortName).visibility =
            if (state.sortOption == SortOption.NAME) View.VISIBLE else View.GONE
        popupView.findViewById<ImageView>(R.id.checkSortDate).visibility =
            if (state.sortOption == SortOption.DATE_MODIFIED) View.VISIBLE else View.GONE
        popupView.findViewById<ImageView>(R.id.checkSortSize).visibility =
            if (state.sortOption == SortOption.SIZE) View.VISIBLE else View.GONE

        popupView.findViewById<TextView>(R.id.textViewMode).text =
            if (state.displayMode == ExplorerAdapter.DisplayMode.GRID) {
                getString(R.string.explorer_switch_to_list)
            } else {
                getString(R.string.explorer_switch_to_grid)
            }
        popupView.findViewById<ImageView>(R.id.iconViewMode).setImageResource(
            if (state.displayMode == ExplorerAdapter.DisplayMode.GRID) {
                R.drawable.list
            } else {
                R.drawable.layout_grid
            }
        )
        popupView.findViewById<TextView>(R.id.menuSelectModeText)?.text =
            if (state.isSelectionMode) getString(R.string.explorer_select_done) else getString(R.string.explorer_select)

        popupView.findViewById<View>(R.id.menuSelectMode).setOnClickListener {
            popupWindow.dismiss()
            viewModel.toggleSelectionMode()
        }
        popupView.findViewById<View>(R.id.menuSortName).setOnClickListener {
            popupWindow.dismiss()
            viewModel.setSortOption(SortOption.NAME)
        }
        popupView.findViewById<View>(R.id.menuSortDate).setOnClickListener {
            popupWindow.dismiss()
            viewModel.setSortOption(SortOption.DATE_MODIFIED)
        }
        popupView.findViewById<View>(R.id.menuSortSize).setOnClickListener {
            popupWindow.dismiss()
            viewModel.setSortOption(SortOption.SIZE)
        }
        popupView.findViewById<View>(R.id.menuViewMode).setOnClickListener {
            popupWindow.dismiss()
            viewModel.toggleDisplayMode()
        }

        popupWindow.showAsDropDown(anchor, -24.dp(), 8.dp(), Gravity.END)
    }

    private fun updateListLayoutManager(displayMode: ExplorerAdapter.DisplayMode) {
        val expectedGrid = displayMode == ExplorerAdapter.DisplayMode.GRID
        val isGrid = recyclerView.layoutManager is GridLayoutManager
        if (expectedGrid == isGrid) {
            return
        }

        recyclerView.layoutManager = if (expectedGrid) {
            GridLayoutManager(requireContext(), 2)
        } else {
            LinearLayoutManager(requireContext())
        }
    }

    private fun showCreateActionsSheet() {
        val contentView = layoutInflater.inflate(R.layout.bottom_sheet_create_actions, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(contentView)

        contentView.findViewById<View>(R.id.actionCreateFolder).setOnClickListener {
            dialog.dismiss()
            showCreateFolderDialog()
        }
        contentView.findViewById<View>(R.id.actionUploadFile).setOnClickListener {
            dialog.dismiss()
            uploadFileLauncher.launch(arrayOf("*/*"))
        }

        dialog.show()
    }

    private fun showCreateFolderDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val inputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialogInputLayout)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialogInputEditText)
        
        inputLayout.hint = getString(R.string.explorer_dialog_create_folder_hint)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explorer_dialog_create_folder_title)
            .setView(view)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                viewModel.createFolder(input.text?.toString().orEmpty())
            }
            .show()
    }

    private fun showMoveSelectedDialog() {
        val movingItems = viewModel.uiState.value.items.filter { it.path in viewModel.uiState.value.selectedPaths }
        actionSheetController.pickFolder(
            titleRes = R.string.explorer_dialog_move_selected_title,
            movingItems = movingItems,
            onFolderPicked = { folderPath ->
                viewModel.moveSelectedItems(folderPath)
            }
        )
    }

    private fun showDeleteSelectedDialog() {
        val selectedCount = viewModel.uiState.value.selectedPaths.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explorer_dialog_delete_selected_title)
            .setMessage(getString(R.string.explorer_dialog_delete_selected_message, selectedCount))
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                viewModel.deleteSelectedItems()
            }
            .show()
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ARG_USERNAME = "arg_username"

        fun newInstance(username: String): ExplorerFragment {
            return ExplorerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                }
            }
        }
    }
}
