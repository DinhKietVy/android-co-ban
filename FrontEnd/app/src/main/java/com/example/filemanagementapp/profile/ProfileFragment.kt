package com.example.filemanagementapp.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.filemanagementapp.R
class ProfileFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindRow(view, R.id.rowProfileEdit, R.drawable.user, R.string.profile_edit_profile)
        bindRow(view, R.id.rowProfilePassword, R.drawable.lock, R.string.profile_change_password)
        bindRow(view, R.id.rowProfileDevices, R.drawable.smartphone, R.string.profile_manage_devices)
        bindRow(view, R.id.rowProfileSecurity, R.drawable.shield_check, R.string.profile_security)
        bindRow(view, R.id.rowProfileNotifications, R.drawable.bell, R.string.profile_notifications, noBorder = true)

        bindRow(view, R.id.rowProfileBackend, R.drawable.server, R.string.profile_backend_url)
        bindRow(view, R.id.rowProfileAiServer, R.drawable.cloud, R.string.profile_ai_server_url)
        bindRow(view, R.id.rowProfileCache, R.drawable.zap, R.string.profile_cache_sync)
        bindRow(view, R.id.rowProfileUpload, R.drawable.cloud, R.string.profile_auto_upload)
        bindRow(view, R.id.rowProfileTheme, R.drawable.palette, R.string.profile_theme)
        bindRow(view, R.id.rowProfileLanguage, R.drawable.languages, R.string.profile_language, noBorder = true)

        bindToggleRow(view, R.id.rowProfileAiOcr, R.drawable.scan, R.string.profile_auto_ocr, checked = true)
        bindToggleRow(view, R.id.rowProfileAiObject, R.drawable.sparkles, R.string.profile_auto_object, checked = true)
        bindToggleRow(view, R.id.rowProfileAiMetadata, R.drawable.tag, R.string.profile_ai_metadata, checked = false, noBorder = true)

        bindRow(view, R.id.rowProfileHelp, R.drawable.help_circle, R.string.profile_help)
        bindRow(view, R.id.rowProfileAbout, R.drawable.info, R.string.profile_about)
        bindRow(view, R.id.rowProfileTerms, R.drawable.file_text, R.string.profile_terms, noBorder = true)

        bindRow(view, R.id.rowProfileLogout, R.drawable.log_out, R.string.profile_logout, iconColor = R.color.profile_warning, textColor = R.color.profile_warning)
        bindRow(view, R.id.rowProfileDelete, R.drawable.info, R.string.profile_delete_account, iconColor = R.color.profile_danger, textColor = R.color.profile_danger, noBorder = true)
    }

    private fun bindRow(
        root: View,
        rowId: Int,
        iconRes: Int,
        textRes: Int,
        iconColor: Int = R.color.profile_text_secondary,
        textColor: Int = R.color.profile_text_primary,
        noBorder: Boolean = false
    ) {
        val row = root.findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.rowIcon).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(requireContext(), iconColor)
        }
        row.findViewById<TextView>(R.id.rowLabel).apply {
            setText(textRes)
            setTextColor(ContextCompat.getColor(requireContext(), textColor))
        }
        if (noBorder) {
            row.background = ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
        }
    }

    private fun bindToggleRow(
        root: View,
        rowId: Int,
        iconRes: Int,
        textRes: Int,
        checked: Boolean,
        noBorder: Boolean = false
    ) {
        val row = root.findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.toggleIcon).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.profile_text_secondary)
        }
        row.findViewById<TextView>(R.id.toggleLabel).setText(textRes)
        row.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.settingSwitch).isChecked = checked
        if (noBorder) {
            row.background = ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
        }
    }
}
