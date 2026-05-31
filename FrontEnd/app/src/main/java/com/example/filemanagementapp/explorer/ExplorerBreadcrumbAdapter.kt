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
    private val items: List<ExplorerBreadcrumbItem>
) : RecyclerView.Adapter<ExplorerBreadcrumbAdapter.BreadcrumbViewHolder>() {

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreadcrumbViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explorer_breadcrumb, parent, false)
        return BreadcrumbViewHolder(view)
    }

    override fun onBindViewHolder(holder: BreadcrumbViewHolder, position: Int) {
        holder.bind(items[position], position == 0, position == items.lastIndex)
    }

    class BreadcrumbViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val separatorIcon: ImageView = itemView.findViewById(R.id.separatorIcon)
        private val homeIcon: ImageView = itemView.findViewById(R.id.homeIcon)
        private val breadcrumbText: TextView = itemView.findViewById(R.id.breadcrumbText)

        fun bind(item: ExplorerBreadcrumbItem, isFirst: Boolean, isLast: Boolean) {
            val context = itemView.context
            separatorIcon.visibility = if (isFirst) View.GONE else View.VISIBLE
            homeIcon.visibility = if (item.isHome) View.VISIBLE else View.GONE
            breadcrumbText.text = item.title
            breadcrumbText.visibility = if (item.title.isBlank()) View.GONE else View.VISIBLE
            breadcrumbText.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isLast) R.color.explorer_text_primary else R.color.explorer_text_secondary
                )
            )
            breadcrumbText.setTypeface(null, if (isLast) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}

data class ExplorerBreadcrumbItem(
    val title: String,
    val isHome: Boolean = false
)
