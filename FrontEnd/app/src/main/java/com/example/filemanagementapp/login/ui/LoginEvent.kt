package com.example.filemanagementapp.login.ui

import com.example.filemanagementapp.data.auth.model.LoginUser
import com.example.filemanagementapp.util.UiText

sealed interface LoginEvent {
    data class NavigateToMain(val user: LoginUser) : LoginEvent
    data class ShowMessage(val message: UiText) : LoginEvent
}
