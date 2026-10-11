package com.example.filemanagementapp.main

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.filemanagementapp.explorer.ExplorerFragment
import com.example.filemanagementapp.profile.ProfileFragment
import com.example.filemanagementapp.recent.RecentFragment
import com.example.filemanagementapp.trash.TrashFragment

class MainPagerAdapter(
    activity: AppCompatActivity,
    private val username: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = PAGE_COUNT

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ExplorerFragment.newInstance(username)
            1 -> RecentFragment.newInstance(username)
            2 -> TrashFragment.newInstance(username)
            3 -> ProfileFragment()
            else -> throw IndexOutOfBoundsException("Invalid page index: $position")
        }
    }

    private companion object {
        private const val PAGE_COUNT = 4
    }
}
