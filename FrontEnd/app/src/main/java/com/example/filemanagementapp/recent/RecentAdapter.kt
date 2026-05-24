package com.example.filemanagementapp.recent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R

class RecentAdapter(
    private var items: List<RecentListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun submitItems(newItems: List<RecentListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is RecentListItem.Header) VIEW_TYPE_HEADER else VIEW_TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_recent_section_header, parent, false))
        } else {
            EntryViewHolder(inflater.inflate(R.layout.item_recent_entry, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is RecentListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is RecentListItem.Entry -> (holder as EntryViewHolder).bind(item.item)
        }
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.sectionIcon)
        private val title: TextView = itemView.findViewById(R.id.sectionTitle)
        private val count: TextView = itemView.findViewById(R.id.sectionCount)

        fun bind(item: RecentListItem.Header) {
            icon.setImageResource(item.iconRes)
            title.text = item.title
            count.text = item.count.toString()
        }
    }

    private class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbContainer: FrameLayout = itemView.findViewById(R.id.thumbContainer)
        private val thumbIcon: ImageView = itemView.findViewById(R.id.thumbIcon)
        private val thumbExt: TextView = itemView.findViewById(R.id.thumbExt)
        private val itemName: TextView = itemView.findViewById(R.id.itemNameText)
        private val favoriteStar: ImageView = itemView.findViewById(R.id.favoriteStarIcon)
        private val itemMeta: TextView = itemView.findViewById(R.id.itemMetaText)
        private val ocrSnippet: TextView = itemView.findViewById(R.id.ocrSnippetText)
        private val aiChip: TextView = itemView.findViewById(R.id.aiChipText)
        private val tagOne: TextView = itemView.findViewById(R.id.tagOneText)
        private val tagTwo: TextView = itemView.findViewById(R.id.tagTwoText)
        private val tagContainer: LinearLayout = itemView.findViewById(R.id.tagContainer)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(item: RecentItem) {
            val context = itemView.context
            itemName.text = item.name
            favoriteStar.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
            itemMeta.text = if (item.kind == RecentItem.Kind.FOLDER) {
                context.getString(R.string.recent_file_meta,
                    context.getString(R.string.recent_folder_item_count, item.itemCount ?: 0),
                    item.lastModified)
            } else {
                context.getString(R.string.recent_file_meta, item.size.orEmpty(), item.lastModified)
            }

            ocrSnippet.text = item.ocrSnippet
            ocrSnippet.visibility = if (item.ocrSnippet.isNullOrBlank()) View.GONE else View.VISIBLE
            aiChip.visibility = if (item.aiAnalyzed) View.VISIBLE else View.GONE

            val tags = item.aiTags.take(2)
            tagOne.visibility = if (tags.isNotEmpty()) View.VISIBLE else View.GONE
            tagTwo.visibility = if (tags.size > 1) View.VISIBLE else View.GONE
            if (tags.isNotEmpty()) {
                tagOne.text = tags[0]
            }
            if (tags.size > 1) {
                tagTwo.text = tags[1]
            }
            tagContainer.visibility =
                if (item.aiAnalyzed || tags.isNotEmpty()) View.VISIBLE else View.GONE

            val style = styleFor(item)
            thumbContainer.backgroundTintList = ContextCompat.getColorStateList(context, style.bgColor)
            thumbIcon.setImageResource(style.iconRes)
            thumbIcon.imageTintList = ContextCompat.getColorStateList(context, style.tintColor)
            thumbExt.text = style.ext
            thumbExt.setTextColor(ContextCompat.getColor(context, style.tintColor))
            thumbExt.visibility = if (item.kind == RecentItem.Kind.FILE) View.VISIBLE else View.GONE

            moreButton.setOnClickListener(null)
        }

        private fun styleFor(item: RecentItem): EntryStyle {
            if (item.kind == RecentItem.Kind.FOLDER) {
                return EntryStyle(R.drawable.folder, R.color.recent_folder_bg, R.color.recent_folder_tint, "")
            }
            return when (item.fileType) {
                RecentItem.FileType.IMAGE -> EntryStyle(R.drawable.image_icon, R.color.recent_image_bg, R.color.recent_image, itemView.context.getString(R.string.recent_type_img))
                RecentItem.FileType.VIDEO -> EntryStyle(R.drawable.film, R.color.recent_video_bg, R.color.recent_video, itemView.context.getString(R.string.recent_type_vid))
                RecentItem.FileType.AUDIO -> EntryStyle(R.drawable.file_audio, R.color.recent_audio_bg, R.color.recent_audio, itemView.context.getString(R.string.recent_type_aud))
                RecentItem.FileType.PDF -> EntryStyle(R.drawable.file_text, R.color.recent_pdf_bg, R.color.recent_pdf, itemView.context.getString(R.string.recent_type_pdf))
                else -> EntryStyle(R.drawable.file_text, R.color.recent_doc_bg, R.color.recent_doc, itemView.context.getString(R.string.recent_type_doc))
            }
        }
    }

    private data class EntryStyle(
        val iconRes: Int,
        val bgColor: Int,
        val tintColor: Int,
        val ext: String
    )

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
    }
}
