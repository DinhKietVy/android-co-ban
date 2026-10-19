package com.example.filemanagementapp.preview

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfPageOrganizerDialog : DialogFragment() {

    private var pdfFile: File? = null
    private var onApplyChangesListener: ((File) -> Unit)? = null
    private var onExtractPagesListener: ((File, Int) -> Unit)? = null

    private val pageItems = mutableListOf<PageItem>()
    private var adapter: PageAdapter? = null

    private lateinit var tvSubtitle: TextView
    private lateinit var progressOrganizer: ProgressBar
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnApply: MaterialButton

    data class PageItem(
        val originalIndex: Int,
        var rotation: Int = 0,
        var isDeleted: Boolean = false,
        var isSelected: Boolean = false,
        var thumbnail: Bitmap? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_FileManagementApp)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_pdf_page_organizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val targetPdf = pdfFile
        if (targetPdf == null || !targetPdf.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        tvSubtitle = view.findViewById(R.id.tvOrganizerSubtitle)
        progressOrganizer = view.findViewById(R.id.progressOrganizer)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)
        btnApply = view.findViewById(R.id.btnApply)

        view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            dismiss()
        }

        val rvPages = view.findViewById<RecyclerView>(R.id.rvPages)
        rvPages.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = PageAdapter(pageItems) { updateSelectionStats() }
        rvPages.adapter = adapter

        btnSelectAll.setOnClickListener {
            val allSelected = pageItems.isNotEmpty() && pageItems.all { it.isSelected }
            pageItems.forEach { it.isSelected = !allSelected }
            adapter?.notifyDataSetChanged()
            updateSelectionStats()
        }

        view.findViewById<MaterialButton>(R.id.btnRotateSelected).setOnClickListener {
            val targets = pageItems.filter { it.isSelected }.ifEmpty { pageItems }
            targets.forEach { item ->
                item.rotation = (item.rotation + 90) % 360
            }
            adapter?.notifyDataSetChanged()
        }

        view.findViewById<MaterialButton>(R.id.btnDeleteSelected).setOnClickListener {
            val selected = pageItems.filter { it.isSelected }
            if (selected.isEmpty()) {
                Toast.makeText(context, R.string.pdf_organizer_no_page_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val willAllBeDeleted = pageItems.all { it.isSelected || it.isDeleted }
            if (willAllBeDeleted && selected.any { !it.isDeleted }) {
                Toast.makeText(context, R.string.pdf_organizer_all_deleted_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selected.forEach { it.isDeleted = !it.isDeleted }
            adapter?.notifyDataSetChanged()
            updateSelectionStats()
        }

        view.findViewById<MaterialButton>(R.id.btnExtractSelected).setOnClickListener {
            extractSelectedPages()
        }

        btnApply.setOnClickListener {
            applyChanges()
        }

        loadPageThumbnails(targetPdf)
    }

    private fun loadPageThumbnails(file: File) {
        progressOrganizer.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val items = mutableListOf<PageItem>()
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount

                for (i in 0 until count) {
                    val page = renderer.openPage(i)
                    // Render lightweight thumbnail (approx 300px height)
                    val scale = 300f / page.height.coerceAtLeast(1)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    items.add(PageItem(originalIndex = i, thumbnail = bmp))
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                progressOrganizer.visibility = View.GONE
                pageItems.clear()
                pageItems.addAll(items)
                adapter?.notifyDataSetChanged()
                updateSelectionStats()
            }
        }
    }

    private fun updateSelectionStats() {
        val selectedCount = pageItems.count { it.isSelected }
        val allSelected = pageItems.isNotEmpty() && selectedCount == pageItems.size
        btnSelectAll.text = if (allSelected) {
            getString(R.string.pdf_organizer_deselect_all)
        } else {
            getString(R.string.pdf_organizer_select_all)
        }

        if (selectedCount > 0) {
            tvSubtitle.text = getString(R.string.pdf_selected_count, selectedCount)
        } else {
            tvSubtitle.text = getString(R.string.pdf_organizer_subtitle)
        }
    }

    private fun extractSelectedPages() {
        val activeSelected = pageItems.filter { it.isSelected && !it.isDeleted }
        if (activeSelected.isEmpty()) {
            Toast.makeText(context, R.string.pdf_organizer_no_page_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val src = pdfFile ?: return
        progressOrganizer.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val destFile = File(requireContext().cacheDir, "extracted_${System.currentTimeMillis()}.pdf")
            val selectedIndices = activeSelected.map { it.originalIndex }
            val rotations = activeSelected.associate { it.originalIndex to it.rotation }

            val result = PdfEditHelper.extractPages(src, destFile, selectedIndices, rotations)

            withContext(Dispatchers.Main) {
                progressOrganizer.visibility = View.GONE
                if (result.isSuccess) {
                    Toast.makeText(
                        context,
                        getString(R.string.pdf_organizer_extract_success, activeSelected.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    onExtractPagesListener?.invoke(destFile, activeSelected.size)
                    dismiss()
                } else {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.message ?: "Error extracting pages",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun applyChanges() {
        val nonDeleted = pageItems.filter { !it.isDeleted }
        if (nonDeleted.isEmpty()) {
            Toast.makeText(context, R.string.pdf_organizer_all_deleted_error, Toast.LENGTH_SHORT).show()
            return
        }

        val src = pdfFile ?: return
        progressOrganizer.visibility = View.VISIBLE
        btnApply.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val destFile = File(requireContext().cacheDir, "modified_${System.currentTimeMillis()}.pdf")
            val mods = pageItems.map {
                PdfEditHelper.PageModification(
                    originalIndex = it.originalIndex,
                    rotation = it.rotation,
                    isDeleted = it.isDeleted
                )
            }

            val result = PdfEditHelper.applyPageModifications(src, destFile, mods)

            withContext(Dispatchers.Main) {
                progressOrganizer.visibility = View.GONE
                btnApply.isEnabled = true
                if (result.isSuccess) {
                    onApplyChangesListener?.invoke(destFile)
                    dismiss()
                } else {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.message ?: "Error modifying PDF",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up thumbnails to prevent any memory leak
        pageItems.forEach {
            it.thumbnail?.recycle()
            it.thumbnail = null
        }
    }

    inner class PageAdapter(
        private val items: List<PageItem>,
        private val onItemSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<PageAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val cardPage: MaterialCardView = v.findViewById(R.id.cardPage)
            val ivThumbnail: ImageView = v.findViewById(R.id.ivPageThumbnail)
            val tvRotationBadge: TextView = v.findViewById(R.id.tvRotationBadge)
            val layoutDeletedOverlay: View = v.findViewById(R.id.layoutDeletedOverlay)
            val tvPageNumber: TextView = v.findViewById(R.id.tvPageNumber)
            val btnRotateSingle: ImageButton = v.findViewById(R.id.btnRotateSingle)
            val cbSelectPage: CheckBox = v.findViewById(R.id.cbSelectPage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_organizer_page, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.tvPageNumber.text = holder.itemView.context.getString(
                R.string.pdf_organizer_page_format,
                item.originalIndex + 1
            )

            holder.ivThumbnail.setImageBitmap(item.thumbnail)
            holder.ivThumbnail.rotation = item.rotation.toFloat()

            if (item.rotation % 360 != 0) {
                holder.tvRotationBadge.visibility = View.VISIBLE
                holder.tvRotationBadge.text = "${item.rotation % 360}°"
            } else {
                holder.tvRotationBadge.visibility = View.GONE
            }

            holder.layoutDeletedOverlay.visibility = if (item.isDeleted) View.VISIBLE else View.GONE

            holder.cbSelectPage.setOnCheckedChangeListener(null)
            holder.cbSelectPage.isChecked = item.isSelected
            holder.cbSelectPage.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                updateCardStroke(holder.cardPage, isChecked)
                onItemSelectionChanged()
            }

            updateCardStroke(holder.cardPage, item.isSelected)

            holder.cardPage.setOnClickListener {
                item.isSelected = !item.isSelected
                holder.cbSelectPage.isChecked = item.isSelected
                updateCardStroke(holder.cardPage, item.isSelected)
                onItemSelectionChanged()
            }

            holder.btnRotateSingle.setOnClickListener {
                item.rotation = (item.rotation + 90) % 360
                holder.ivThumbnail.rotation = item.rotation.toFloat()
                if (item.rotation % 360 != 0) {
                    holder.tvRotationBadge.visibility = View.VISIBLE
                    holder.tvRotationBadge.text = "${item.rotation % 360}°"
                } else {
                    holder.tvRotationBadge.visibility = View.GONE
                }
            }
        }

        private fun updateCardStroke(card: MaterialCardView, isSelected: Boolean) {
            if (isSelected) {
                card.strokeColor = androidx.core.content.ContextCompat.getColor(card.context, R.color.explorer_primary)
                card.strokeWidth = 4
            } else {
                card.strokeColor = androidx.core.content.ContextCompat.getColor(card.context, R.color.explorer_border)
                card.strokeWidth = 2
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        fun newInstance(
            pdfFile: File,
            onApplyChanges: (File) -> Unit,
            onExtractPages: (File, Int) -> Unit
        ): PdfPageOrganizerDialog {
            val dialog = PdfPageOrganizerDialog()
            dialog.pdfFile = pdfFile
            dialog.onApplyChangesListener = onApplyChanges
            dialog.onExtractPagesListener = onExtractPages
            return dialog
        }
    }
}
