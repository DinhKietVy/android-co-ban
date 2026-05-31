package com.example.filemanagementapp.explorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R

class FolderPickerAdapter(
    private val onFolderClick: (ExplorerItem) -> Unit
) : RecyclerView.Adapter<FolderPickerAdapter.FolderViewHolder>() {

    private val items = mutableListOf<FolderPickerEntry>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_picker_entry, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(items[position], onFolderClick)
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(newItems: List<FolderPickerEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderNameText: TextView = itemView.findViewById(R.id.folderPickerNameText)

        fun bind(entry: FolderPickerEntry, onFolderClick: (ExplorerItem) -> Unit) {
            folderNameText.text = entry.item.name
            itemView.alpha = if (entry.isEnabled) 1f else 0.4f
            itemView.isEnabled = entry.isEnabled
            itemView.setOnClickListener {
                if (entry.isEnabled) {
                    onFolderClick(entry.item)
                }
            }
        }
    }
}

data class FolderPickerEntry(
    val item: ExplorerItem,
    val isEnabled: Boolean
)
