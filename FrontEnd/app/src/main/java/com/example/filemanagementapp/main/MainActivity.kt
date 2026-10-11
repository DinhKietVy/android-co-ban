package com.example.filemanagementapp.main

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.example.filemanagementapp.R
import com.example.filemanagementapp.login.LoginActivity
import com.example.filemanagementapp.data.auth.local.LoginPreferencesRepository
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import coil.load
import androidx.core.content.ContextCompat
import android.content.Intent
import com.example.filemanagementapp.widget.QuickHubWidgetProvider

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var username: String
    private val navigationViewModel: MainNavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        drawerLayout = findViewById(R.id.drawerLayout)
        
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        username = intent.getStringExtra(LoginActivity.EXTRA_USERNAME).orEmpty()
        if (username.isEmpty()) {
            val prefs = getSharedPreferences(QuickHubWidgetProvider.PREFS_NAME, MODE_PRIVATE)
            username = prefs.getString(QuickHubWidgetProvider.KEY_USERNAME, "").orEmpty()
        }

        setupPagerAndNavigation()
        observeNavigationRequests()
        handleWidgetNavigation(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetNavigation(intent)
    }

    private fun handleWidgetNavigation(intent: Intent?) {
        if (intent == null) return
        val navigateAi = intent.getBooleanExtra(QuickHubWidgetProvider.EXTRA_NAVIGATE_AI, false)
        val navigateDrive = intent.getBooleanExtra(QuickHubWidgetProvider.EXTRA_NAVIGATE_DRIVE, false)
        val navigateUpload = intent.getBooleanExtra(QuickHubWidgetProvider.EXTRA_NAVIGATE_UPLOAD, false)

        if (navigateAi) {
            intent.removeExtra(QuickHubWidgetProvider.EXTRA_NAVIGATE_AI)
            com.example.filemanagementapp.chat.AiChatBottomSheetFragment.newInstance(username)
                .show(supportFragmentManager, com.example.filemanagementapp.chat.AiChatBottomSheetFragment.TAG)
        } else if (navigateDrive) {
            intent.removeExtra(QuickHubWidgetProvider.EXTRA_NAVIGATE_DRIVE)
            viewPager.setCurrentItem(EXPLORER_PAGE_INDEX, false)
            navigationViewModel.requestOpenDrive()
        } else if (navigateUpload) {
            intent.removeExtra(QuickHubWidgetProvider.EXTRA_NAVIGATE_UPLOAD)
            viewPager.setCurrentItem(EXPLORER_PAGE_INDEX, false)
            navigationViewModel.requestTriggerCreateFab()
        }
    }

    private fun observeNavigationRequests() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigationViewModel.openFolderRequests.collect { folderPath ->
                    if (!folderPath.isNullOrBlank()) {
                        if (viewPager.currentItem != EXPLORER_PAGE_INDEX) {
                            viewPager.setCurrentItem(EXPLORER_PAGE_INDEX, false)
                        }
                    }
                }
            }
        }
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun setupPagerAndNavigation() {
        viewPager = findViewById(R.id.mainViewPager)
        navigationView = findViewById(R.id.navigationView)

        viewPager.isUserInputEnabled = false // Disable swiping to avoid conflict with drawer
        viewPager.adapter = MainPagerAdapter(this, username)
        viewPager.offscreenPageLimit = 6

        val headerView = navigationView.getHeaderView(0)
        val headerUsername = headerView.findViewById<TextView>(R.id.navHeaderUsername)
        val headerAvatar = headerView.findViewById<ImageView>(R.id.navHeaderAvatar)
        
        headerUsername.text = username

        lifecycleScope.launch {
            val prefs = LoginPreferencesRepository(applicationContext).preferencesFlow.first()
            if (!prefs.avatarUrl.isNullOrEmpty()) {
                headerAvatar.imageTintList = null
                headerAvatar.setPadding(0, 0, 0, 0)
                headerAvatar.load(prefs.avatarUrl) {
                    transformations(coil.transform.CircleCropTransformation())
                    crossfade(true)
                    placeholder(R.drawable.user)
                    error(R.drawable.user)
                }
            } else {
                headerAvatar.setImageResource(R.drawable.user)
                val padding = (12 * resources.displayMetrics.density).toInt()
                headerAvatar.setPadding(padding, padding, padding, padding)
                headerAvatar.imageTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.profile_primary)
            }
        }

        findViewById<View>(R.id.aiChatFab).setOnClickListener {
            com.example.filemanagementapp.chat.AiChatBottomSheetFragment.newInstance(username)
                .show(supportFragmentManager, com.example.filemanagementapp.chat.AiChatBottomSheetFragment.TAG)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            // Clear checked state of all items across groups
            val allItems = listOf(R.id.nav_explorer, R.id.nav_shared, R.id.nav_ai_chat, R.id.nav_recent, R.id.nav_trash, R.id.nav_profile, R.id.nav_logout)
            allItems.forEach { navigationView.menu.findItem(it)?.isChecked = false }
            item.isChecked = true

            when (item.itemId) {
                R.id.nav_explorer -> viewPager.setCurrentItem(EXPLORER_PAGE_INDEX, false)
                R.id.nav_shared -> {
                    viewPager.setCurrentItem(EXPLORER_PAGE_INDEX, false)
                    navigationViewModel.requestOpenFolder("shared://root")
                }
                R.id.nav_ai_chat -> {
                    com.example.filemanagementapp.chat.AiChatBottomSheetFragment.newInstance(username)
                        .show(supportFragmentManager, com.example.filemanagementapp.chat.AiChatBottomSheetFragment.TAG)
                }
                R.id.nav_recent -> viewPager.setCurrentItem(1, false)
                R.id.nav_trash -> viewPager.setCurrentItem(2, false)
                R.id.nav_profile -> viewPager.setCurrentItem(3, false)
                R.id.nav_logout -> {
                    lifecycleScope.launch {
                        com.example.filemanagementapp.data.auth.local.LoginPreferencesRepository(applicationContext).clearRememberedLogin()
                        com.example.filemanagementapp.data.auth.repository.AuthRepository(
                            authApiService = com.example.filemanagementapp.data.auth.network.AuthNetworkModule.authApiService,
                            gson = com.example.filemanagementapp.data.auth.network.AuthNetworkModule.gson
                        ).clearLocalSession()
                        
                        startActivity(
                            android.content.Intent(this@MainActivity, com.example.filemanagementapp.login.LoginActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        finish()
                    }
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        navigationView.setCheckedItem(R.id.nav_explorer)
    }

    private companion object {
        private const val EXPLORER_PAGE_INDEX = 0
    }
}
