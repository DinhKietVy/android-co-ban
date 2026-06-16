package com.example.filemanagementapp.explorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.filemanagementapp.R
import com.google.android.material.imageview.ShapeableImageView

class ExplorerAdapter(
    private val onFolderClick: (ExplorerItem) -> Unit,
    private val onMoreClick: (ExplorerItem) -> Unit,
    private val onItemSelectionToggle: (ExplorerItem) -> Unit,
    private val onItemLongPress: (ExplorerItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ExplorerItem>()
    private var isSelectionMode = false
    private var selectedPaths = emptySet<String>()
    private var displayMode = DisplayMode.GRID

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        val isGrid = displayMode == DisplayMode.GRID
        return if (items[position].type == ExplorerItem.Type.FOLDER) {
            if (isGrid) VIEW_TYPE_FOLDER_GRID else VIEW_TYPE_FOLDER_LIST
        } else {
            if (isGrid) VIEW_TYPE_FILE_GRID else VIEW_TYPE_FILE_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_FOLDER_GRID -> FolderViewHolder(inflater.inflate(R.layout.item_explorer_folder_grid, parent, false))
            VIEW_TYPE_FOLDER_LIST -> FolderViewHolder(inflater.inflate(R.layout.item_explorer_folder_list, parent, false))
            VIEW_TYPE_FILE_GRID -> FileViewHolder(inflater.inflate(R.layout.item_explorer_file_grid, parent, false))
            VIEW_TYPE_FILE_LIST -> FileViewHolder(inflater.inflate(R.layout.item_explorer_file_list, parent, false))
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FolderViewHolder -> holder.bind(
                item = items[position],
                isSelectionMode = isSelectionMode,
                isSelected = items[position].path in selectedPaths,
                onFolderClick = onFolderClick,
                onMoreClick = onMoreClick,
                onItemSelectionToggle = onItemSelectionToggle,
                onItemLongPress = onItemLongPress
            )

            is FileViewHolder -> holder.bind(
                item = items[position],
                isSelectionMode = isSelectionMode,
                isSelected = items[position].path in selectedPaths,
                onMoreClick = onMoreClick,
                onItemSelectionToggle = onItemSelectionToggle,
                onItemLongPress = onItemLongPress
            )
        }
    }

    fun submitItems(
        newItems: List<ExplorerItem>,
        isSelectionMode: Boolean,
        selectedPaths: Set<String>,
        displayMode: DisplayMode
    ) {
        items.clear()
        items.addAll(newItems)
        this.isSelectionMode = isSelectionMode
        this.selectedPaths = selectedPaths
        this.displayMode = displayMode
        notifyDataSetChanged()
    }

    private class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val selectionCheckBox: CheckBox = itemView.findViewById(R.id.itemSelectionCheckBox)
        private val nameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val favoriteBadge: ImageView = itemView.findViewById(R.id.favoriteBadgeIcon)
        private val metaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(
            item: ExplorerItem,
            isSelectionMode: Boolean,
            isSelected: Boolean,
            onFolderClick: (ExplorerItem) -> Unit,
            onMoreClick: (ExplorerItem) -> Unit,
            onItemSelectionToggle: (ExplorerItem) -> Unit,
            onItemLongPress: (ExplorerItem) -> Unit
        ) {
            val context = itemView.context
            nameText.text = item.name
            favoriteBadge.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
            val count = context.getString(R.string.explorer_item_count, item.itemCount ?: 0)
            metaText.text = context.getString(R.string.explorer_file_meta, count, item.modified)
            selectionCheckBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            selectionCheckBox.isChecked = isSelected
            moreButton.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            moreButton.setOnClickListener { onMoreClick(item) }
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    onItemSelectionToggle(item)
                } else {
                    onFolderClick(item)
                }
            }
            itemView.setOnLongClickListener {
                onItemLongPress(item)
                true
            }
            selectionCheckBox.setOnClickListener { onItemSelectionToggle(item) }
        }
    }

    private class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val selectionCheckBox: CheckBox = itemView.findViewById(R.id.itemSelectionCheckBox)
        private val previewImage: ShapeableImageView = itemView.findViewById(R.id.filePreviewImage)
        private val fallbackIcon: ImageView = itemView.findViewById(R.id.fileFallbackIcon)
        private val nameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val aiBadge: ImageView = itemView.findViewById(R.id.aiBadgeIcon)
        private val favoriteBadge: ImageView = itemView.findViewById(R.id.favoriteBadgeIcon)
        private val tagContainer: LinearLayout = itemView.findViewById(R.id.tagContainer)
        private val ocrPreviewText: TextView? = itemView.findViewById(R.id.ocrPreviewText)
        private val metaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val dateText: TextView = itemView.findViewById(R.id.itemDateText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(
            item: ExplorerItem,
            isSelectionMode: Boolean,
            isSelected: Boolean,
            onMoreClick: (ExplorerItem) -> Unit,
            onItemSelectionToggle: (ExplorerItem) -> Unit,
            onItemLongPress: (ExplorerItem) -> Unit
        ) {
            if (item.isImagePreviewable && !item.previewUrl.isNullOrBlank()) {
                fallbackIcon.visibility = View.GONE
                previewImage.load(item.previewUrl) {
                    crossfade(true)
                    placeholder(R.drawable.explorer_file_preview_placeholder)
                    error(R.drawable.explorer_file_preview_placeholder)
                }
            } else {
                previewImage.setImageResource(R.drawable.explorer_file_preview_placeholder)
                fallbackIcon.visibility = View.VISIBLE
                fallbackIcon.setImageResource(item.fallbackIconRes ?: R.drawable.file_text)
            }

            nameText.text = item.name
            aiBadge.visibility = if (item.aiAnalyzed) View.VISIBLE else View.GONE
            favoriteBadge.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
            selectionCheckBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            selectionCheckBox.isChecked = isSelected

            val tags = item.tags
            if (tags.isEmpty()) {
                tagContainer.visibility = View.GONE
            } else {
                tagContainer.visibility = View.VISIBLE
                renderTags(tags)
            }
            
            if (ocrPreviewText != null) {
                if (!item.ocrSnippet.isNullOrBlank()) {
                    ocrPreviewText.visibility = View.VISIBLE
                    ocrPreviewText.text = item.ocrSnippet
                } else {
                    ocrPreviewText.visibility = View.GONE
                }
            }

            metaText.text = item.size.orEmpty()
            if (item.modified.isBlank()) {
                dateText.visibility = View.GONE
            } else {
                dateText.visibility = View.VISIBLE
                dateText.text = item.modified
            }
            moreButton.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            moreButton.setOnClickListener { onMoreClick(item) }
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    onItemSelectionToggle(item)
                }
            }
            itemView.setOnLongClickListener {
                onItemLongPress(item)
                true
            }
            selectionCheckBox.setOnClickListener { onItemSelectionToggle(item) }
        }

        private fun renderTags(tags: List<String>) {
            val context = itemView.context
            tagContainer.removeAllViews()
            tags.forEachIndexed { index, tag ->
                val tagView = TextView(context).apply {
                    text = tag
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.explorer_tag_text))
                    setBackgroundResource(R.drawable.explorer_tag_background)
                    setPadding(8.dp(context), 3.dp(context), 8.dp(context), 3.dp(context))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) {
                            marginStart = 6.dp(context)
                        }
                    }
                }
                tagContainer.addView(tagView)
            }
        }

        private fun Int.dp(context: android.content.Context): Int {
            return (this * context.resources.displayMetrics.density).toInt()
        }
    }

    companion object {
        private const val VIEW_TYPE_FOLDER_LIST = 0
        private const val VIEW_TYPE_FILE_LIST = 1
        private const val VIEW_TYPE_FOLDER_GRID = 2
        private const val VIEW_TYPE_FILE_GRID = 3
    }

    enum class DisplayMode {
        LIST,
        GRID
    }
}
