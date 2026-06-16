package com.example.filemanagementapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.data.auth.network.SessionCookieJar
import com.example.filemanagementapp.data.local.profile.SettingsPreferencesRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class FileManagementApp : Application(), ImageLoaderFactory {
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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                ExplorerNetworkModule.okHttpClient
            }
            .crossfade(true)
            .build()
    }
}
