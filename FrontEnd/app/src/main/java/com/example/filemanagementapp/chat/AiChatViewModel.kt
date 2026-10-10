package com.example.filemanagementapp.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanagementapp.data.ai.model.ChatMessageItem
import com.example.filemanagementapp.data.ai.model.ChatSessionItem
import com.example.filemanagementapp.data.ai.repository.AiChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiChatViewModel(
    private val username: String,
    private val aiChatRepository: AiChatRepository,
    initialFilePath: String? = null
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSessionItem>>(emptyList())
    val sessions: StateFlow<List<ChatSessionItem>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Int?>(null)
    val currentSessionId: StateFlow<Int?> = _currentSessionId.asStateFlow()

    private val _attachedFilePath = MutableStateFlow<String?>(initialFilePath)
    val attachedFilePath: StateFlow<String?> = _attachedFilePath.asStateFlow()

    init {
        loadSessions()
    }

    fun attachFile(filePath: String) {
        _attachedFilePath.value = filePath
    }

    fun detachFile() {
        _attachedFilePath.value = null
    }

    fun loadSessions() {
        viewModelScope.launch {
            val result = aiChatRepository.getSessions(username)
            if (result.isSuccess) {
                _sessions.value = result.getOrNull().orEmpty()
            }
        }
    }

    fun startNewSession() {
        _currentSessionId.value = null
        _messages.value = emptyList()
    }

    fun switchSession(sessionId: Int) {
        _currentSessionId.value = sessionId
        viewModelScope.launch {
            _isLoading.value = true
            val result = aiChatRepository.getSessionMessages(sessionId)
            if (result.isSuccess) {
                val dbMessages = result.getOrNull().orEmpty()
                _messages.value = dbMessages.map { item ->
                    ChatMessage(
                        id = item.id.toString(),
                        isUser = item.role.equals("user", ignoreCase = true),
                        text = item.content,
                        filePath = item.filePath,
                        actionsExecuted = item.actionsExecuted,
                        affectedItems = item.affectedItems
                    )
                }
            }
            _isLoading.value = false
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            val result = aiChatRepository.deleteSession(sessionId)
            if (result.isSuccess) {
                if (_currentSessionId.value == sessionId) {
                    startNewSession()
                }
                loadSessions()
            }
        }
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _isLoading.value) return

        val filePath = _attachedFilePath.value
        val userMsg = ChatMessage(
            isUser = true,
            text = trimmed,
            filePath = filePath
        )

        val typingMsg = ChatMessage(
            isUser = false,
            text = "",
            isTyping = true
        )

        // Optimistically add user message and typing indicator
        _messages.value = _messages.value + userMsg + typingMsg
        _isLoading.value = true

        viewModelScope.launch {
            val result = aiChatRepository.sendMessage(
                username = username,
                prompt = trimmed,
                sessionId = _currentSessionId.value,
                filePath = filePath
            )

            // Remove typing indicator
            val currentList = _messages.value.filter { !it.isTyping }

            if (result.isSuccess) {
                val response = result.getOrNull()
                val newSessionId = response?.sessionId
                if (newSessionId != null && _currentSessionId.value == null) {
                    _currentSessionId.value = newSessionId
                    loadSessions()
                }

                val chatData = response?.data
                val aiMsg = ChatMessage(
                    isUser = false,
                    text = chatData?.text ?: "Không có phản hồi từ máy chủ.",
                    actionsExecuted = chatData?.actionsExecuted,
                    affectedItems = chatData?.affectedItems
                )
                _messages.value = currentList + aiMsg
            } else {
                val errorMsg = ChatMessage(
                    isUser = false,
                    text = "⚠️ " + (result.exceptionOrNull()?.message ?: "Lỗi kết nối tới AI chatbot")
                )
                _messages.value = currentList + errorMsg
            }
            _isLoading.value = false
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val username: String,
        private val aiChatRepository: AiChatRepository,
        private val initialFilePath: String? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AiChatViewModel(username, aiChatRepository, initialFilePath) as T
        }
    }
}
