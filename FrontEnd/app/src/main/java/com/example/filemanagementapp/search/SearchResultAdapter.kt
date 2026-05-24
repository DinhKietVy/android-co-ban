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
            .inflate(R.layout.item_search_result, parent, false)
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
        private val thumbIcon: ImageView = itemView.findViewById(R.id.thumbIcon)
        private val fileNameText: TextView = itemView.findViewById(R.id.fileNameText)
        private val fileMetaText: TextView = itemView.findViewById(R.id.fileMetaText)
        private val ocrPreviewText: TextView = itemView.findViewById(R.id.ocrPreviewText)
        private val tagContainer: LinearLayout = itemView.findViewById(R.id.tagContainer)
        private val tagOneText: TextView = itemView.findViewById(R.id.tagOneText)
        private val tagTwoText: TextView = itemView.findViewById(R.id.tagTwoText)
        private val tagThreeText: TextView = itemView.findViewById(R.id.tagThreeText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(item: SearchItem) {
            val context = itemView.context
            val style = when (item.category) {
                SearchItem.Category.PDF -> Triple(R.drawable.file_text, R.color.recent_pdf_bg, R.color.recent_pdf)
                SearchItem.Category.IMAGE -> Triple(R.drawable.image_icon, R.color.recent_image_bg, R.color.recent_image)
                SearchItem.Category.DOCUMENT -> Triple(R.drawable.file_text, R.color.recent_doc_bg, R.color.recent_doc)
            }
            thumbContainer.backgroundTintList = ContextCompat.getColorStateList(context, style.second)
            thumbIcon.setImageResource(style.first)
            thumbIcon.imageTintList = ContextCompat.getColorStateList(context, style.third)

            fileNameText.text = item.name
            fileMetaText.text = "${item.type} • ${item.size} • ${item.date}"
            ocrPreviewText.text = item.ocrText
            ocrPreviewText.visibility = if (item.ocrText.isNullOrBlank()) View.GONE else View.VISIBLE

            val tags = item.tags
            tagContainer.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
            tagOneText.visibility = if (tags.isNotEmpty()) View.VISIBLE else View.GONE
            tagTwoText.visibility = if (tags.size > 1) View.VISIBLE else View.GONE
            tagThreeText.visibility = if (tags.size > 2) View.VISIBLE else View.GONE
            if (tags.isNotEmpty()) tagOneText.text = tags[0]
            if (tags.size > 1) tagTwoText.text = tags[1]
            if (tags.size > 2) tagThreeText.text = tags[2]

            moreButton.setOnClickListener { onMoreClick(item) }
        }
    }
}
