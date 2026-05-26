package com.example.filemanagementapp.trash

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

class TrashAdapter(
    private var items: List<TrashItemModel>,
    private val selectedIds: MutableSet<String>,
    private val onToggleSelect: (String) -> Unit
) : RecyclerView.Adapter<TrashAdapter.TrashViewHolder>() {

    fun submitItems(newItems: List<TrashItemModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trash_entry, parent, false)
        return TrashViewHolder(view, selectedIds, onToggleSelect)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class TrashViewHolder(
        itemView: View,
        private val selectedIds: Set<String>,
        private val onToggleSelect: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val selectButton: ImageButton = itemView.findViewById(R.id.selectButton)
        private val thumbContainer: FrameLayout = itemView.findViewById(R.id.thumbContainer)
        private val thumbIcon: ImageView = itemView.findViewById(R.id.thumbIcon)
        private val itemNameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val itemMetaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val ocrPreviewText: TextView = itemView.findViewById(R.id.ocrPreviewText)
        private val aiChipText: TextView = itemView.findViewById(R.id.aiChipText)
        private val tagOneText: TextView = itemView.findViewById(R.id.tagOneText)
        private val tagTwoText: TextView = itemView.findViewById(R.id.tagTwoText)
        private val tagContainer: LinearLayout = itemView.findViewById(R.id.tagContainer)
        private val deletedDateText: TextView = itemView.findViewById(R.id.deletedDateText)
        private val removeInText: TextView = itemView.findViewById(R.id.removeInText)
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

            val style = iconStyle(item)
            thumbContainer.backgroundTintList = ContextCompat.getColorStateList(context, style.bgColor)
            thumbIcon.setImageResource(style.iconRes)
            thumbIcon.imageTintList = ContextCompat.getColorStateList(context, style.tintColor)

            itemNameText.text = item.name
            itemMetaText.text = if (item.type == TrashItemModel.Type.FOLDER) {
                context.getString(R.string.trash_folder_label)
            } else {
                "${item.size.orEmpty()} • ${item.type.name.lowercase()}"
            }

            ocrPreviewText.text = item.ocrPreview
            ocrPreviewText.visibility = if (item.ocrPreview.isNullOrBlank()) View.GONE else View.VISIBLE
            aiChipText.visibility = if (item.aiAnalyzed) View.VISIBLE else View.GONE

            val tags = item.aiTags.take(2)
            tagOneText.visibility = if (tags.isNotEmpty()) View.VISIBLE else View.GONE
            tagTwoText.visibility = if (tags.size > 1) View.VISIBLE else View.GONE
            if (tags.isNotEmpty()) tagOneText.text = tags[0]
            if (tags.size > 1) tagTwoText.text = tags[1]
            tagContainer.visibility =
                if (item.aiAnalyzed || tags.isNotEmpty()) View.VISIBLE else View.GONE

            deletedDateText.text = context.getString(R.string.trash_deleted, item.deletedDate)
            removeInText.text = context.getString(R.string.trash_remove_in, item.daysUntilRemoval)
            moreButton.setOnClickListener(null)
        }

        private fun iconStyle(item: TrashItemModel): IconStyle {
            return when (item.type) {
                TrashItemModel.Type.FOLDER -> IconStyle(R.drawable.folder, R.color.recent_folder_bg, R.color.recent_folder_tint)
                TrashItemModel.Type.IMAGE -> IconStyle(R.drawable.image_icon, R.color.recent_image_bg, R.color.recent_image)
                TrashItemModel.Type.DOCUMENT -> IconStyle(R.drawable.file_text, R.color.recent_pdf_bg, R.color.recent_pdf)
                TrashItemModel.Type.SPREADSHEET -> IconStyle(R.drawable.file_text, R.color.recent_doc_bg, R.color.recent_doc)
                TrashItemModel.Type.VIDEO -> IconStyle(R.drawable.film, R.color.recent_video_bg, R.color.recent_video)
                TrashItemModel.Type.FILE -> IconStyle(R.drawable.file_text, R.color.recent_chip_bg, R.color.recent_text_secondary)
            }
        }
    }

    private data class IconStyle(
        val iconRes: Int,
        val bgColor: Int,
        val tintColor: Int
    )
}
