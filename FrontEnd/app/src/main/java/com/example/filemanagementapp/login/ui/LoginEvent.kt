package com.example.filemanagementapp.login.ui

import com.example.filemanagementapp.login.data.model.LoginUser

sealed interface LoginEvent {
    data class ShowMessage(val message: String) : LoginEvent
    data class NavigateToMain(val user: LoginUser) : LoginEvent
}
