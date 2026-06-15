package com.example.filemanagementapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.filemanagementapp.data.auth.network.SessionCookieJar
import com.example.filemanagementapp.data.local.profile.SettingsPreferencesRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class FileManagementApp : Application() {
    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        SessionCookieJar.initialize(this)
        
        val settingsRepository = SettingsPreferencesRepository(this)
        applicationScope.launch {
            settingsRepository.themeModeFlow.collect { mode ->
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }
}
