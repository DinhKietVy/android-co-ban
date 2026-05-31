package com.example.filemanagementapp.password.ui

sealed interface ForgotPasswordEvent {
    data class ShowMessage(val message: String) : ForgotPasswordEvent
}
