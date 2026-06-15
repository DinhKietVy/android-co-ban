package com.example.filemanagementapp.profile.theme

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.local.profile.SettingsPreferencesRepository
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThemeActivity : AppCompatActivity() {

    private lateinit var settingsRepository: SettingsPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)

        settingsRepository = SettingsPreferencesRepository(this)

        findViewById<MaterialToolbar>(R.id.toolbarTheme).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupTheme)
        val radioSystem = findViewById<RadioButton>(R.id.radioSystem)
        val radioLight = findViewById<RadioButton>(R.id.radioLight)
        val radioDark = findViewById<RadioButton>(R.id.radioDark)

        lifecycleScope.launch {
            val currentMode = settingsRepository.themeModeFlow.first()
            when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> radioLight.isChecked = true
                AppCompatDelegate.MODE_NIGHT_YES -> radioDark.isChecked = true
                else -> radioSystem.isChecked = true
            }
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            lifecycleScope.launch {
                settingsRepository.updateThemeMode(mode)
            }
        }
    }
}
