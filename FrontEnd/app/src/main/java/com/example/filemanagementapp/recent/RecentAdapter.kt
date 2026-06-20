package com.example.filemanagementapp.recent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.filemanagementapp.R
import com.google.android.material.imageview.ShapeableImageView

class RecentAdapter(
    private var items: List<RecentListItem>,
    private val onItemClick: (RecentItem) -> Unit,
    private val onMoreClick: (RecentItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun submitItems(newItems: List<RecentListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is RecentListItem.Header -> VIEW_TYPE_HEADER
            is RecentListItem.Entry ->
                if (item.item.kind == RecentItem.Kind.FOLDER) VIEW_TYPE_FOLDER else VIEW_TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER ->
                HeaderViewHolder(inflater.inflate(R.layout.item_recent_section_header, parent, false))

            VIEW_TYPE_FOLDER ->
                FolderViewHolder(inflater.inflate(R.layout.item_explorer_folder_list, parent, false))

            else ->
                FileViewHolder(inflater.inflate(R.layout.item_explorer_file_list, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is RecentListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is RecentListItem.Entry -> when (holder) {
                is FolderViewHolder -> holder.bind(item.item, onItemClick, onMoreClick)
                is FileViewHolder -> holder.bind(item.item, onItemClick, onMoreClick)
            }
        }
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.sectionIcon)
        private val title: TextView = itemView.findViewById(R.id.sectionTitle)
        private val count: TextView = itemView.findViewById(R.id.sectionCount)

        fun bind(item: RecentListItem.Header) {
            val context = itemView.context
            icon.setImageResource(iconFor(item.section))
            title.text = context.getString(titleFor(item.section))
            count.text = item.count.toString()
        }

        private fun titleFor(section: RecentItem.Section): Int {
            return when (section) {
                RecentItem.Section.TODAY -> R.string.recent_today
                RecentItem.Section.YESTERDAY -> R.string.recent_yesterday
                RecentItem.Section.WEEK -> R.string.recent_this_week
                RecentItem.Section.EARLIER -> R.string.recent_earlier
                RecentItem.Section.FAV_RECENT -> R.string.recent_favorites_section
                RecentItem.Section.FAV_AI -> R.string.recent_ai_section
                RecentItem.Section.FAV_FREQUENT -> R.string.recent_frequent_section
            }
        }

        private fun iconFor(section: RecentItem.Section): Int {
            return when (section) {
                RecentItem.Section.FAV_RECENT -> R.drawable.star
                RecentItem.Section.FAV_AI -> R.drawable.brain
                RecentItem.Section.FAV_FREQUENT -> R.drawable.bookmark_check
                else -> R.drawable.clock
            }
        }
    }

    private class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val selectionCheckBox: View = itemView.findViewById(R.id.itemSelectionCheckBox)
        private val nameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val favoriteBadge: ImageView = itemView.findViewById(R.id.favoriteBadgeIcon)
        private val metaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(
            item: RecentItem,
            onItemClick: (RecentItem) -> Unit,
            onMoreClick: (RecentItem) -> Unit
        ) {
            val context = itemView.context
            selectionCheckBox.visibility = View.GONE
            nameText.text = item.name
            favoriteBadge.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
            val count = context.getString(R.string.recent_folder_item_count, item.itemCount ?: 0)
            metaText.text = context.getString(R.string.recent_file_meta, count, item.lastModified)
            itemView.setOnClickListener { onItemClick(item) }
            moreButton.setOnClickListener { onMoreClick(item) }
        }
    }

    private class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val selectionCheckBox: View = itemView.findViewById(R.id.itemSelectionCheckBox)
        private val previewImage: ShapeableImageView = itemView.findViewById(R.id.filePreviewImage)
        private val fallbackIcon: ImageView = itemView.findViewById(R.id.fileFallbackIcon)
        private val nameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val aiBadge: ImageView = itemView.findViewById(R.id.aiBadgeIcon)
        private val favoriteBadge: ImageView = itemView.findViewById(R.id.favoriteBadgeIcon)
        private val tagContainer: LinearLayout = itemView.findViewById(R.id.tagContainer)
        private val metaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val dateText: TextView = itemView.findViewById(R.id.itemDateText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(
            item: RecentItem,
            onItemClick: (RecentItem) -> Unit,
            onMoreClick: (RecentItem) -> Unit
        ) {
            selectionCheckBox.visibility = View.GONE

            if (item.isImagePreviewable && !item.previewUrl.isNullOrBlank()) {
                fallbackIcon.visibility = View.GONE
                previewImage.load(item.previewUrl) {
                    crossfade(true)
                    setHeader("ngrok-skip-browser-warning", "69420")
                    placeholder(R.drawable.explorer_file_preview_placeholder)
                    error(R.drawable.explorer_file_preview_placeholder)
                }
            } else {
                previewImage.load(null) // Clear any pending coil request
                previewImage.setImageResource(R.drawable.explorer_file_preview_placeholder)
                fallbackIcon.visibility = View.VISIBLE
                fallbackIcon.setImageResource(fallbackIconFor(item))
            }

            nameText.text = item.name
            aiBadge.visibility = if (item.aiAnalyzed) View.VISIBLE else View.GONE
            favoriteBadge.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

            renderTags(item.aiTags.take(2))

            metaText.text = item.size.orEmpty()
            if (item.lastModified.isBlank()) {
                dateText.visibility = View.GONE
            } else {
                dateText.visibility = View.VISIBLE
                dateText.text = item.lastModified
            }

            itemView.setOnClickListener { onItemClick(item) }
            moreButton.setOnClickListener { onMoreClick(item) }
        }

        private fun renderTags(tags: List<String>) {
            val context = itemView.context
            tagContainer.removeAllViews()
            if (tags.isEmpty()) {
                tagContainer.visibility = View.GONE
                return
            }
            tagContainer.visibility = View.VISIBLE
            tags.forEachIndexed { index, tag ->
                val tagView = TextView(context).apply {
                    text = tag
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.explorer_tag_text))
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
                tagContainer.addView(tagView)
            }
        }

        private fun fallbackIconFor(item: RecentItem): Int {
            if (item.kind == RecentItem.Kind.FOLDER) {
                return R.drawable.folder
            }
            return when (item.fileType) {
                RecentItem.FileType.IMAGE -> R.drawable.image_icon
                RecentItem.FileType.VIDEO -> R.drawable.film
                RecentItem.FileType.AUDIO -> R.drawable.file_audio
                else -> R.drawable.file_text
            }
        }

        private fun Int.dp(): Int {
            return (this * itemView.resources.displayMetrics.density).toInt()
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_FILE = 1
        private const val VIEW_TYPE_FOLDER = 2
    }
}
