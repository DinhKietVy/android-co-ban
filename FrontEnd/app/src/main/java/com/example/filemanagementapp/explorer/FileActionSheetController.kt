package com.example.filemanagementapp.explorer

import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * UI-only controller for the file action bottom sheet (open/download/rename/move/favorite/delete/ai)
 * plus the rename/delete confirmation dialogs and the move folder-picker dialog.
 *
 * Mutation logic stays in each host's ViewModel — the controller only dispatches user intent
 * through [Callbacks]. Shared by ExplorerFragment and RecentFragment.
 */
class FileActionSheetController(
    private val rootView: View,
    private val lifecycleOwner: LifecycleOwner,
    private val explorerRepository: ExplorerRepository,
    private val username: String,
    private val currentFolderProvider: () -> String,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onOpen(item: ExplorerItem)
        fun onDownload(item: ExplorerItem)
        fun onRename(item: ExplorerItem, newName: String)
        fun onMove(item: ExplorerItem, targetPath: String)
        fun onFavorite(item: ExplorerItem)
        fun onDelete(item: ExplorerItem)
        fun onAnalyzeAi(item: ExplorerItem)
    }

    data class ActionConfig(
        val showOpen: Boolean = true,
        val showDownload: Boolean = true,
        val showRename: Boolean = true,
        val showMove: Boolean = true,
        val showFavorite: Boolean = true,
        val showDelete: Boolean = true,
        val showAi: Boolean = true
    )

    private val context = rootView.context

    private val bottomSheet: LinearLayout = rootView.findViewById(R.id.fileActionBottomSheet)
    private val bottomSheetFileIcon: ImageView = rootView.findViewById(R.id.bsFileIcon)
    private val bottomSheetFilePreviewImage: ImageView = rootView.findViewById(R.id.bsFilePreviewImage)
    private val bottomSheetFileName: TextView = rootView.findViewById(R.id.bsFileName)
    private val bottomSheetFileMeta: TextView = rootView.findViewById(R.id.bsFileMeta)
    private val bottomSheetTagsContainer: LinearLayout = rootView.findViewById(R.id.bsAiTagsContainer)
    private val actionFavoriteLabel: TextView = rootView.findViewById(R.id.actionFavoriteLabel)
    private val scrim: View? = rootView.findViewById(R.id.fileActionScrim)

    private val actionOpen: View = rootView.findViewById(R.id.actionOpen)
    private val actionDownload: View = rootView.findViewById(R.id.actionDownload)
    private val actionRename: View = rootView.findViewById(R.id.actionRename)
    private val actionMove: View = rootView.findViewById(R.id.actionMove)
    private val actionFavorite: View = rootView.findViewById(R.id.actionFavorite)
    private val actionDelete: View = rootView.findViewById(R.id.actionDelete)
    private val actionAi: View = rootView.findViewById(R.id.actionAi)

    private val bottomSheetBehavior: BottomSheetBehavior<LinearLayout> =
        BottomSheetBehavior.from(bottomSheet)

    private var selectedItem: ExplorerItem? = null

    init {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    sheet.visibility = View.GONE
                    scrim?.visibility = View.GONE
                } else {
                    scrim?.visibility = View.VISIBLE
                }
            }

            override fun onSlide(sheet: View, slideOffset: Float) {
                if (slideOffset > 0) {
                    scrim?.alpha = slideOffset
                } else if (slideOffset <= 0 && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    scrim?.alpha = 1f + slideOffset // Fade out if sliding down, but offset is negative
                }
            }
        })

        scrim?.setOnClickListener { hide() }
        rootView.findViewById<ImageButton>(R.id.bsCloseButton).setOnClickListener { hide() }

        actionOpen.setOnClickListener { dispatch { callbacks.onOpen(it) } }
        actionDownload.setOnClickListener { dispatch { callbacks.onDownload(it) } }
        actionRename.setOnClickListener { dispatch { showRenameDialog(it) } }
        actionMove.setOnClickListener { dispatch { showMoveDialog(it) } }
        actionFavorite.setOnClickListener { dispatch { callbacks.onFavorite(it) } }
        actionDelete.setOnClickListener { dispatch { showDeleteDialog(it) } }
        actionAi.setOnClickListener { dispatch { callbacks.onAnalyzeAi(it) } }
    }

    fun show(item: ExplorerItem, config: ActionConfig = ActionConfig()) {
        selectedItem = item

        bottomSheetFileName.text = item.name
        bottomSheetFileMeta.text = buildMeta(item)
        
        if (item.isImagePreviewable && !item.previewUrl.isNullOrEmpty()) {
            bottomSheetFilePreviewImage.load(item.previewUrl) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ -> bottomSheetFileIcon.visibility = View.GONE },
                    onError = { _, _ -> bottomSheetFileIcon.visibility = View.VISIBLE }
                )
            }
        } else {
            bottomSheetFilePreviewImage.setImageDrawable(null)
            bottomSheetFileIcon.visibility = View.VISIBLE
            bottomSheetFileIcon.setImageResource(
                when (item.type) {
                    ExplorerItem.Type.FOLDER -> R.drawable.folder
                    ExplorerItem.Type.FILE -> item.fallbackIconRes ?: R.drawable.file_text
                }
            )
        }
        
        actionFavoriteLabel.setText(
            if (item.isFavorite) R.string.preview_action_unfavorite else R.string.action_favorite
        )
        
        val actionAiLabel: TextView = rootView.findViewById(R.id.actionAiLabel)
        actionAiLabel.setText(
            if (item.aiAnalyzed) R.string.action_reanalyze_ai else R.string.action_analyze_ai
        )

        actionOpen.visibility = if (config.showOpen) View.VISIBLE else View.GONE
        actionDownload.visibility = if (config.showDownload) View.VISIBLE else View.GONE
        actionRename.visibility = if (config.showRename) View.VISIBLE else View.GONE
        actionMove.visibility = if (config.showMove) View.VISIBLE else View.GONE
        actionFavorite.visibility = if (config.showFavorite) View.VISIBLE else View.GONE
        actionDelete.visibility = if (config.showDelete) View.VISIBLE else View.GONE
        actionAi.visibility = if (config.showAi) View.VISIBLE else View.GONE

        bottomSheetTagsContainer.removeAllViews()
        bottomSheetTagsContainer.visibility = View.GONE

        scrim?.alpha = 0f
        scrim?.visibility = View.VISIBLE
        bottomSheet.visibility = View.VISIBLE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun hide() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    /** Public entry for bulk-move flows (multi-select) that reuse the same folder picker. */
    fun pickFolder(
        titleRes: Int,
        movingItems: List<ExplorerItem>,
        onFolderPicked: (String) -> Unit
    ) {
        showFolderPickerDialog(titleRes, movingItems, onFolderPicked)
    }

    private inline fun dispatch(action: (ExplorerItem) -> Unit) {
        val item = selectedItem ?: return
        hide()
        action(item)
    }

    private fun showRenameDialog(item: ExplorerItem) {
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
        val inputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialogInputLayout)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialogInputEditText)
        
        inputLayout.hint = context.getString(R.string.explorer_dialog_rename_hint)
        val isFile = item.type == ExplorerItem.Type.FILE
        val nameWithoutExt = if (isFile) item.name.substringBeforeLast('.', item.name) else item.name
        val extension = if (isFile && item.name.contains('.')) "." + item.name.substringAfterLast('.', "") else ""
        
        input.setText(nameWithoutExt)
        input.setSelection(nameWithoutExt.length)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.explorer_dialog_rename_title)
            .setView(view)
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                val newNameInput = input.text?.toString().orEmpty()
                if (newNameInput.isBlank()) return@setPositiveButton
                val newName = newNameInput + extension
                callbacks.onRename(item, newName)
            }
            .show()
    }

    private fun showDeleteDialog(item: ExplorerItem) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.explorer_dialog_delete_title)
            .setMessage(context.getString(R.string.explorer_dialog_delete_message, item.name))
            .setNegativeButton(R.string.explorer_dialog_cancel, null)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                callbacks.onDelete(item)
            }
            .show()
    }

    private fun showMoveDialog(item: ExplorerItem) {
        showFolderPickerDialog(
            titleRes = R.string.explorer_dialog_move_title,
            movingItems = listOf(item),
            onFolderPicked = { folderPath -> callbacks.onMove(item, folderPath) }
        )
    }

    private fun showFolderPickerDialog(
        titleRes: Int,
        movingItems: List<ExplorerItem>,
        onFolderPicked: (String) -> Unit
    ) {
        val inflater = android.view.LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_folder_picker, null)
        val pathText = dialogView.findViewById<TextView>(R.id.folderPickerPathText)
        val upButton = dialogView.findViewById<TextView>(R.id.folderPickerUpButton)
        val loadingView = dialogView.findViewById<ProgressBar>(R.id.folderPickerLoading)
        val emptyView = dialogView.findViewById<TextView>(R.id.folderPickerEmptyText)
        val invalidHintText = dialogView.findViewById<TextView>(R.id.folderPickerInvalidHintText)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderPickerRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(context)

        var currentPath = currentFolderProvider()
        var loadJob: Job? = null
        lateinit var loadFolderPickerPath: (String) -> Unit
        var positiveButton: Button? = null
        val adapter = FolderPickerAdapter { folder -> loadFolderPickerPath(folder.path) }
        recyclerView.adapter = adapter

        fun renderFolderPickerPath() {
            pathText.text = if (currentPath.isBlank()) {
                context.getString(R.string.explorer_folder_picker_current_root)
            } else {
                context.getString(R.string.explorer_folder_picker_current, currentPath)
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
            loadJob = lifecycleOwner.lifecycleScope.launch {
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
                        emptyView.text = context.getString(R.string.explorer_folder_picker_empty)
                    }
                    .onFailure {
                        adapter.submitItems(emptyList())
                        emptyView.visibility = View.VISIBLE
                        emptyView.text = context.getString(R.string.explorer_folder_picker_loading_error)
                    }
                loadingView.visibility = View.GONE
                recyclerView.alpha = 1f
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
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

        dialog.setOnDismissListener { loadJob?.cancel() }

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

    private fun buildMeta(item: ExplorerItem): String {
        return when (item.type) {
            ExplorerItem.Type.FOLDER -> {
                val count = context.getString(R.string.explorer_item_count, item.itemCount ?: 0)
                context.getString(R.string.explorer_file_meta, count, item.modified)
            }

            ExplorerItem.Type.FILE -> context.getString(
                R.string.explorer_file_meta,
                item.size.orEmpty(),
                item.modified
            )
        }
    }

    private fun renderTags(tags: List<String>) {
        bottomSheetTagsContainer.removeAllViews()
        if (tags.isEmpty()) {
            bottomSheetTagsContainer.visibility = View.GONE
            return
        }

        bottomSheetTagsContainer.visibility = View.VISIBLE
        tags.forEachIndexed { index, tag ->
            val tagView = TextView(context).apply {
                text = tag
                textSize = 11f
                setTextColor(context.resources.getColor(R.color.explorer_tag_text, null))
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
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
