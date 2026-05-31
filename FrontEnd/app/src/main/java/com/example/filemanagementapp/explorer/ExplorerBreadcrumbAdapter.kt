package com.example.filemanagementapp.explorer

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R

class ExplorerBreadcrumbAdapter(
    private val onItemClick: (ExplorerBreadcrumbItem) -> Unit
) : RecyclerView.Adapter<ExplorerBreadcrumbAdapter.BreadcrumbViewHolder>() {

    private val items = mutableListOf<ExplorerBreadcrumbItem>()

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreadcrumbViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explorer_breadcrumb, parent, false)
        return BreadcrumbViewHolder(view)
    }

    override fun onBindViewHolder(holder: BreadcrumbViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            isLast = position == items.lastIndex,
            onItemClick = onItemClick
        )
    }

    fun submitItems(newItems: List<ExplorerBreadcrumbItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class BreadcrumbViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val separatorIcon: ImageView = itemView.findViewById(R.id.separatorIcon)
        private val breadcrumbText: TextView = itemView.findViewById(R.id.breadcrumbText)

        fun bind(
            item: ExplorerBreadcrumbItem,
            isLast: Boolean,
            onItemClick: (ExplorerBreadcrumbItem) -> Unit
        ) {
            val context = itemView.context
            separatorIcon.visibility = if (isLast) View.GONE else View.VISIBLE
            breadcrumbText.text = item.title
            breadcrumbText.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isLast) R.color.explorer_text_primary else R.color.explorer_text_secondary
                )
            )
            breadcrumbText.setTypeface(null, if (isLast) Typeface.BOLD else Typeface.NORMAL)
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}

data class ExplorerBreadcrumbItem(
    val title: String,
    val path: String
)
