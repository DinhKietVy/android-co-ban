package com.example.filemanagementapp.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import io.noties.markwon.Markwon

class AiChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    private var markwon: Markwon? = null

    private fun getMarkwon(context: Context): Markwon {
        return markwon ?: Markwon.create(context).also { markwon = it }
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
        private const val VIEW_TYPE_TYPING = 3
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when {
            item.isTyping -> VIEW_TYPE_TYPING
            item.isUser -> VIEW_TYPE_USER
            else -> VIEW_TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_chat_user, parent, false)
                UserViewHolder(view)
            }
            VIEW_TYPE_TYPING -> {
                val view = inflater.inflate(R.layout.item_chat_typing, parent, false)
                TypingViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_chat_ai, parent, false)
                AiViewHolder(view, getMarkwon(parent.context))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is UserViewHolder -> holder.bind(item)
            is AiViewHolder -> holder.bind(item)
            is TypingViewHolder -> { /* no-op */ }
        }
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.userMessageText)
        private val fileContainer: View = itemView.findViewById(R.id.userFileContainer)
        private val fileNameText: TextView = itemView.findViewById(R.id.userFileName)

        fun bind(item: ChatMessage) {
            messageText.text = item.text
            if (!item.filePath.isNullOrBlank()) {
                fileContainer.visibility = View.VISIBLE
                fileNameText.text = item.filePath.substringAfterLast('/')
            } else {
                fileContainer.visibility = View.GONE
            }
        }
    }

    class AiViewHolder(
        itemView: View,
        private val markwon: Markwon
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.aiMessageText)
        private val actionsContainer: View = itemView.findViewById(R.id.actionsContainer)
        private val actionDetailsText: TextView = itemView.findViewById(R.id.actionDetailsText)

        fun bind(item: ChatMessage) {
            markwon.setMarkdown(messageText, item.text)

            val actions = item.actionsExecuted.orEmpty()
            val affected = item.affectedItems.orEmpty()

            if (actions.isNotEmpty() || affected.isNotEmpty()) {
                actionsContainer.visibility = View.VISIBLE
                val summaryLines = mutableListOf<String>()
                if (actions.isNotEmpty()) {
                    summaryLines.add(actions.joinToString("\n") { "✓ $it" })
                }
                if (affected.isNotEmpty()) {
                    summaryLines.add("Tệp tác động: " + affected.joinToString(", "))
                }
                actionDetailsText.text = summaryLines.joinToString("\n")
            } else {
                actionsContainer.visibility = View.GONE
            }
        }
    }

    class TypingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem
    }
}
