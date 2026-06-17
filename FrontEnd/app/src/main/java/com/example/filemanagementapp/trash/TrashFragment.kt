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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class TrashFragment : Fragment() {
    companion object {
        const val ARG_USERNAME = "arg_username"
        fun newInstance(username: String): TrashFragment {
            return TrashFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                }
            }
        }
    }

    private val username: String
        get() = arguments?.getString(ARG_USERNAME) ?: "tester"

    private val selectedIds = linkedSetOf<String>()
    private lateinit var adapter: TrashAdapter
    private lateinit var emptyStateContainer: View
    private lateinit var infoCard: View
    private lateinit var bulkActionBar: LinearLayout
    private lateinit var selectedCountText: TextView

    private lateinit var defaultTopBar: View
    private lateinit var searchTopBar: View
    private lateinit var searchInput: EditText
    private lateinit var closeSearchButton: View
    private lateinit var clearSearchButton: View

    private var searchQuery: String = ""
    private var sortBy = "date"
    private var sortDir = "desc"

    private lateinit var viewModel: TrashViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_trash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val explorerRepository = ExplorerRepository(
            appContext = requireContext().applicationContext,
            explorerApiService = ExplorerNetworkModule.explorerApiService,
            gson = ExplorerNetworkModule.gson
        )
        
        viewModel = ViewModelProvider(
            this,
            TrashViewModel.Factory(username, explorerRepository)
        )[TrashViewModel::class.java]

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

        adapter = TrashAdapter(
            items = emptyList(),
            selectedIds = selectedIds,
            onToggleSelect = { id -> toggleSelected(id) },
            onMoreClick = { item, anchorView -> showItemMoreMenu(item, anchorView) }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.restoreSelectedButton).setOnClickListener {
            val count = selectedIds.size
            viewModel.restoreItems(selectedIds.toSet())
            selectedIds.clear()
            renderSelectionState()
            Toast.makeText(requireContext(), getString(R.string.trash_bulk_restored, count), Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.deleteSelectedButton).setOnClickListener {
            val count = selectedIds.size
            viewModel.deleteItemsForever(selectedIds.toSet())
            selectedIds.clear()
            renderSelectionState()
            Toast.makeText(requireContext(), getString(R.string.trash_bulk_deleted, count), Toast.LENGTH_SHORT).show()
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
                updateAdapterData(viewModel.uiState.value.items)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.loadTrash()
        }
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
            updateAdapterData(viewModel.uiState.value.items)
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

        val currentItems = viewModel.uiState.value.items
        val isAllSelected = selectedIds.size == currentItems.size && currentItems.isNotEmpty()
        val textSelectAll = popupView.findViewById<android.widget.TextView>(R.id.textSelectAll)
        textSelectAll.text = if (isAllSelected) getString(R.string.trash_deselect_all) else getString(R.string.trash_select_all)

        popupView.findViewById<View>(R.id.menuSelectAll).setOnClickListener {
            popupWindow.dismiss()
            if (isAllSelected) {
                selectedIds.clear()
            } else {
                selectedIds.clear()
                selectedIds.addAll(currentItems.map { it.id })
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
        popup.menu.add(0, 4, 0, getString(R.string.trash_menu_delete_forever))
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    viewModel.restoreItems(setOf(item.id))
                    selectedIds.remove(item.id)
                    renderSelectionState()
                    Toast.makeText(requireContext(), getString(R.string.trash_restored_item, item.name), Toast.LENGTH_SHORT).show()
                }
                4 -> {
                    viewModel.deleteItemsForever(setOf(item.id))
                    selectedIds.remove(item.id)
                    renderSelectionState()
                    Toast.makeText(requireContext(), getString(R.string.trash_deleted_item, item.name), Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        popup.show()
    }

    private fun showEmptyTrashConfirmDialog() {
        val currentItems = viewModel.uiState.value.items
        if (currentItems.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.trash_empty_confirm_title))
            .setMessage(getString(R.string.trash_empty_confirm_message, currentItems.size))
            .setPositiveButton(getString(R.string.trash_empty_confirm_positive)) { _, _ ->
                viewModel.emptyTrash()
                selectedIds.clear()
                renderSelectionState()
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

    private fun renderState(state: TrashUiState) {
        val hasItems = state.items.isNotEmpty()
        view?.findViewById<RecyclerView>(R.id.trashRecyclerView)?.isVisible = hasItems && !state.isLoading
        infoCard.isVisible = hasItems && !state.isLoading
        emptyStateContainer.isVisible = !hasItems && !state.isLoading
        
        if (!state.isLoading) {
            updateAdapterData(state.items)
        }
        
        renderSelectionState()
        
        state.errorMessage?.let { msg ->
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAdapterData(items: List<TrashItemModel>) {
        val filtered = if (searchQuery.isBlank()) {
            items
        } else {
            val q = searchQuery.lowercase()
            items.filter { it.name.lowercase().contains(q) }
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
                else -> b.daysUntilRemoval.compareTo(a.daysUntilRemoval)
            }
            if (sortDir == "asc") cmp else -cmp
        }
        adapter.submitItems(sorted)
    }

    private fun renderSelectionState() {
        bulkActionBar.isVisible = selectedIds.isNotEmpty()
        selectedCountText.text = getString(R.string.trash_selected_count, selectedIds.size)
    }
}
