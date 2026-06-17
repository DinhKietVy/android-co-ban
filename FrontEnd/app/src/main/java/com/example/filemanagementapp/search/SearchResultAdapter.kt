package com.example.filemanagementapp.search

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
import coil.load
import com.example.filemanagementapp.R

class SearchResultAdapter(
    private var items: List<SearchItem>,
    private val onItemClick: (SearchItem) -> Unit,
    private val onMoreClick: (SearchItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchViewHolder>() {

    fun submitItems(newItems: List<SearchItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explorer_file_list, parent, false)
        return SearchViewHolder(view, onItemClick, onMoreClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class SearchViewHolder(
        itemView: View,
        private val onItemClick: (SearchItem) -> Unit,
        private val onMoreClick: (SearchItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val thumbContainer: FrameLayout = itemView.findViewById(R.id.thumbContainer)
        private val thumbIcon: ImageView = itemView.findViewById(R.id.fileFallbackIcon)
        private val filePreviewImage: ImageView = itemView.findViewById(R.id.filePreviewImage)
        private val fileNameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val itemMetaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val itemDateText: TextView = itemView.findViewById(R.id.itemDateText)
        private val ocrPreviewText: TextView = itemView.findViewById(R.id.ocrPreviewText)
        private val tagContainer: LinearLayout = itemView.findViewById(R.id.tagContainer)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)
        private val aiBadgeIcon: ImageView = itemView.findViewById(R.id.aiBadgeIcon)
        private val favoriteBadgeIcon: ImageView = itemView.findViewById(R.id.favoriteBadgeIcon)

        fun bind(item: SearchItem) {
            val context = itemView.context
            val style = when (item.category) {
                SearchItem.Category.PDF -> Triple(R.drawable.file_text, R.color.recent_pdf_bg, R.color.recent_pdf)
                SearchItem.Category.IMAGE -> Triple(R.drawable.image_icon, R.color.recent_image_bg, R.color.recent_image)
                SearchItem.Category.DOCUMENT -> Triple(R.drawable.file_text, R.color.recent_doc_bg, R.color.recent_doc)
                SearchItem.Category.FOLDER -> Triple(R.drawable.folder, R.color.search_surface, R.color.explorer_primary)
                SearchItem.Category.VIDEO -> Triple(R.drawable.film, R.color.recent_video_bg, R.color.recent_video)
                SearchItem.Category.AUDIO -> Triple(R.drawable.file_audio, R.color.recent_audio_bg, R.color.recent_audio)
                SearchItem.Category.OTHER -> Triple(R.drawable.file_text, R.color.search_surface, R.color.explorer_text_secondary)
            }
            
            if (item.rawItem.isImagePreviewable && !item.rawItem.previewUrl.isNullOrBlank()) {
                thumbIcon.visibility = View.GONE
                filePreviewImage.visibility = View.VISIBLE
                thumbContainer.backgroundTintList = null
                
                filePreviewImage.load(item.rawItem.previewUrl) {
                    crossfade(true)
                    placeholder(R.drawable.explorer_file_preview_placeholder)
                    error(R.drawable.explorer_file_preview_placeholder)
                }
            } else {
                filePreviewImage.visibility = View.GONE
                thumbIcon.visibility = View.VISIBLE
                
                thumbContainer.backgroundTintList = ContextCompat.getColorStateList(context, style.second)
                thumbIcon.setImageResource(style.first)
                thumbIcon.imageTintList = ContextCompat.getColorStateList(context, style.third)
            }

            fileNameText.text = item.name
            if (item.rawItem.type == com.example.filemanagementapp.explorer.ExplorerItem.Type.FOLDER) {
                itemMetaText.text = context.getString(
                    R.string.search_file_meta_format, 
                    item.type, 
                    context.getString(R.string.explorer_item_count, item.rawItem.itemCount ?: 0)
                )
            } else {
                itemMetaText.text = context.getString(
                    R.string.search_file_meta_format, 
                    item.type, 
                    item.size ?: ""
                )
            }
            itemDateText.text = item.date
            ocrPreviewText.text = item.ocrText
            ocrPreviewText.visibility = if (item.ocrText.isNullOrBlank()) View.GONE else View.VISIBLE

            aiBadgeIcon.visibility = if (item.rawItem.aiAnalyzed) View.VISIBLE else View.GONE
            favoriteBadgeIcon.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

            tagContainer.removeAllViews()
            val displayTags = item.tags.filter { !it.equals("AI analyzed", ignoreCase = true) }
            tagContainer.visibility = if (displayTags.isEmpty()) View.GONE else View.VISIBLE
            displayTags.forEachIndexed { index, tag ->
                val tv = TextView(context).apply {
                    text = tag
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.explorer_tag_text))
                    setBackgroundResource(R.drawable.explorer_tag_background)
                    val pxHorizontal = (8 * context.resources.displayMetrics.density).toInt()
                    val pxVertical = (3 * context.resources.displayMetrics.density).toInt()
                    setPadding(pxHorizontal, pxVertical, pxHorizontal, pxVertical)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) {
                            marginStart = (6 * context.resources.displayMetrics.density).toInt()
                        }
                        marginEnd = (6 * context.resources.displayMetrics.density).toInt()
                    }
                }
                tagContainer.addView(tv)
            }

            itemView.setOnClickListener { onItemClick(item) }
            moreButton.setOnClickListener { onMoreClick(item) }
        }
    }
}
