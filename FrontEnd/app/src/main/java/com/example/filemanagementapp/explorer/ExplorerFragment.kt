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
import com.example.filemanagementapp.data.drive.auth.DriveAuthManager
import com.example.filemanagementapp.data.drive.local.DrivePreferencesRepository
import com.example.filemanagementapp.data.drive.repository.GoogleDriveRepository
import com.example.filemanagementapp.chat.AiChatBottomSheetFragment
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.documentfile.provider.DocumentFile

class ExplorerFragment : Fragment(), FileActionSheetController.Callbacks {
    private val username: String
        get() = requireArguments().getString(ARG_USERNAME).orEmpty()

    private lateinit var viewModel: ExplorerViewModel
    private val navigationViewModel: MainNavigationViewModel by activityViewModels()
    private lateinit var explorerRepository: ExplorerRepository
    private lateinit var recentOpenLocalRepository: com.example.filemanagementapp.data.local.recent.RecentOpenLocalRepository
    private lateinit var driveAuthManager: DriveAuthManager
    private lateinit var drivePreferencesRepository: DrivePreferencesRepository
    private val googleDriveRepository = GoogleDriveRepository()
    private lateinit var actionSheetController: FileActionSheetController
    private lateinit var breadcrumbAdapter: ExplorerBreadcrumbAdapter
    private lateinit var explorerAdapter: ExplorerAdapter
    private lateinit var homeIcon: ImageView
    private lateinit var homeSeparator: ImageView
    private lateinit var headerMenuButton: ImageButton
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
    private lateinit var progressContainer: LinearLayout
    private lateinit var uploadProgressCard: View
    private lateinit var uploadProgressRing: com.google.android.material.progressindicator.CircularProgressIndicator
    private lateinit var uploadProgressPercentText: TextView
    private lateinit var uploadProgressFileName: TextView
    private lateinit var uploadProgressMetaText: TextView
    private lateinit var selectionActionCard: View
    private lateinit var selectionSummaryText: TextView
    private lateinit var compressSelectedButton: View
    private lateinit var moveSelectedButton: View
    private lateinit var deleteSelectedButton: View
    private var currentDownloadProgress: ExplorerDownloadProgress? = null
    private var pendingDownloadItem: ExplorerItem? = null
    private var convertingDialog: androidx.appcompat.app.AlertDialog? = null

    private val googleDriveSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        driveAuthManager.parseSignInResult(result.data)
            .onSuccess { account ->
                viewLifecycleOwner.lifecycleScope.launch {
                    drivePreferencesRepository.saveConnectedAccount(
                        email = account.email.orEmpty(),
                        displayName = account.displayName
                    )
                    val driveService = driveAuthManager.getDriveService(account)
                    viewModel.setDriveService(driveService)
                    Toast.makeText(requireContext(), "Đã kết nối Google Drive (${account.email})", Toast.LENGTH_SHORT).show()
                    viewModel.loadDirectory("gdrive://root", "Google Drive")
                }
            }
            .onFailure { error ->
                Toast.makeText(requireContext(), "Kết nối Google Drive thất bại: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

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
            if (viewModel.uiState.value.currentFolder.startsWith("gdrive://")) {
                Toast.makeText(requireContext(), "Đang tải tệp lên Google Drive...", Toast.LENGTH_SHORT).show()
                viewModel.uploadToDrive(it, requireContext())
            } else {
                com.example.filemanagementapp.explorer.network.ExplorerUploadService.start(
                    context = requireContext(),
                    fileUri = it,
                    targetPath = viewModel.uiState.value.currentFolder,
                    username = username
                )
                Toast.makeText(requireContext(), R.string.msg_uploading_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val uploadFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            Toast.makeText(requireContext(), R.string.msg_uploading_file, Toast.LENGTH_SHORT).show()
            
            // Chạy duyệt thư mục dưới background để không làm đứng UI
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), treeUri)
                rootDoc?.let { doc ->
                    traverseAndUploadFolder(doc, "")
                }
            }
        }
    }

    private fun traverseAndUploadFolder(docFile: androidx.documentfile.provider.DocumentFile, relativePath: String) {
        val currentPath = if (relativePath.isEmpty()) docFile.name.orEmpty() else "$relativePath/${docFile.name.orEmpty()}"
        
        for (file in docFile.listFiles()) {
            if (file.isDirectory) {
                traverseAndUploadFolder(file, currentPath)
            } else if (file.isFile) {
                // Gửi từng file lên với targetPath là currentFolder (trên server) cộng với đường dẫn tương đối
                val serverTargetPath = viewModel.uiState.value.currentFolder.trim('/') + "/" + currentPath
                com.example.filemanagementapp.explorer.network.ExplorerUploadService.start(
                    context = requireContext(),
                    fileUri = file.uri,
                    targetPath = serverTargetPath,
                    username = username
                )
            }
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
                @OptIn(coil.annotation.ExperimentalCoilApi::class)
                coil.Coil.imageLoader(requireContext()).let { loader ->
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
                viewModel.refreshCurrentDirectory()
            }
            FilePreviewActivity.RESULT_ACTION_FAVORITE -> {
                val item = result.data?.getSerializableExtra(FilePreviewActivity.EXTRA_EXPLORER_ITEM) as? ExplorerItem
                if (item != null) viewModel.toggleFavorite(item)
            }
            FilePreviewActivity.RESULT_ACTION_AI -> {
                val item = result.data?.getSerializableExtra(FilePreviewActivity.EXTRA_EXPLORER_ITEM) as? ExplorerItem
                if (item != null) onAnalyzeAi(item)
            }
            FilePreviewActivity.RESULT_ACTION_EXTRACT -> {
                val item = result.data?.getSerializableExtra(FilePreviewActivity.EXTRA_EXPLORER_ITEM) as? ExplorerItem
                if (item != null) onExtract(item)
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
        recentOpenLocalRepository = com.example.filemanagementapp.data.local.recent.RecentOpenLocalRepository(appDatabase.recentOpenDao())
        
        driveAuthManager = DriveAuthManager(requireContext().applicationContext)
        drivePreferencesRepository = DrivePreferencesRepository(requireContext().applicationContext)

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
                settingsPreferencesRepository = com.example.filemanagementapp.data.local.profile.SettingsPreferencesRepository(requireContext()),
                googleDriveRepository = googleDriveRepository,
                drivePreferencesRepository = drivePreferencesRepository
            )
        )[ExplorerViewModel::class.java]

        val driveAccount = driveAuthManager.getSignedInAccount()
        if (driveAccount != null) {
            val service = driveAuthManager.getDriveService(driveAccount)
            viewModel.setDriveService(service)
        }

        homeIcon = view.findViewById(R.id.homeBreadcrumbIcon)
        homeSeparator = view.findViewById(R.id.homeBreadcrumbSeparator)
        headerMenuButton = view.findViewById(R.id.explorerHeaderMenuButton)
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
        progressContainer = view.findViewById(R.id.progressContainer)
        uploadProgressCard = view.findViewById(R.id.uploadProgressCard)
        uploadProgressRing = view.findViewById(R.id.uploadProgressRing)
        uploadProgressPercentText = view.findViewById(R.id.uploadProgressPercentText)
        uploadProgressFileName = view.findViewById(R.id.uploadProgressFileName)
        uploadProgressMetaText = view.findViewById(R.id.uploadProgressMetaText)
        selectionActionCard = view.findViewById(R.id.selectionActionCard)
        selectionSummaryText = view.findViewById(R.id.selectionSummaryText)
        compressSelectedButton = view.findViewById(R.id.compressSelectedButton)
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
            viewModel.loadDirectory(breadcrumb.path, breadcrumb.title)
        }
        breadcrumbRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        breadcrumbRecyclerView.adapter = breadcrumbAdapter

        explorerAdapter = ExplorerAdapter(
            onItemClick = { item -> onOpen(item) },
            onMoreClick = { item -> 
                if (item.isGoogleDriveItem) {
                    if (item.type == ExplorerItem.Type.FILE) {
                        showDriveFileActionsSheet(item)
                    } else if (item.id != "gdrive_root") {
                        showDriveFolderActionsSheet(item)
                    } else {
                        if (driveAuthManager.getSignedInAccount() != null) {
                            showDriveAccountSheet()
                        } else {
                            showDriveConnectSheet()
                        }
                    }
                } else {
                    actionSheetController.show(
                        item = item,
                        config = FileActionSheetController.ActionConfig(
                            showOpen = item.type == ExplorerItem.Type.FOLDER,
                            showDownload = true, // Cho phép tải xuống cả file và folder
                            showRename = true,
                            showMove = true,
                            showFavorite = true,
                            showDelete = true,
                            showAi = item.isImagePreviewable,
                            showExtract = true
                        )
                    )
                }
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
        headerMenuButton.setOnClickListener {
            (activity as? com.example.filemanagementapp.main.MainActivity)?.openDrawer()
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
        compressSelectedButton.setOnClickListener {
            showCompressSelectedDialog()
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

                        is ExplorerUiEvent.ConnectGoogleDrive -> {
                            showDriveConnectSheet()
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
                    updateProgressContainer()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                com.example.filemanagementapp.explorer.network.ExplorerUploadProgressStore.progress.collect { progress ->
                    renderUploadProgressCard(progress)
                    updateProgressContainer()
                    
                    if (progress != null && progress.status != com.example.filemanagementapp.explorer.network.UploadStatus.RUNNING) {
                        if (progress.status == com.example.filemanagementapp.explorer.network.UploadStatus.SUCCESS) {
                            viewModel.onUploadCompleted(progress.fileName)
                        } else if (progress.status == com.example.filemanagementapp.explorer.network.UploadStatus.FAILED) {
                            Toast.makeText(requireContext(), progress.errorMessage ?: "Upload Failed", Toast.LENGTH_SHORT).show()
                        }
                        
                        kotlinx.coroutines.delay(2000)
                        com.example.filemanagementapp.explorer.network.ExplorerUploadProgressStore.clear()
                    }
                }
            }
        }
    }

    private fun updateProgressContainer() {
        val hasSelectionCard = viewModel.uiState.value.isSelectionMode && viewModel.uiState.value.selectedPaths.isNotEmpty()
        val layoutParams = progressContainer.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.bottomMargin = if (hasSelectionCard) 112.dp() else 24.dp()
        progressContainer.layoutParams = layoutParams

        renderDownloadProgressCard(currentDownloadProgress)
    }

    private fun renderUploadProgressCard(progress: com.example.filemanagementapp.explorer.network.ExplorerUploadProgress?) {
        if (progress == null) {
            uploadProgressCard.visibility = View.GONE
            return
        }

        uploadProgressCard.visibility = View.VISIBLE
        uploadProgressRing.max = 100
        uploadProgressRing.progress = progress.progressPercent
        uploadProgressPercentText.text = getString(
            R.string.explorer_download_progress_percent,
            progress.progressPercent
        )
        uploadProgressFileName.text = progress.fileName
        uploadProgressMetaText.text = if (progress.totalBytes != null && progress.totalBytes > 0L) {
            getString(
                R.string.explorer_download_progress_meta,
                Formatter.formatShortFileSize(requireContext(), progress.uploadedBytes),
                Formatter.formatShortFileSize(requireContext(), progress.totalBytes)
            )
        } else {
            getString(R.string.explorer_download_preparing)
        }
    }

    private fun render(state: ExplorerUiState) {
        if (state.processingState != null) {
            if (convertingDialog == null) {
                convertingDialog = MaterialAlertDialogBuilder(requireContext())
                    .setView(R.layout.dialog_beautiful_loading)
                    .setCancelable(false)
                    .create()
                    .apply { window?.setBackgroundDrawableResource(android.R.color.transparent) }
            }
            if (convertingDialog?.isShowing == false) {
                convertingDialog?.show()
            }
            
            // Update texts based on processingState
            convertingDialog?.let { dialog ->
                val titleView = dialog.findViewById<TextView>(R.id.loadingTitle)
                val subtitleView = dialog.findViewById<TextView>(R.id.loadingSubtitle)
                
                when (state.processingState) {
                    com.example.filemanagementapp.explorer.ui.ProcessingState.CONVERTING -> {
                        titleView?.setText(R.string.dialog_converting_title)
                        subtitleView?.setText(R.string.dialog_converting_subtitle)
                    }
                    com.example.filemanagementapp.explorer.ui.ProcessingState.COMPRESSING -> {
                        titleView?.setText(R.string.dialog_compressing_title)
                        subtitleView?.setText(R.string.dialog_compressing_subtitle)
                    }
                    com.example.filemanagementapp.explorer.ui.ProcessingState.EXTRACTING -> {
                        titleView?.setText(R.string.dialog_extracting_title)
                        subtitleView?.setText(R.string.dialog_extracting_subtitle)
                    }
                }
            }
        } else {
            convertingDialog?.dismiss()
            convertingDialog = null
        }

        loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        aiLoadingOverlay.visibility = if (state.isAnalyzingAi) View.VISIBLE else View.GONE
        if (state.isAnalyzingAi) {
            aiLoadingAnimation.playAnimation()
        } else {
            aiLoadingAnimation.cancelAnimation()
        }
        recyclerView.alpha = if (state.isLoading || state.isAnalyzingAi || state.processingState != null) 0.5f else 1f
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
        updateProgressContainer()
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
        viewLifecycleOwner.lifecycleScope.launch {
            recentOpenLocalRepository.recordOpen(username, item.path)
        }
        if (item.id == "gdrive_root") {
            if (driveAuthManager.getSignedInAccount() != null) {
                viewModel.loadDirectory("gdrive://root", "Google Drive")
            } else {
                showDriveConnectSheet()
            }
            return
        }

        if (item.isGoogleDriveItem) {
            when (item.type) {
                ExplorerItem.Type.FOLDER -> viewModel.loadDirectory(item.path, item.name)
                ExplorerItem.Type.FILE -> showDriveFileActionsSheet(item)
            }
            return
        }

        when (item.type) {
            ExplorerItem.Type.FOLDER -> viewModel.loadDirectory(item.path, item.name)
            ExplorerItem.Type.FILE -> openFilePreview(item, showAiPanel = false)
        }
    }

    override fun onDownload(item: ExplorerItem) {
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

    override fun onAskAi(item: ExplorerItem) {
        AiChatBottomSheetFragment.newInstance(username, item.path)
            .show(parentFragmentManager, AiChatBottomSheetFragment.TAG)
    }

    override fun onConvert(item: ExplorerItem, targetFormat: String) {
        viewModel.convertFile(item, targetFormat)
    }

    override fun onCompress(item: ExplorerItem) {
        showCompressItemDialog(item)
    }

    override fun onExtract(item: ExplorerItem) {
        viewModel.extractItem(item)
    }

    override fun onManageAccess(item: ExplorerItem) {
        showShareDialog(item)
    }

    private fun showShareDialog(item: ExplorerItem) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = android.view.LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_share_manage, null)
        bottomSheet.setContentView(view)

        view.findViewById<TextView>(R.id.shareItemName).text = item.name
        
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.shareUsernameInput)
        val radioGroup = view.findViewById<android.widget.RadioGroup>(R.id.sharePermissionGroup)
        val btnGrantAccess = view.findViewById<android.widget.Button>(R.id.btnGrantAccess)
        
        btnGrantAccess.setOnClickListener {
            val targetUsername = input.text?.toString().orEmpty().trim()
            if (targetUsername.isNotBlank()) {
                val permission = if (radioGroup.checkedRadioButtonId == R.id.sharePermissionWrite) "WRITE" else "READ"
                viewModel.shareItem(item, targetUsername, permission)
                input.text?.clear()
                bottomSheet.dismiss()
            }
        }
        
        var isRestricted = item.previewUrl.isNullOrEmpty()
        
        val btnCopyLink = view.findViewById<View>(R.id.btnCopyLink)
        btnCopyLink.setOnClickListener {
            if (isRestricted) {
                val internalLinkUrl = "filemanagementapp://file?owner=$username&path=${android.net.Uri.encode(item.path)}"
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Internal Link", internalLinkUrl)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(requireContext(), "Đã copy link nội bộ (Yêu cầu đăng nhập & có quyền)", android.widget.Toast.LENGTH_LONG).show()
            } else {
                viewModel.createPublicLink(item) { token ->
                    if (!token.isNullOrEmpty()) {
                        val publicLinkUrl = "filemanagementapp://file?token=$token"
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Public Link", publicLinkUrl)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(requireContext(), "Đã copy link công khai: $publicLinkUrl", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            bottomSheet.dismiss()
        }
        
        val btnChangeGeneralAccess = view.findViewById<TextView>(R.id.btnChangeGeneralAccess)
        val generalAccessTitle = view.findViewById<TextView>(R.id.generalAccessTitle)
        val generalAccessSubtitle = view.findViewById<TextView>(R.id.generalAccessSubtitle)
        val generalAccessIcon = view.findViewById<ImageView>(R.id.generalAccessIcon)
        
        // Cập nhật giao diện tạm thời nếu có public link (nếu API có trả về)
        if (!isRestricted) {
            generalAccessTitle.text = "Bất kỳ ai có liên kết"
            generalAccessSubtitle.text = "Bất kỳ ai trên Internet có liên kết này đều có thể xem"
            generalAccessIcon.setImageResource(R.drawable.ic_link)
        }
        
        btnChangeGeneralAccess.setOnClickListener { anchor ->
            val popupView = layoutInflater.inflate(R.layout.popup_general_access, null)
            val popupWindow = android.widget.PopupWindow(
                popupView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            popupWindow.elevation = 8f
            popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            popupView.findViewById<View>(R.id.menuRestricted).setOnClickListener {
                popupWindow.dismiss()
                isRestricted = true
                generalAccessTitle.text = "Bị hạn chế"
                generalAccessSubtitle.text = "Chỉ những người được thêm mới có thể mở bằng liên kết này"
                generalAccessIcon.setImageResource(R.drawable.lock)

                viewModel.deletePublicLink(item) { success ->
                    if (success) {
                        android.widget.Toast.makeText(requireContext(), "Đã chuyển về quyền Bị hạn chế", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            popupView.findViewById<View>(R.id.menuAnyoneWithLink).setOnClickListener {
                popupWindow.dismiss()
                isRestricted = false
                generalAccessTitle.text = "Bất kỳ ai có liên kết"
                generalAccessSubtitle.text = "Bất kỳ ai trên Internet có liên kết này đều có thể xem"
                generalAccessIcon.setImageResource(R.drawable.ic_link)

                viewModel.createPublicLink(item) { token -> 
                    if (!token.isNullOrEmpty()) {
                        android.widget.Toast.makeText(requireContext(), "Đã bật chia sẻ công khai", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            popupWindow.showAsDropDown(anchor, 0, 8)
        }
        
        bottomSheet.show()
    }

    private fun startDownload(item: ExplorerItem) {
        val downloadUrl = item.previewUrl ?: return
        val finalFileName = if (item.type == ExplorerItem.Type.FOLDER) "${item.name}.zip" else item.name
        
        ExplorerDownloadService.start(
            context = requireContext(),
            fileName = finalFileName,
            downloadUrl = downloadUrl
        )
        Toast.makeText(requireContext(), R.string.explorer_download_started, Toast.LENGTH_SHORT).show()
    }

    private fun renderDownloadProgressCard(
        progress: ExplorerDownloadProgress?
    ) {
        if (progress == null) {
            downloadProgressCard.visibility = View.GONE
            return
        }

        downloadProgressCard.visibility = View.VISIBLE

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

        if (viewModel.uiState.value.currentFolder.startsWith("gdrive://")) {
            contentView.findViewById<View>(R.id.actionUploadFolder)?.visibility = View.GONE
        }

        contentView.findViewById<View>(R.id.actionCreateFolder).setOnClickListener {
            dialog.dismiss()
            showCreateFolderDialog()
        }
        contentView.findViewById<View>(R.id.actionUploadFile).setOnClickListener {
            dialog.dismiss()
            uploadFileLauncher.launch(arrayOf("*/*"))
        }
        contentView.findViewById<View>(R.id.actionUploadFolder).setOnClickListener {
            dialog.dismiss()
            uploadFolderLauncher.launch(null)
        }

        dialog.show()
    }

    private fun showDriveConnectSheet() {
        val contentView = layoutInflater.inflate(R.layout.bottom_sheet_drive_connect, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(contentView)

        contentView.findViewById<View>(R.id.btnConnectDrive).setOnClickListener {
            dialog.dismiss()
            googleDriveSignInLauncher.launch(driveAuthManager.getSignInIntent())
        }
        contentView.findViewById<View>(R.id.btnCancelDriveConnect).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDriveAccountSheet() {
        val account = driveAuthManager.getSignedInAccount() ?: run {
            showDriveConnectSheet()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tài khoản Google Drive")
            .setMessage("Đang liên kết với: ${account.email}")
            .setPositiveButton("Mở Google Drive") { _, _ ->
                viewModel.loadDirectory("gdrive://root", "Google Drive")
            }
            .setNeutralButton("Đăng xuất Drive") { _, _ ->
                driveAuthManager.signOut {
                    viewLifecycleOwner.lifecycleScope.launch {
                        drivePreferencesRepository.clearAccount()
                        viewModel.setDriveService(null)
                        Toast.makeText(requireContext(), "Đã ngắt kết nối Google Drive", Toast.LENGTH_SHORT).show()
                        viewModel.loadRootDirectory()
                    }
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showDriveFolderActionsSheet(item: ExplorerItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setItems(arrayOf("Mở thư mục", "Xóa thư mục khỏi Drive")) { _, which ->
                when (which) {
                    0 -> viewModel.loadDirectory(item.path, item.name)
                    1 -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Xác nhận xóa")
                            .setMessage("Bạn có chắc muốn xóa thư mục '${item.name}' khỏi Google Drive không?")
                            .setPositiveButton("Xóa") { _, _ ->
                                viewModel.deleteDriveItem(item)
                            }
                            .setNegativeButton("Hủy", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showDriveFileActionsSheet(item: ExplorerItem) {
        val contentView = layoutInflater.inflate(R.layout.bottom_sheet_drive_file_actions, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(contentView)

        val nameView = contentView.findViewById<TextView>(R.id.driveActionFileName)
        val metaView = contentView.findViewById<TextView>(R.id.driveActionFileMeta)
        nameView.text = item.name
        metaView.text = item.size ?: "Google Drive"

        // Action 1: Xem trước tệp
        contentView.findViewById<View>(R.id.actionDrivePreview).setOnClickListener {
            dialog.dismiss()
            val service = viewModel.getDriveService() ?: run {
                showDriveConnectSheet()
                return@setOnClickListener
            }
            val fileId = item.driveFileId ?: return@setOnClickListener
            Toast.makeText(requireContext(), "Đang chuẩn bị tệp xem trước...", Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                val result = googleDriveRepository.downloadFile(
                    drive = service,
                    fileId = fileId,
                    fileName = item.name,
                    mimeType = item.driveMimeType,
                    context = requireContext()
                )
                result.onSuccess { cachedFile ->
                    val previewItem = item.copy(
                        previewUrl = "file://${cachedFile.absolutePath}"
                    )
                    previewLauncher.launch(
                        FilePreviewActivity.newIntent(
                            context = requireContext(),
                            item = previewItem,
                            username = username,
                            analyzedImagePath = null,
                            ocrText = null,
                            aiTags = emptyList(),
                            showAiPanel = false
                        )
                    )
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "Lỗi tải tệp: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Action 2: Sao chép sang bộ nhớ ứng dụng
        contentView.findViewById<View>(R.id.actionDriveImport).setOnClickListener {
            dialog.dismiss()
            val service = viewModel.getDriveService() ?: run {
                showDriveConnectSheet()
                return@setOnClickListener
            }
            val fileId = item.driveFileId ?: return@setOnClickListener
            Toast.makeText(requireContext(), "Đang tải tệp từ Google Drive về...", Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                val result = googleDriveRepository.downloadFile(
                    drive = service,
                    fileId = fileId,
                    fileName = item.name,
                    mimeType = item.driveMimeType,
                    context = requireContext()
                )
                result.onSuccess { cachedFile ->
                    com.example.filemanagementapp.explorer.network.ExplorerUploadService.start(
                        context = requireContext(),
                        fileUri = Uri.fromFile(cachedFile),
                        targetPath = "",
                        username = username
                    )
                    Toast.makeText(requireContext(), "Đã bắt đầu sao chép '${cachedFile.name}' sang bộ nhớ ứng dụng", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "Lỗi sao chép tệp: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Action 3: Hỏi AI về tệp này
        contentView.findViewById<View>(R.id.actionDriveAskAi).setOnClickListener {
            dialog.dismiss()
            val service = viewModel.getDriveService() ?: run {
                showDriveConnectSheet()
                return@setOnClickListener
            }
            val fileId = item.driveFileId ?: return@setOnClickListener
            Toast.makeText(requireContext(), "Đang chuẩn bị tệp từ Google Drive...", Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                val result = googleDriveRepository.downloadFile(
                    drive = service,
                    fileId = fileId,
                    fileName = item.name,
                    mimeType = item.driveMimeType,
                    context = requireContext()
                )
                result.onSuccess { cachedFile ->
                    Toast.makeText(requireContext(), "Đang nạp tệp vào trợ lý AI...", Toast.LENGTH_SHORT).show()
                    val uploadResult = explorerRepository.uploadDirectFile(
                        username = username,
                        targetPath = "",
                        file = cachedFile,
                        overrideFileName = cachedFile.name
                    )
                    uploadResult.onSuccess { pair ->
                        val serverRelativePath = pair.second
                        AiChatBottomSheetFragment.newInstance(username, serverRelativePath)
                            .show(parentFragmentManager, AiChatBottomSheetFragment.TAG)
                    }.onFailure { uploadErr ->
                        Toast.makeText(requireContext(), "Không thể đồng bộ tệp lên server AI: ${uploadErr.message}", Toast.LENGTH_LONG).show()
                    }
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "Lỗi chuẩn bị tệp: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Action 4: Mở trong Google Drive
        contentView.findViewById<View>(R.id.actionDriveOpenExternal).setOnClickListener {
            dialog.dismiss()
            val webLink = item.driveWebViewLink
            if (!webLink.isNullOrBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webLink))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Không thể mở liên kết: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Không có liên kết xem trực tuyến cho tệp này", Toast.LENGTH_SHORT).show()
            }
        }

        // Action 5: Xóa khỏi Drive
        contentView.findViewById<View>(R.id.actionDriveDelete).setOnClickListener {
            dialog.dismiss()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa khỏi Google Drive")
                .setMessage("Bạn có chắc chắn muốn xóa '${item.name}' khỏi Google Drive không?")
                .setPositiveButton("Xóa") { _, _ ->
                    viewModel.deleteDriveItem(item)
                }
                .setNegativeButton("Hủy", null)
                .show()
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

    private fun showCompressSelectedDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val inputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialogInputLayout)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialogInputEditText)
        
        inputLayout.hint = getString(R.string.dialog_compress_name_hint)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_compress_name_title)
            .setView(view)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.dialog_compress_button) { _, _ ->
                val zipNameInput = input.text?.toString().orEmpty()
                if (zipNameInput.isNotBlank()) {
                    val finalName = if (zipNameInput.endsWith(".zip")) zipNameInput else "$zipNameInput.zip"
                    viewModel.compressSelectedItems(finalName)
                }
            }
            .show()
    }

    private fun showCompressItemDialog(item: ExplorerItem) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val inputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialogInputLayout)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialogInputEditText)
        
        inputLayout.hint = getString(R.string.dialog_compress_name_hint)
        // Default name is the item's name without extension if it's a file, or just folder name
        val defaultName = if (item.type == ExplorerItem.Type.FILE) item.name.substringBeforeLast(".") else item.name
        input.setText(defaultName)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_compress_name_title)
            .setView(view)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.dialog_compress_button) { _, _ ->
                val zipNameInput = input.text?.toString().orEmpty()
                if (zipNameInput.isNotBlank()) {
                    val finalName = if (zipNameInput.endsWith(".zip")) zipNameInput else "$zipNameInput.zip"
                    viewModel.compressItem(item, finalName)
                }
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
