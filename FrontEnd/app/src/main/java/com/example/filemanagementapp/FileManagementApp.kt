package com.example.filemanagementapp

import android.app.Application
import com.example.filemanagementapp.data.auth.network.SessionCookieJar

class FileManagementApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionCookieJar.initialize(this)
    }
}
