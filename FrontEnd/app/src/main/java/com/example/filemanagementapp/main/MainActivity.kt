package com.example.filemanagementapp.main

import android.os.Bundle
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
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

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
        setupPagerAndNavigation()
        observeNavigationRequests()
    }

    private fun observeNavigationRequests() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigationViewModel.openFolderRequests.collect {
                    if (viewPager.currentItem != EXPLORER_PAGE_INDEX) {
                        viewPager.setCurrentItem(EXPLORER_PAGE_INDEX, false)
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

        // Setup header
        val headerView = navigationView.getHeaderView(0)
        val headerUsername = headerView.findViewById<TextView>(R.id.navHeaderUsername)
        headerUsername.text = username

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_explorer -> viewPager.setCurrentItem(0, false)
                R.id.nav_shared -> viewPager.setCurrentItem(1, false)
                R.id.nav_public_links -> viewPager.setCurrentItem(2, false)
                R.id.nav_recent -> viewPager.setCurrentItem(3, false)
                R.id.nav_trash -> viewPager.setCurrentItem(4, false)
                R.id.nav_profile -> viewPager.setCurrentItem(5, false)
                R.id.nav_logout -> {
                    // Handle logout
                    finish()
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
