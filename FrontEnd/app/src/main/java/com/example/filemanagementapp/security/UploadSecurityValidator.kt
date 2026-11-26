package com.example.filemanagementapp.security

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object UploadSecurityValidator {
    private val BLOCKED_EXECUTABLE_EXTENSIONS = setOf(
        "exe", "bat", "cmd", "sh", "php", "jsp", "asp", "aspx", "msi", "vbs", "ps1"
    )

    fun isBlockedFileName(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) return false
        val ext = fileName.substringAfterLast('.', "").trim().lowercase()
        return ext.isNotEmpty() && ext in BLOCKED_EXECUTABLE_EXTENSIONS
    }

    fun resolveDisplayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
