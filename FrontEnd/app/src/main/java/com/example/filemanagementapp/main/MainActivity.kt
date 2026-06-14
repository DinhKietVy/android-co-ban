package com.example.filemanagementapp.main

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.example.filemanagementapp.R
import com.example.filemanagementapp.login.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var username: String
    private val navigationViewModel: MainNavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
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
                        viewPager.setCurrentItem(EXPLORER_PAGE_INDEX, true)
                    }
                }
            }
        }
    }

    private fun setupPagerAndNavigation() {
        val navItems = listOf(
            R.id.navigation_explorer,
            R.id.navigation_recent,
            R.id.navigation_trash,
            R.id.navigation_profile
        )

        viewPager = findViewById(R.id.mainViewPager)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        viewPager.adapter = MainPagerAdapter(this, username)
        viewPager.offscreenPageLimit = navItems.size

        bottomNavigation.setOnItemSelectedListener { item ->
            val pageIndex = navItems.indexOf(item.itemId)
            if (pageIndex >= 0) {
                if (viewPager.currentItem != pageIndex) {
                    viewPager.setCurrentItem(pageIndex, true)
                }
                true
            } else {
                false
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (bottomNavigation.selectedItemId != navItems[position]) {
                    bottomNavigation.selectedItemId = navItems[position]
                }
            }
        })

        bottomNavigation.selectedItemId = navItems.first()
    }

    private companion object {
        private const val EXPLORER_PAGE_INDEX = 0
    }
}
