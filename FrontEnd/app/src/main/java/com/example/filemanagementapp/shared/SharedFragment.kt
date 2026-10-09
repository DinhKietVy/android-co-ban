package com.example.filemanagementapp.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.data.explorer.repository.ExplorerRepository
import com.example.filemanagementapp.explorer.ExplorerAdapter
import com.example.filemanagementapp.explorer.ExplorerItem
import com.example.filemanagementapp.preview.FilePreviewActivity
import kotlinx.coroutines.launch

class SharedFragment : Fragment() {
    private var username: String = ""
    private lateinit var viewModel: SharedViewModel
    private lateinit var adapter: ExplorerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        username = arguments?.getString(ARG_USERNAME).orEmpty()
        
        val repository = ExplorerRepository(
            requireContext().applicationContext,
            ExplorerNetworkModule.explorerApiService,
            ExplorerNetworkModule.gson
        )
        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SharedViewModel(username, repository) as T
                }
            }
        )[SharedViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shared, container, false)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.sharedRecyclerView)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val emptyStateContainer = view.findViewById<LinearLayout>(R
        .id.emptyStateContainer)
        
        view.findViewById<View>(R.id.headerMenuButton)?.setOnClickListener {
            (activity as? com.example.filemanagementapp.main.MainActivity)?.openDrawer()
        }

        adapter = ExplorerAdapter(
            onItemClick = { item ->
                val intent = FilePreviewActivity.newIntent(
                    context = requireContext(),
                    item = item,
                    username = username,
                    analyzedImagePath = null,
                    ocrText = null,
                    aiTags = emptyList()
                )
                startActivity(intent)
            },
            onMoreClick = { item ->
                if (item.permission == "WRITE") {
                    showActionMenu(item)
                } else {
                    Toast.makeText(requireContext(), "Quyền CHỈ XEM (READ)", Toast.LENGTH_SHORT).show()
                }
            },
            onItemSelectionToggle = { _ -> },
            onItemLongPress = { item ->
                if (item.permission == "WRITE") {
                    showActionMenu(item)
                } else {
                    Toast.makeText(requireContext(), "Quyền CHỈ XEM (READ) - Của: ${item.ownerUsername}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progressBar.isVisible = state.isLoading
                    emptyStateContainer.isVisible = !state.isLoading && state.items.isEmpty()
                    recyclerView.isVisible = state.items.isNotEmpty()
                    adapter.submitItems(
                        newItems = state.items,
                        isSelectionMode = false,
                        selectedPaths = emptySet(),
                        displayMode = com.example.filemanagementapp.explorer.ExplorerAdapter.DisplayMode.LIST
                    )
                    
                    if (state.error != null) {
                        Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewModel.load()
        return view
    }

    private fun showActionMenu(item: ExplorerItem) {
        val popupMenu = androidx.appcompat.widget.PopupMenu(requireContext(), requireView())
        popupMenu.menu.add(0, 1, 0, "Đổi tên")
        popupMenu.menu.add(0, 2, 1, "Xóa")
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    showRenameDialog(item)
                    true
                }
                2 -> {
                    showDeleteDialog(item)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showRenameDialog(item: ExplorerItem) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val inputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialogInputLayout)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialogInputEditText)
        
        inputLayout.hint = getString(R.string.explorer_dialog_rename_hint)
        input.setText(item.name)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explorer_dialog_rename_title)
            .setView(view)
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                val newName = input.text?.toString().orEmpty().trim()
                if (newName.isNotBlank() && newName != item.name) {
                    viewModel.renameItem(item, newName)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(item: ExplorerItem) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explorer_dialog_delete_title)
            .setMessage(getString(R.string.explorer_dialog_delete_message, item.name))
            .setPositiveButton(R.string.explorer_dialog_confirm) { _, _ ->
                viewModel.deleteItem(item)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val ARG_USERNAME = "username"

        fun newInstance(username: String) = SharedFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_USERNAME, username)
            }
        }
    }
}
