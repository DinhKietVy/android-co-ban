package com.example.filemanagementapp.explorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R

class ExplorerAdapter(
    private val items: List<ExplorerItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return if (items[position].type == ExplorerItem.Type.FOLDER) {
            VIEW_TYPE_FOLDER
        } else {
            VIEW_TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_FOLDER) {
            FolderViewHolder(inflater.inflate(R.layout.item_explorer_folder, parent, false))
        } else {
            FileViewHolder(inflater.inflate(R.layout.item_explorer_file, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FolderViewHolder -> holder.bind(items[position])
            is FileViewHolder -> holder.bind(items[position])
        }
    }

    private class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val metaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(item: ExplorerItem) {
            val context = itemView.context
            nameText.text = item.name
            val count = context.getString(R.string.explorer_item_count, item.itemCount ?: 0)
            metaText.text = context.getString(R.string.explorer_file_meta, count, item.modified)
            moreButton.setOnClickListener(null)
        }
    }

    private class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val aiBadge: ImageView = itemView.findViewById(R.id.aiBadgeIcon)
        private val tagContainer: LinearLayout = itemView.findViewById(R.id.tagContainer)
        private val tagOneText: TextView = itemView.findViewById(R.id.tagOneText)
        private val tagTwoText: TextView = itemView.findViewById(R.id.tagTwoText)
        private val metaText: TextView = itemView.findViewById(R.id.itemMetaText)
        private val dateText: TextView = itemView.findViewById(R.id.itemDateText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)

        fun bind(item: ExplorerItem) {
            nameText.text = item.name
            aiBadge.visibility = if (item.aiAnalyzed) View.VISIBLE else View.GONE

            val tags = item.aiTags.take(2)
            if (tags.isEmpty()) {
                tagContainer.visibility = View.GONE
            } else {
                tagContainer.visibility = View.VISIBLE
                tagOneText.text = tags[0]
                if (tags.size > 1) {
                    tagTwoText.visibility = View.VISIBLE
                    tagTwoText.text = tags[1]
                } else {
                    tagTwoText.visibility = View.GONE
                }
            }

            metaText.text = item.size.orEmpty()
            dateText.text = item.modified
            moreButton.setOnClickListener(null)
        }
    }

    companion object {
        private const val VIEW_TYPE_FOLDER = 0
        private const val VIEW_TYPE_FILE = 1
    }
}
