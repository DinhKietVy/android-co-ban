package com.example.filemanagementapp.recent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class RecentFragment : Fragment() {
    private lateinit var adapter: RecentAdapter
    private lateinit var quickAccessContainer: HorizontalScrollView
    private lateinit var quickAccessRow: LinearLayout
    private lateinit var searchInputLayout: View
    private lateinit var searchEditText: TextInputEditText
    private lateinit var recentTabButton: MaterialButton
    private lateinit var favoritesTabButton: MaterialButton
    private var activeTab: Tab = Tab.RECENT

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_recent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recentRecyclerView)
        quickAccessContainer = view.findViewById(R.id.quickAccessContainer)
        quickAccessRow = view.findViewById(R.id.quickAccessRow)
        searchInputLayout = view.findViewById(R.id.searchInputLayout)
        searchEditText = view.findViewById(R.id.searchEditText)
        recentTabButton = view.findViewById(R.id.recentTabButton)
        favoritesTabButton = view.findViewById(R.id.favoritesTabButton)

        adapter = RecentAdapter(buildRecentList(""))
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        renderQuickAccess()
        setupTabs()
        setupSearch(view)
        renderTabState()
    }

    private fun setupTabs() {
        recentTabButton.setOnClickListener {
            activeTab = Tab.RECENT
            renderTabState()
        }
        favoritesTabButton.setOnClickListener {
            activeTab = Tab.FAVORITES
            renderTabState()
        }
    }

    private fun setupSearch(root: View) {
        root.findViewById<ImageButton>(R.id.searchButton).setOnClickListener {
            searchInputLayout.isVisible = !searchInputLayout.isVisible
            if (!searchInputLayout.isVisible) {
                searchEditText.setText("")
            }
        }
        searchEditText.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty()
            submitCurrentList(query)
        }
    }

    private fun renderTabState() {
        val context = requireContext()
        val selectedBg = ContextCompat.getColorStateList(context, R.color.white)
        val unselectedBg = ContextCompat.getColorStateList(context, android.R.color.transparent)
        val selectedText = ContextCompat.getColor(context, R.color.recent_primary)
        val unselectedText = ContextCompat.getColor(context, R.color.recent_text_secondary)

        recentTabButton.backgroundTintList = if (activeTab == Tab.RECENT) selectedBg else unselectedBg
        favoritesTabButton.backgroundTintList = if (activeTab == Tab.FAVORITES) selectedBg else unselectedBg
        recentTabButton.setTextColor(if (activeTab == Tab.RECENT) selectedText else unselectedText)
        favoritesTabButton.setTextColor(if (activeTab == Tab.FAVORITES) selectedText else unselectedText)

        quickAccessContainer.isVisible = activeTab == Tab.RECENT
        submitCurrentList(searchEditText.text?.toString().orEmpty())
    }

    private fun submitCurrentList(query: String) {
        adapter.submitItems(
            if (activeTab == Tab.RECENT) buildRecentList(query) else buildFavoriteList(query)
        )
    }

    private fun renderQuickAccess() {
        val context = requireContext()
        quickAccessRow.removeAllViews()
        recentItems().take(4).forEach { item ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(android.R.layout.simple_list_item_1, quickAccessRow, false) as TextView
            chip.text = item.name.substringBefore(".")
            chip.setTextColor(ContextCompat.getColor(context, R.color.recent_text_secondary))
            chip.textSize = 11f
            chip.backgroundTintList = ContextCompat.getColorStateList(context, R.color.recent_surface)
            chip.setPadding(20, 28, 20, 28)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 12
            chip.layoutParams = lp
            chip.background = ContextCompat.getDrawable(context, R.drawable.explorer_tag_background)
            quickAccessRow.addView(chip)
        }
    }

    private fun buildRecentList(query: String): List<RecentListItem> {
        val filtered = recentItems().filter { matches(it, query) }
        return buildList {
            addSection(this, getString(R.string.recent_today), R.drawable.clock, filtered, RecentItem.Section.TODAY)
            addSection(this, getString(R.string.recent_yesterday), R.drawable.clock, filtered, RecentItem.Section.YESTERDAY)
            addSection(this, getString(R.string.recent_this_week), R.drawable.clock, filtered, RecentItem.Section.WEEK)
            addSection(this, getString(R.string.recent_earlier), R.drawable.clock, filtered, RecentItem.Section.EARLIER)
        }
    }

    private fun buildFavoriteList(query: String): List<RecentListItem> {
        val filtered = favoriteItems().filter { matches(it, query) }
        return buildList {
            addSection(this, getString(R.string.recent_favorites_section), R.drawable.star, filtered, RecentItem.Section.FAV_RECENT)
            addSection(this, getString(R.string.recent_ai_section), R.drawable.brain, filtered, RecentItem.Section.FAV_AI)
            addSection(this, getString(R.string.recent_frequent_section), R.drawable.bookmark_check, filtered, RecentItem.Section.FAV_FREQUENT)
        }
    }

    private fun addSection(
        target: MutableList<RecentListItem>,
        title: String,
        iconRes: Int,
        items: List<RecentItem>,
        section: RecentItem.Section
    ) {
        val sectionItems = items.filter { it.section == section }
        if (sectionItems.isEmpty()) return
        target += RecentListItem.Header(title, sectionItems.size, iconRes)
        target += sectionItems.map { RecentListItem.Entry(it) }
    }

    private fun matches(item: RecentItem, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return item.name.lowercase().contains(q) ||
            item.aiTags.any { it.lowercase().contains(q) } ||
            (item.ocrSnippet?.lowercase()?.contains(q) == true)
    }

    private fun recentItems(): List<RecentItem> = listOf(
        RecentItem("r1", "Design_System_v3.fig", RecentItem.Kind.FILE, RecentItem.Section.TODAY, "10:34 AM", RecentItem.FileType.DOCUMENT, "8.2 MB"),
        RecentItem("r2", "Sprint_Planning.pdf", RecentItem.Kind.FILE, RecentItem.Section.TODAY, "9:12 AM", RecentItem.FileType.PDF, "310 KB", aiTags = listOf("Planning", "Sprint"), aiAnalyzed = true),
        RecentItem("r3", "Product Screenshots", RecentItem.Kind.FOLDER, RecentItem.Section.TODAY, "8:05 AM", itemCount = 38),
        RecentItem("r4", "Contract_NDA_2026.pdf", RecentItem.Kind.FILE, RecentItem.Section.YESTERDAY, "Yesterday, 4:50 PM", RecentItem.FileType.PDF, "540 KB", aiTags = listOf("Contract", "Legal"), aiAnalyzed = true, ocrSnippet = "Non-Disclosure Agreement — Effective May 20, 2026", isFavorite = true),
        RecentItem("r5", "Team_Photo.jpg", RecentItem.Kind.FILE, RecentItem.Section.YESTERDAY, "Yesterday, 2:30 PM", RecentItem.FileType.IMAGE, "3.7 MB"),
        RecentItem("r6", "Archive 2025", RecentItem.Kind.FOLDER, RecentItem.Section.YESTERDAY, "Yesterday, 11:15 AM", itemCount = 142),
        RecentItem("r7", "Budget_Q2.xlsx", RecentItem.Kind.FILE, RecentItem.Section.WEEK, "Mon, 8:20 AM", RecentItem.FileType.DOCUMENT, "210 KB", isFavorite = true),
        RecentItem("r8", "Brand_Guidelines.pdf", RecentItem.Kind.FILE, RecentItem.Section.WEEK, "Sun, 3:00 PM", RecentItem.FileType.PDF, "4.5 MB", aiTags = listOf("Brand", "Design"), aiAnalyzed = true),
        RecentItem("r9", "Podcast_Ep12.mp3", RecentItem.Kind.FILE, RecentItem.Section.EARLIER, "May 15", RecentItem.FileType.AUDIO, "48 MB"),
        RecentItem("r10", "Source Code Backup", RecentItem.Kind.FOLDER, RecentItem.Section.EARLIER, "May 12", itemCount = 503)
    )

    private fun favoriteItems(): List<RecentItem> = listOf(
        RecentItem("f1", "Work Documents", RecentItem.Kind.FOLDER, RecentItem.Section.FAV_RECENT, "2 days ago", itemCount = 24, isFavorite = true),
        RecentItem("f2", "Invoice_March_2026.pdf", RecentItem.Kind.FILE, RecentItem.Section.FAV_RECENT, "3 days ago", RecentItem.FileType.PDF, "245 KB", aiTags = listOf("Invoice", "Receipt"), aiAnalyzed = true, ocrSnippet = "Total Amount Due: $1,240.00 — March 15, 2026", isFavorite = true),
        RecentItem("f3", "Meeting_Notes_Q1.pdf", RecentItem.Kind.FILE, RecentItem.Section.FAV_AI, "1 week ago", RecentItem.FileType.PDF, "128 KB", aiTags = listOf("Meeting", "Notes"), aiAnalyzed = true, ocrSnippet = "Q1 Roadmap Review — finalize API specs by Apr 3", isFavorite = true),
        RecentItem("f4", "Personal Projects", RecentItem.Kind.FOLDER, RecentItem.Section.FAV_AI, "2 weeks ago", itemCount = 15, isFavorite = true),
        RecentItem("f5", "Presentation_2026.pptx", RecentItem.Kind.FILE, RecentItem.Section.FAV_FREQUENT, "2 weeks ago", RecentItem.FileType.DOCUMENT, "3.4 MB", isFavorite = true),
        RecentItem("f6", "Conference_Keynote.mp4", RecentItem.Kind.FILE, RecentItem.Section.FAV_FREQUENT, "1 month ago", RecentItem.FileType.VIDEO, "45 MB", isFavorite = true)
    )

    private enum class Tab { RECENT, FAVORITES }
}
