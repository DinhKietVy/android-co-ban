package com.example.filemanagementapp.register.ui

import com.example.filemanagementapp.data.auth.model.LoginUser

import com.example.filemanagementapp.util.UiText

sealed interface RegisterEvent {
    data class ShowMessage(val message: UiText) : RegisterEvent
    data class NavigateToLogin(val username: String) : RegisterEvent
    data class NavigateToMain(val user: LoginUser) : RegisterEvent
}
