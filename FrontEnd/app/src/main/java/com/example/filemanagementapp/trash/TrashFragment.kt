package com.example.filemanagementapp.trash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TrashFragment : Fragment() {
    private val selectedIds = linkedSetOf<String>()
    private lateinit var adapter: TrashAdapter
    private lateinit var emptyStateContainer: View
    private lateinit var infoCard: View
    private lateinit var bulkActionBar: LinearLayout
    private lateinit var selectedCountText: TextView
    private val trashItems = mutableListOf<TrashItemModel>()

    private lateinit var defaultTopBar: View
    private lateinit var searchTopBar: View
    private lateinit var searchInput: EditText
    private lateinit var closeSearchButton: View
    private lateinit var clearSearchButton: View

    private var searchQuery: String = ""
    private var sortBy = "date"
    private var sortDir = "desc"

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

        defaultTopBar = view.findViewById(R.id.defaultTopBar)
        searchTopBar = view.findViewById(R.id.searchTopBar)
        searchInput = view.findViewById(R.id.searchInput)
        closeSearchButton = view.findViewById(R.id.closeSearchButton)
        clearSearchButton = view.findViewById(R.id.clearSearchButton)

        trashItems += sampleItems()
        
        adapter = TrashAdapter(
            items = getSortedItems(),
            selectedIds = selectedIds,
            onToggleSelect = { id -> toggleSelected(id) },
            onMoreClick = { item, anchorView -> showItemMoreMenu(item, anchorView) }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.restoreSelectedButton).setOnClickListener {
            removeSelectedItems(getString(R.string.trash_bulk_restored))
        }
        view.findViewById<View>(R.id.deleteSelectedButton).setOnClickListener {
            removeSelectedItems(getString(R.string.trash_bulk_deleted))
        }
        view.findViewById<View>(R.id.closeSelectionButton)?.setOnClickListener {
            selectedIds.clear()
            adapter.notifyDataSetChanged()
            renderSelectionState()
        }

        view.findViewById<View>(R.id.topSortButton)?.setOnClickListener {
            showSortMenu(it)
        }
        view.findViewById<View>(R.id.topMoreButton)?.setOnClickListener {
            showTopMoreMenu(it)
        }
        view.findViewById<View>(R.id.topSearchButton)?.setOnClickListener {
            defaultTopBar.visibility = View.GONE
            searchTopBar.visibility = View.VISIBLE
            searchInput.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        closeSearchButton.setOnClickListener {
            searchInput.setText("")
            searchInput.clearFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
            searchTopBar.visibility = View.GONE
            defaultTopBar.visibility = View.VISIBLE
        }

        clearSearchButton.setOnClickListener {
            searchInput.setText("")
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                clearSearchButton.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                adapter.submitItems(getSortedItems())
                renderState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        renderState()
    }

    private fun showSortMenu(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_trash_sort, null)
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val menuSortDate = popupView.findViewById<View>(R.id.menuSortDate)
        val menuSortName = popupView.findViewById<View>(R.id.menuSortName)
        val menuSortSize = popupView.findViewById<View>(R.id.menuSortSize)
        val menuSortDaysLeft = popupView.findViewById<View>(R.id.menuSortDaysLeft)

        val textSortDate = popupView.findViewById<android.widget.TextView>(R.id.textSortDate)
        val textSortName = popupView.findViewById<android.widget.TextView>(R.id.textSortName)
        val textSortSize = popupView.findViewById<android.widget.TextView>(R.id.textSortSize)
        val textSortDaysLeft = popupView.findViewById<android.widget.TextView>(R.id.textSortDaysLeft)

        val iconSortDate = popupView.findViewById<android.widget.ImageView>(R.id.iconSortDate)
        val iconSortName = popupView.findViewById<android.widget.ImageView>(R.id.iconSortName)
        val iconSortSize = popupView.findViewById<android.widget.ImageView>(R.id.iconSortSize)
        val iconSortDaysLeft = popupView.findViewById<android.widget.ImageView>(R.id.iconSortDaysLeft)

        val indicatorSortDate = popupView.findViewById<android.widget.ImageView>(R.id.indicatorSortDate)
        val indicatorSortName = popupView.findViewById<android.widget.ImageView>(R.id.indicatorSortName)
        val indicatorSortSize = popupView.findViewById<android.widget.ImageView>(R.id.indicatorSortSize)
        val indicatorSortDaysLeft = popupView.findViewById<android.widget.ImageView>(R.id.indicatorSortDaysLeft)

        val colorSelected = android.graphics.Color.parseColor("#0D6EFD")
        val colorUnselectedText = android.graphics.Color.parseColor("#111827")
        val colorUnselectedIcon = android.graphics.Color.parseColor("#374151")

        fun updateUI() {
            textSortDate.setTextColor(colorUnselectedText)
            textSortName.setTextColor(colorUnselectedText)
            textSortSize.setTextColor(colorUnselectedText)
            textSortDaysLeft.setTextColor(colorUnselectedText)

            iconSortDate.setColorFilter(colorUnselectedIcon)
            iconSortName.setColorFilter(colorUnselectedIcon)
            iconSortSize.setColorFilter(colorUnselectedIcon)
            iconSortDaysLeft.setColorFilter(colorUnselectedIcon)

            indicatorSortDate.visibility = View.INVISIBLE
            indicatorSortName.visibility = View.INVISIBLE
            indicatorSortSize.visibility = View.INVISIBLE
            indicatorSortDaysLeft.visibility = View.INVISIBLE

            val (selectedText, selectedIcon, selectedIndicator) = when(sortBy) {
                "date" -> Triple(textSortDate, iconSortDate, indicatorSortDate)
                "name" -> Triple(textSortName, iconSortName, indicatorSortName)
                "size" -> Triple(textSortSize, iconSortSize, indicatorSortSize)
                "daysLeft" -> Triple(textSortDaysLeft, iconSortDaysLeft, indicatorSortDaysLeft)
                else -> Triple(textSortDate, iconSortDate, indicatorSortDate)
            }

            selectedText.setTextColor(colorSelected)
            selectedIcon.setColorFilter(colorSelected)
            selectedIndicator.visibility = View.VISIBLE
        }
        
        updateUI()

        fun handleSelect(newSortBy: String) {
            if (sortBy == newSortBy) {
                sortDir = if (sortDir == "asc") "desc" else "asc"
            } else {
                sortBy = newSortBy
                sortDir = "asc"
            }
            adapter.submitItems(getSortedItems())
            popupWindow.dismiss()
        }

        menuSortDate.setOnClickListener { handleSelect("date") }
        menuSortName.setOnClickListener { handleSelect("name") }
        menuSortSize.setOnClickListener { handleSelect("size") }
        menuSortDaysLeft.setOnClickListener { handleSelect("daysLeft") }

        popupWindow.showAsDropDown(anchor, -350, 16)
    }

    private fun showTopMoreMenu(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_trash_more, null)
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val isAllSelected = selectedIds.size == trashItems.size && trashItems.isNotEmpty()
        val textSelectAll = popupView.findViewById<android.widget.TextView>(R.id.textSelectAll)
        textSelectAll.text = if (isAllSelected) getString(R.string.trash_deselect_all) else getString(R.string.trash_select_all)

        popupView.findViewById<View>(R.id.menuSelectAll).setOnClickListener {
            popupWindow.dismiss()
            if (isAllSelected) {
                selectedIds.clear()
            } else {
                selectedIds.clear()
                selectedIds.addAll(trashItems.map { it.id })
            }
            adapter.notifyDataSetChanged()
            renderSelectionState()
        }
        
        popupView.findViewById<View>(R.id.menuEmptyTrash).setOnClickListener {
            popupWindow.dismiss()
            showEmptyTrashConfirmDialog()
        }

        popupWindow.showAsDropDown(anchor, -300, 16)
    }

    private fun showItemMoreMenu(item: TrashItemModel, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, getString(R.string.trash_menu_restore))
        popup.menu.add(0, 2, 0, getString(R.string.trash_menu_download))
        popup.menu.add(0, 3, 0, getString(R.string.trash_menu_details))
        popup.menu.add(0, 4, 0, getString(R.string.trash_menu_delete_forever))
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    trashItems.removeAll { it.id == item.id }
                    selectedIds.remove(item.id)
                    adapter.submitItems(getSortedItems())
                    renderState()
                    Toast.makeText(requireContext(), getString(R.string.trash_restored_item, item.name), Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    Toast.makeText(requireContext(), getString(R.string.trash_downloading_item, item.name), Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    Toast.makeText(requireContext(), getString(R.string.trash_details_item, item.name), Toast.LENGTH_SHORT).show()
                }
                4 -> {
                    trashItems.removeAll { it.id == item.id }
                    selectedIds.remove(item.id)
                    adapter.submitItems(getSortedItems())
                    renderState()
                    Toast.makeText(requireContext(), getString(R.string.trash_deleted_item, item.name), Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        popup.show()
    }

    private fun showEmptyTrashConfirmDialog() {
        if (trashItems.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.trash_empty_confirm_title))
            .setMessage(getString(R.string.trash_empty_confirm_message, trashItems.size))
            .setPositiveButton(getString(R.string.trash_empty_confirm_positive)) { _, _ ->
                trashItems.clear()
                selectedIds.clear()
                adapter.submitItems(emptyList())
                renderState()
                Toast.makeText(requireContext(), getString(R.string.trash_emptied), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.trash_empty_confirm_negative), null)
            .show()
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

    private fun removeSelectedItems(messagePrefix: String) {
        val count = selectedIds.size
        trashItems.removeAll { selectedIds.contains(it.id) }
        selectedIds.clear()
        adapter.submitItems(getSortedItems())
        renderState()
        Toast.makeText(requireContext(), getString(R.string.trash_bulk_action_message, messagePrefix, count), Toast.LENGTH_SHORT).show()
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

    private fun getSortedItems(): List<TrashItemModel> {
        val filtered = if (searchQuery.isBlank()) {
            trashItems
        } else {
            val q = searchQuery.lowercase()
            trashItems.filter { it.name.lowercase().contains(q) }
        }
        
        val sorted = filtered.sortedWith { a, b ->
            val cmp = when (sortBy) {
                "name" -> a.name.compareTo(b.name, ignoreCase = true)
                "size" -> {
                    val sizeA = a.size?.replace(Regex("[^0-9.]"), "")?.toFloatOrNull() ?: 0f
                    val sizeB = b.size?.replace(Regex("[^0-9.]"), "")?.toFloatOrNull() ?: 0f
                    sizeA.compareTo(sizeB)
                }
                "daysLeft" -> a.daysUntilRemoval.compareTo(b.daysUntilRemoval)
                else -> b.daysUntilRemoval.compareTo(a.daysUntilRemoval) // date: more recently deleted = fewer days left remaining -> larger daysLeft = older deletion
            }
            if (sortDir == "asc") cmp else -cmp
        }
        return sorted
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
