package com.example.filemanagementapp.password.ui

import com.example.filemanagementapp.util.UiText

sealed interface ForgotPasswordEvent {
    data class ShowMessage(val message: UiText) : ForgotPasswordEvent
}
