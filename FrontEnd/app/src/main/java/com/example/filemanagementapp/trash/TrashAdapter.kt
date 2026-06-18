package com.example.filemanagementapp.trash

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.google.android.material.imageview.ShapeableImageView
import coil.load
import coil.clear
import coil.dispose

class TrashAdapter(
    private var items: List<TrashItemModel>,
    private val selectedIds: MutableSet<String>,
    private val onToggleSelect: (String) -> Unit,
    private val onMoreClick: (TrashItemModel, View) -> Unit
) : RecyclerView.Adapter<TrashAdapter.TrashViewHolder>() {

    fun submitItems(newItems: List<TrashItemModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trash_entry, parent, false)
        return TrashViewHolder(view, selectedIds, onToggleSelect, onMoreClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class TrashViewHolder(
        itemView: View,
        private val selectedIds: Set<String>,
        private val onToggleSelect: (String) -> Unit,
        private val onMoreClick: (TrashItemModel, View) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val selectButton: ImageView = itemView.findViewById(R.id.selectButton)
        private val previewImage: ShapeableImageView = itemView.findViewById(R.id.filePreviewImage)
        private val fallbackIcon: ImageView = itemView.findViewById(R.id.fileFallbackIcon)
        private val itemNameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val itemMetaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val tagContainer: LinearLayout = itemView.findViewById(R.id.tagContainer)
        private val deletedDateText: TextView = itemView.findViewById(R.id.deletedDateText)
        private val removeInText: TextView = itemView.findViewById(R.id.removeInText)
        private val ocrPreviewText: TextView = itemView.findViewById(R.id.ocrPreviewText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(item: TrashItemModel) {
            val context = itemView.context
            val selected = selectedIds.contains(item.id)
            itemView.setBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (selected) R.color.trash_select_bg else R.color.trash_surface
                )
            )
            selectButton.setImageResource(if (selected) R.drawable.check_circle else R.drawable.circle)
            selectButton.imageTintList = ContextCompat.getColorStateList(
                context,
                if (selected) R.color.recent_primary else R.color.trash_empty_icon
            )
            selectButton.setOnClickListener { onToggleSelect(item.id) }

            if (item.type == TrashItemModel.Type.IMAGE && !item.previewUrl.isNullOrEmpty()) {
                previewImage.load(item.previewUrl) {
                    crossfade(true)
                    placeholder(R.drawable.explorer_file_preview_placeholder)
                    error(R.drawable.explorer_file_preview_placeholder)
                    listener(
                        onSuccess = { _, _ -> fallbackIcon.visibility = View.GONE },
                        onError = { _, _ -> fallbackIcon.visibility = View.VISIBLE }
                    )
                }
            } else {
                previewImage.dispose()
                previewImage.setImageResource(R.drawable.explorer_file_preview_placeholder)
                fallbackIcon.visibility = View.VISIBLE
                fallbackIcon.setImageResource(iconFor(item.type))
            }

            itemNameText.text = item.name
            itemMetaText.text = if (item.type == TrashItemModel.Type.FOLDER) {
                context.getString(R.string.trash_type_folder)
            } else {
                context.getString(
                    R.string.trash_file_meta,
                    item.size.orEmpty(),
                    context.getString(typeStringRes(item.type))
                )
            }

            renderTags(item)

            if (!item.ocrPreview.isNullOrBlank()) {
                ocrPreviewText.visibility = View.VISIBLE
                ocrPreviewText.text = item.ocrPreview
            } else {
                ocrPreviewText.visibility = View.GONE
            }

            deletedDateText.text = context.getString(R.string.trash_deleted, item.deletedDate)
            removeInText.text = context.getString(R.string.trash_remove_in, item.daysUntilRemoval)
            moreButton.setOnClickListener { onMoreClick(item, it) }
        }

        private fun renderTags(item: TrashItemModel) {
            val context = itemView.context
            tagContainer.removeAllViews()
            val tags = buildList {
                if (item.aiAnalyzed) add(context.getString(R.string.recent_ai_analyzed))
                addAll(item.aiTags.take(2))
            }
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

        private fun iconFor(type: TrashItemModel.Type): Int {
            return when (type) {
                TrashItemModel.Type.FOLDER -> R.drawable.folder
                TrashItemModel.Type.IMAGE -> R.drawable.image_icon
                TrashItemModel.Type.VIDEO -> R.drawable.film
                else -> R.drawable.file_text
            }
        }

        private fun typeStringRes(type: TrashItemModel.Type): Int {
            return when (type) {
                TrashItemModel.Type.FOLDER -> R.string.trash_type_folder
                TrashItemModel.Type.IMAGE -> R.string.trash_type_image
                TrashItemModel.Type.DOCUMENT -> R.string.trash_type_document
                TrashItemModel.Type.SPREADSHEET -> R.string.trash_type_spreadsheet
                TrashItemModel.Type.VIDEO -> R.string.trash_type_video
                TrashItemModel.Type.FILE -> R.string.trash_type_file
            }
        }

        private fun Int.dp(): Int {
            return (this * itemView.resources.displayMetrics.density).toInt()
        }
    }
}
