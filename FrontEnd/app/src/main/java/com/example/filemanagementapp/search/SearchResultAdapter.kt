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
import com.example.filemanagementapp.R

class SearchResultAdapter(
    private var items: List<SearchItem>,
    private val onMoreClick: (SearchItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchViewHolder>() {

    fun submitItems(newItems: List<SearchItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explorer_file_list, parent, false)
        return SearchViewHolder(view, onMoreClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class SearchViewHolder(
        itemView: View,
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
            
            filePreviewImage.visibility = View.GONE
            thumbIcon.visibility = View.VISIBLE
            
            thumbContainer.backgroundTintList = ContextCompat.getColorStateList(context, style.second)
            thumbIcon.setImageResource(style.first)
            thumbIcon.imageTintList = ContextCompat.getColorStateList(context, style.third)

            fileNameText.text = item.name
            itemMetaText.text = context.getString(R.string.search_file_meta_format, item.type, item.size)
            itemDateText.text = item.date
            ocrPreviewText.text = item.ocrText
            ocrPreviewText.visibility = if (item.ocrText.isNullOrBlank()) View.GONE else View.VISIBLE

            aiBadgeIcon.visibility = View.GONE
            favoriteBadgeIcon.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

            tagContainer.removeAllViews()
            val tags = item.tags
            tagContainer.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
            tags.forEach { tag ->
                val tv = TextView(context).apply {
                    text = tag
                    textSize = 11f
                    setPadding(16, 8, 16, 8)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = 12 }
                    
                    if (tag.equals("AI analyzed", ignoreCase = true)) {
                        setBackgroundResource(R.color.search_primary_soft)
                        setTextColor(ContextCompat.getColor(context, R.color.search_primary))
                        
                        // Add sparkle icon
                        val drawable = ContextCompat.getDrawable(context, R.drawable.sparkles)
                        drawable?.setTint(ContextCompat.getColor(context, R.color.search_primary))
                        drawable?.setBounds(0, 0, 30, 30) // roughly 10-12dp
                        setCompoundDrawables(drawable, null, null, null)
                        compoundDrawablePadding = 8
                    } else {
                        setBackgroundResource(R.drawable.explorer_tag_background)
                        setTextColor(ContextCompat.getColor(context, R.color.search_text_secondary))
                    }
                }
                tagContainer.addView(tv)
            }

            moreButton.setOnClickListener { onMoreClick(item) }
        }
    }
}
