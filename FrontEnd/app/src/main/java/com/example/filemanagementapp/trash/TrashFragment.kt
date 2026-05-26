package com.example.filemanagementapp.trash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
class TrashFragment : Fragment() {
    private val selectedIds = linkedSetOf<String>()
    private lateinit var adapter: TrashAdapter
    private lateinit var emptyStateContainer: View
    private lateinit var infoCard: View
    private lateinit var bulkActionBar: LinearLayout
    private lateinit var selectedCountText: TextView
    private val trashItems = mutableListOf<TrashItemModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_trash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.trashRecyclerView)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        infoCard = view.findViewById(R.id.infoCard)
        bulkActionBar = view.findViewById(R.id.bulkActionBar)
        selectedCountText = view.findViewById(R.id.selectedCountText)

        trashItems += sampleItems()
        adapter = TrashAdapter(trashItems.toList(), selectedIds) { id ->
            toggleSelected(id)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.restoreSelectedButton).setOnClickListener {
            removeSelectedItems()
        }
        view.findViewById<View>(R.id.deleteSelectedButton).setOnClickListener {
            removeSelectedItems()
        }

        renderState()
    }

    private fun toggleSelected(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        adapter.notifyDataSetChanged()
        renderSelectionState()
    }

    private fun removeSelectedItems() {
        trashItems.removeAll { selectedIds.contains(it.id) }
        selectedIds.clear()
        adapter.submitItems(trashItems.toList())
        renderState()
    }

    private fun renderState() {
        val hasItems = trashItems.isNotEmpty()
        view?.findViewById<RecyclerView>(R.id.trashRecyclerView)?.isVisible = hasItems
        infoCard.isVisible = hasItems
        emptyStateContainer.isVisible = !hasItems
        renderSelectionState()
    }

    private fun renderSelectionState() {
        bulkActionBar.isVisible = selectedIds.isNotEmpty()
        selectedCountText.text = getString(R.string.trash_selected_count, selectedIds.size)
    }

    private fun sampleItems(): List<TrashItemModel> = listOf(
        TrashItemModel("1", "Project Documents", TrashItemModel.Type.FOLDER, "2 days ago", 28, itemCount = 24),
        TrashItemModel("2", "Receipt_Amazon_2026.jpg", TrashItemModel.Type.IMAGE, "3 days ago", 27, size = "2.4 MB", aiAnalyzed = true, aiTags = listOf("Receipt", "Invoice"), ocrPreview = "Amazon.com Order #123-4567890-123..."),
        TrashItemModel("3", "Laptop_Setup_Guide.pdf", TrashItemModel.Type.DOCUMENT, "5 days ago", 25, size = "1.8 MB", aiAnalyzed = true, aiTags = listOf("Laptop", "Manual")),
        TrashItemModel("4", "Vacation_Photos", TrashItemModel.Type.FOLDER, "1 week ago", 23, itemCount = 86),
        TrashItemModel("5", "Beach_Sunset.jpg", TrashItemModel.Type.IMAGE, "1 week ago", 23, size = "5.2 MB", aiAnalyzed = true, aiTags = listOf("Sunset", "Beach")),
        TrashItemModel("6", "Budget_2026.xlsx", TrashItemModel.Type.SPREADSHEET, "10 days ago", 20, size = "342 KB"),
        TrashItemModel("7", "Product_Demo.mp4", TrashItemModel.Type.VIDEO, "2 weeks ago", 16, size = "45.8 MB", aiAnalyzed = true, aiTags = listOf("Product", "Demo")),
        TrashItemModel("8", "Business_Card.jpg", TrashItemModel.Type.IMAGE, "3 weeks ago", 9, size = "1.1 MB", aiAnalyzed = true, aiTags = listOf("Business Card", "Contact"), ocrPreview = "John Smith - Senior Developer...")
    )
}
