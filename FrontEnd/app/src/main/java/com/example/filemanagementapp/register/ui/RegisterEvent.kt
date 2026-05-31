package com.example.filemanagementapp.register.ui

import com.example.filemanagementapp.data.auth.model.LoginUser

sealed interface RegisterEvent {
    data class ShowMessage(val message: String) : RegisterEvent
    data class NavigateToLogin(val username: String) : RegisterEvent
    data class NavigateToMain(val user: LoginUser) : RegisterEvent
}
