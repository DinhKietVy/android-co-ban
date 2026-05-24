package com.example.filemanagementapp.explorer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R

class ExplorerFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_explorer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.explorerRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = ExplorerAdapter(sampleItems())
    }

    private fun sampleItems(): List<ExplorerItem> {
        return listOf(
            ExplorerItem(
                id = "1",
                name = "Work Documents",
                type = ExplorerItem.Type.FOLDER,
                itemCount = 24,
                modified = "May 15, 2026"
            ),
            ExplorerItem(
                id = "2",
                name = "Photos",
                type = ExplorerItem.Type.FOLDER,
                itemCount = 156,
                modified = "May 18, 2026"
            ),
            ExplorerItem(
                id = "3",
                name = "Invoice_2026.pdf",
                type = ExplorerItem.Type.FILE,
                size = "245 KB",
                modified = "May 19, 2026",
                aiTags = listOf("Invoice", "Receipt"),
                aiAnalyzed = true
            ),
            ExplorerItem(
                id = "4",
                name = "Mountain_Trip.jpg",
                type = ExplorerItem.Type.FILE,
                size = "3.2 MB",
                modified = "May 17, 2026",
                aiTags = listOf("Nature", "Mountains"),
                aiAnalyzed = true
            ),
            ExplorerItem(
                id = "5",
                name = "Downloads",
                type = ExplorerItem.Type.FOLDER,
                itemCount = 48,
                modified = "May 19, 2026"
            ),
            ExplorerItem(
                id = "6",
                name = "Meeting_Notes.pdf",
                type = ExplorerItem.Type.FILE,
                size = "128 KB",
                modified = "May 14, 2026",
                aiTags = listOf("Notes", "Office")
            )
        )
    }
}
