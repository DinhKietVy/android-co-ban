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
                    okHttpClient = AiNetworkModule.okHttpClient,
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
                )
            )
        )[ExplorerViewModel::class.java]

        homeIcon = view.findViewById(R.id.homeBreadcrumbIcon)
        homeSeparator = view.findViewById(R.id.homeBreadcrumbSeparator)
        headerSearchButton = view.findViewById(R.id.explorerHeaderSearchButton)
        headerMoreButton = view.findViewById(R.id.explorerHeaderMoreButton)
        breadcrumbRecyclerView = view.findViewById(R.id.breadcrumbRecyclerView)
        swipeRefreshLayout = view.findViewById(R.id.explorerSwipeRefresh)
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
            onFolderClick = { item -> viewModel.loadDirectory(item.path) },
            onMoreClick = { item -> showActionSheet(item) },
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
            startActivity(SearchActivity.newIntent(requireContext()))
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
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }

                        is ExplorerUiEvent.OpenPreview -> {
                            startActivity(
                                FilePreviewActivity.newIntent(
                                    context = requireContext(),
                                    fileName = event.fileName,
                                    previewUrl = event.previewUrl,
                                    analyzedImagePath = event.analyzedImagePath,
                                    ocrText = event.ocrText,
                                    aiTags = event.aiTags,
                                    fileSize = event.fileSize,
                                    modified = event.modified,
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
        sortAndSelectContainer.visibility = if (state.items.isEmpty()) View.GONE else View.VISIBLE
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

    private fun setupBottomSheet(root: View) {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        bottomSheet.visibility = View.GONE
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            })
        }

        root.findViewById<ImageButton>(R.id.bsCloseButton).setOnClickListener {
            hideBottomSheet()
        }

        listOf(
            R.id.actionOpen,
            R.id.actionDownload,
            R.id.actionRename,
            R.id.actionMove,
            R.id.actionFavorite,
            R.id.actionDelete,
            R.id.actionAi
        ).forEach { actionId ->
            root.findViewById<View>(actionId).setOnClickListener { actionView ->
                handleSheetAction(actionView)
            }
        }
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
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.explorer_dialog_create_folder_hint)
            setPadding(24.dp(), 20.dp(), 24.dp(), 0)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explorer_dialog_create_folder_title)
            .setView(input)
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

    private fun showBottomSheet(item: ExplorerItem) {
        selectedItem = item
        bottomSheetFileName.text = item.name
        bottomSheetFileMeta.text = buildBottomSheetMeta(item)
        bottomSheetFileIcon.setImageResource(
            when (item.type) {
                ExplorerItem.Type.FOLDER -> R.drawable.folder
                ExplorerItem.Type.FILE -> item.fallbackIconRes ?: R.drawable.file_text
            }
        )
        actionFavoriteLabel.setText(
            if (item.isFavorite) {
                R.string.preview_action_unfavorite
            } else {
                R.string.preview_action_favorite
            }
        )
        actionAiView.visibility = if (item.type == ExplorerItem.Type.FILE) View.VISIBLE else View.GONE
        renderBottomSheetTags(item.tags)
        bottomSheet.visibility = View.VISIBLE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun hideBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun handleSheetAction(actionView: View) {
        val item = selectedItem ?: return
        when (actionView.id) {
            R.id.actionOpen -> {
                hideBottomSheet()
                handleOpen(item)
            }

            R.id.actionDownload -> {
                hideBottomSheet()
                handleDownload(item)
            }

            R.id.actionRename -> {
                hideBottomSheet()
                showRenameDialog(item)
            }

            R.id.actionMove -> {
                hideBottomSheet()
                showMoveDialog(item)
            }

            R.id.actionFavorite -> {
                hideBottomSheet()
                viewModel.toggleFavorite(item)
            }

            R.id.actionDelete -> {
                hideBottomSheet()
                showDeleteDialog(item)
            }

            R.id.actionAi -> {
                hideBottomSheet()
                viewModel.analyzeItem(item)
            }
        }
    }

    private fun handleOpen(item: ExplorerItem) {
        when (item.type) {
            ExplorerItem.Type.FOLDER -> viewModel.loadDirectory(item.path)
            ExplorerItem.Type.FILE -> openFilePreview(item, showAiPanel = false)
        }
    }

    private fun handleDownload(item: ExplorerItem) {
        if (item.type == ExplorerItem.Type.FOLDER) {
            Toast.makeText(
                requireContext(),
                R.string.explorer_download_folder_unsupported,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (item.previewUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.explorer_download_start_failed, Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (currentDownloadProgress != null) {
            Toast.makeText(
                requireContext(),
                R.string.explorer_download_already_running,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadItem = item
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        startDownload(item)
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
        startActivity(
            FilePreviewActivity.newIntent(
                context = requireContext(),
                fileName = item.name,
                previewUrl = item.previewUrl,
                analyzedImagePath = null,
                ocrText = null,
                aiTags = emptyList(),
                fileSize = item.size,
                modified = item.modified,
                showAiPanel = showAiPanel
            )
        )
    }

    private fun showRenameDialog(item: ExplorerItem) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(item.name)
            setSelection(item.name.length)
            hint = getString(R.string.explorer_dialog_rename_hint)
            setPadding(24.dp(), 20.dp(), 24.dp(), 0)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explorer_dialog_rename_title)
            .setView(input)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                viewModel.renameItem(item, input.text?.toString().orEmpty())
            }
            .show()
    }

    private fun showMoveDialog(item: ExplorerItem) {
        showFolderPickerDialog(
            titleRes = R.string.explorer_dialog_move_title,
            movingItems = listOf(item),
            onFolderPicked = { folderPath ->
                viewModel.moveItem(item, folderPath)
            }
        )
    }

    private fun showDeleteDialog(item: ExplorerItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explorer_dialog_delete_title)
            .setMessage(getString(R.string.explorer_dialog_delete_message, item.name))
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                viewModel.deleteItem(item)
            }
            .show()
    }

    private fun showFolderPickerDialog(
        titleRes: Int,
        movingItems: List<ExplorerItem>,
        onFolderPicked: (String) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_folder_picker, null)
        val pathText = dialogView.findViewById<TextView>(R.id.folderPickerPathText)
        val upButton = dialogView.findViewById<TextView>(R.id.folderPickerUpButton)
        val loadingView = dialogView.findViewById<ProgressBar>(R.id.folderPickerLoading)
        val emptyView = dialogView.findViewById<TextView>(R.id.folderPickerEmptyText)
        val invalidHintText = dialogView.findViewById<TextView>(R.id.folderPickerInvalidHintText)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderPickerRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        var currentPath = viewModel.uiState.value.currentFolder
        var loadJob: Job? = null
        lateinit var loadFolderPickerPath: (String) -> Unit
        var positiveButton: android.widget.Button? = null
        val adapter = FolderPickerAdapter { folder ->
            loadFolderPickerPath(folder.path)
        }
        recyclerView.adapter = adapter

        fun renderFolderPickerPath() {
            pathText.text = if (currentPath.isBlank()) {
                getString(R.string.explorer_folder_picker_current_root)
            } else {
                getString(R.string.explorer_folder_picker_current, currentPath)
            }
            upButton.visibility = if (currentPath.isBlank()) View.INVISIBLE else View.VISIBLE
        }

        loadFolderPickerPath = { targetPath ->
            currentPath = targetPath
            renderFolderPickerPath()
            val isInvalidTarget = isInvalidMoveTarget(targetPath, movingItems)
            invalidHintText.visibility = if (isInvalidTarget) View.VISIBLE else View.GONE
            positiveButton?.isEnabled = !isInvalidTarget
            loadJob?.cancel()
            loadJob = viewLifecycleOwner.lifecycleScope.launch {
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
                                    isEnabled = !isInvalidMoveTarget(folder.path, movingItems)
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

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(dialogView)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_folder_picker_move_here) { _, _ ->
                onFolderPicked(currentPath)
            }
            .show()

        positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)

        upButton.setOnClickListener {
            if (currentPath.isBlank()) return@setOnClickListener
            loadFolderPickerPath(currentPath.substringBeforeLast('/', ""))
        }

        dialog.setOnDismissListener {
            loadJob?.cancel()
        }

        loadFolderPickerPath(currentPath)
    }

    private fun isInvalidMoveTarget(
        targetPath: String,
        movingItems: List<ExplorerItem>
    ): Boolean {
        val normalizedTarget = targetPath.trim('/').lowercase()
        return movingItems
            .filter { it.type == ExplorerItem.Type.FOLDER }
            .map { it.path.trim('/').lowercase() }
            .any { sourcePath ->
                normalizedTarget == sourcePath ||
                    (normalizedTarget.isNotBlank() && normalizedTarget.startsWith("$sourcePath/"))
            }
    }

    private fun buildBottomSheetMeta(item: ExplorerItem): String {
        return when (item.type) {
            ExplorerItem.Type.FOLDER -> {
                val count = getString(R.string.explorer_item_count, item.itemCount ?: 0)
                getString(R.string.explorer_file_meta, count, item.modified)
            }

            ExplorerItem.Type.FILE -> {
                getString(
                    R.string.explorer_file_meta,
                    item.size.orEmpty(),
                    item.modified
                )
            }
        }
    }

    private fun renderBottomSheetTags(tags: List<String>) {
        bottomSheetTagsContainer.removeAllViews()
        if (tags.isEmpty()) {
            bottomSheetTagsContainer.visibility = View.GONE
            return
        }

        bottomSheetTagsContainer.visibility = View.VISIBLE
        tags.forEachIndexed { index, tag ->
            val tagView = TextView(requireContext()).apply {
                text = tag
                textSize = 11f
                setTextColor(resources.getColor(R.color.explorer_tag_text, null))
                setBackgroundResource(R.drawable.explorer_tag_background)
                setPadding(8.dp(), 3.dp(), 8.dp(), 3.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        marginStart = 6.dp()
                    }
                }
            }
            bottomSheetTagsContainer.addView(tagView)
        }
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
