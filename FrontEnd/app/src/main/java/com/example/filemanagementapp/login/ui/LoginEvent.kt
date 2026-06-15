package com.example.filemanagementapp.login.ui

import com.example.filemanagementapp.data.auth.model.User
import com.example.filemanagementapp.util.UiText

sealed interface LoginEvent {
    data class NavigateToMain(val user: User) : LoginEvent
    data class ShowMessage(val message: UiText) : LoginEvent
}
