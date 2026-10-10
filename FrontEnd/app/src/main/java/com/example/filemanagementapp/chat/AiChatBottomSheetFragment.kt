package com.example.filemanagementapp.chat

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.ai.network.AiNetworkModule
import com.example.filemanagementapp.data.ai.repository.AiChatRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AiChatBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "AiChatBottomSheetFragment"
        private const val ARG_USERNAME = "arg_username"
        private const val ARG_FILE_PATH = "arg_file_path"

        fun newInstance(username: String, filePath: String? = null): AiChatBottomSheetFragment {
            return AiChatBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                    putString(ARG_FILE_PATH, filePath)
                }
            }
        }
    }

    private var username: String = ""
    private var initialFilePath: String? = null

    private val viewModel: AiChatViewModel by viewModels {
        val repo = AiChatRepository(AiNetworkModule.aiApiService)
        AiChatViewModel.Factory(username, repo, initialFilePath)
    }

    private lateinit var adapter: AiChatAdapter
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var emptySuggestionsLayout: View
    private lateinit var chatInputEditText: EditText
    private lateinit var chatSendButton: FrameLayout
    private lateinit var chatSendIcon: ImageView
    private lateinit var chatSendProgress: ProgressBar
    private lateinit var attachedFileBar: View
    private lateinit var attachedFileNameText: TextView
    private lateinit var chatSheetSubtitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        username = arguments?.getString(ARG_USERNAME).orEmpty()
        initialFilePath = arguments?.getString(ARG_FILE_PATH)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                val displayMetrics = resources.displayMetrics
                val height = (displayMetrics.heightPixels * 0.85).toInt()
                bottomSheet.layoutParams.height = height
                bottomSheet.requestLayout()
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_ai_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupRecycler()
        setupListeners(view)
        observeViewModel()
    }

    private fun bindViews(view: View) {
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView)
        emptySuggestionsLayout = view.findViewById(R.id.emptyChatSuggestionsLayout)
        chatInputEditText = view.findViewById(R.id.chatInputEditText)
        chatSendButton = view.findViewById(R.id.chatSendButton)
        chatSendIcon = view.findViewById(R.id.chatSendIcon)
        chatSendProgress = view.findViewById(R.id.chatSendProgress)
        attachedFileBar = view.findViewById(R.id.attachedFileBar)
        attachedFileNameText = view.findViewById(R.id.attachedFileNameText)
        chatSheetSubtitle = view.findViewById(R.id.chatSheetSubtitle)
    }

    private fun setupRecycler() {
        adapter = AiChatAdapter()
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        chatRecyclerView.layoutManager = layoutManager
        chatRecyclerView.adapter = adapter
    }

    private fun setupListeners(view: View) {
        view.findViewById<View>(R.id.closeSheetButton).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.newChatButton).setOnClickListener {
            viewModel.startNewSession()
            Toast.makeText(requireContext(), "Đã bắt đầu cuộc trò chuyện mới", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.historySessionsButton).setOnClickListener {
            showSessionsHistoryDialog()
        }

        view.findViewById<View>(R.id.removeAttachedFileButton).setOnClickListener {
            viewModel.detachFile()
        }

        chatSendButton.setOnClickListener {
            val text = chatInputEditText.text.toString().trim()
            if (text.isNotBlank()) {
                chatInputEditText.setText("")
                viewModel.sendMessage(text)
            }
        }

        // Suggestions
        view.findViewById<View>(R.id.suggestionCreateFolder)?.setOnClickListener {
            viewModel.sendMessage("Tạo cho tôi một thư mục tên 'Báo cáo'")
        }
        view.findViewById<View>(R.id.suggestionListFiles)?.setOnClickListener {
            viewModel.sendMessage("Liệt kê danh sách các tệp tin hiện có")
        }
        view.findViewById<View>(R.id.suggestionCleanTrash)?.setOnClickListener {
            viewModel.sendMessage("Tôi đang có những tệp nào trong thùng rác?")
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { list ->
                        adapter.submitList(list) {
                            if (list.isNotEmpty()) {
                                chatRecyclerView.smoothScrollToPosition(list.size - 1)
                            }
                        }
                        emptySuggestionsLayout.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        chatRecyclerView.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        chatSendProgress.visibility = if (loading) View.VISIBLE else View.GONE
                        chatSendIcon.visibility = if (loading) View.GONE else View.VISIBLE
                        chatSendButton.isEnabled = !loading
                    }
                }

                launch {
                    viewModel.attachedFilePath.collect { path ->
                        if (!path.isNullOrBlank()) {
                            attachedFileBar.visibility = View.VISIBLE
                            val name = path.trimEnd('/').substringAfterLast('/')
                            attachedFileNameText.text = "Đang hỏi về: $name"
                        } else {
                            attachedFileBar.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.currentSessionId.collect { id ->
                        if (id != null) {
                            chatSheetSubtitle.text = "Phiên #$id • Trợ lý tệp tin"
                        } else {
                            chatSheetSubtitle.text = "Quản lý và thao tác tệp thông minh"
                        }
                    }
                }
            }
        }
    }

    private fun showSessionsHistoryDialog() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_chat_sessions, null)
        bottomSheet.setContentView(sheetView)

        val recyclerView = sheetView.findViewById<RecyclerView>(R.id.chatSessionsRecyclerView)
        val emptyView = sheetView.findViewById<TextView>(R.id.emptySessionsText)
        val btnNew = sheetView.findViewById<View>(R.id.btnNewChatSession)

        btnNew.setOnClickListener {
            bottomSheet.dismiss()
            viewModel.startNewSession()
            Toast.makeText(requireContext(), "Đã bắt đầu cuộc trò chuyện mới", Toast.LENGTH_SHORT).show()
        }

        fun updateListVisibility(count: Int) {
            if (count == 0) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }

        class SessionAdapter(
            private var items: MutableList<com.example.filemanagementapp.data.ai.model.ChatSessionItem>
        ) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

            inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
                val title: TextView = itemView.findViewById(R.id.sessionTitleText)
                val date: TextView = itemView.findViewById(R.id.sessionDateText)
                val deleteBtn: View = itemView.findViewById(R.id.deleteSessionButton)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = layoutInflater.inflate(R.layout.item_chat_session, parent, false)
                return ViewHolder(view)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val session = items[position]
                holder.title.text = session.title ?: "Cuộc trò chuyện #${session.id}"
                holder.date.text = session.createdAt ?: ""
                holder.itemView.setOnClickListener {
                    bottomSheet.dismiss()
                    viewModel.switchSession(session.id)
                }
                holder.deleteBtn.setOnClickListener {
                    viewModel.deleteSession(session.id)
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        items.removeAt(pos)
                        notifyItemRemoved(pos)
                        updateListVisibility(items.size)
                    }
                }
            }

            override fun getItemCount(): Int = items.size
        }

        val currentSessions = viewModel.sessions.value.toMutableList()
        updateListVisibility(currentSessions.size)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = SessionAdapter(currentSessions)

        bottomSheet.show()
    }
}
