package com.example.filemanagementapp.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R

class PdfRendererAdapter(private val renderer: PdfRenderer) : RecyclerView.Adapter<PdfRendererAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = renderer.openPage(position)
        // Adjust bitmap size for higher quality, e.g., 2x density
        val densityDpi = holder.itemView.context.resources.displayMetrics.densityDpi
        val scale = densityDpi / 160f
        val width = (page.width * scale).toInt()
        val height = (page.height * scale).toInt()
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // PDF default background is transparent, so fill with white
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        holder.imageView.setImageBitmap(bitmap)
        page.close()
    }

    override fun getItemCount(): Int = renderer.pageCount

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.pdfPageView)
    }
}
