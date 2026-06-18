package com.example.filemanagementapp.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.auth.local.LoginPreferencesRepository
import com.example.filemanagementapp.data.auth.network.AuthNetworkModule
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.example.filemanagementapp.login.LoginActivity
import com.example.filemanagementapp.profile.edit.EditProfileActivity
import com.example.filemanagementapp.profile.language.LanguageActivity
import com.example.filemanagementapp.profile.password.ChangePasswordActivity
import com.example.filemanagementapp.profile.theme.ThemeActivity
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    
    private val viewModel: ProfileViewModel by viewModels()
    private var currentProfile: UserProfile? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupStaticRows(view)
        setupClickListeners(view)
        setupLogout(view)
        observeViewModel(view)
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.rowProfileEdit).setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            currentProfile?.let {
                intent.putExtra("EXTRA_FULL_NAME", it.fullName)
                intent.putExtra("EXTRA_EMAIL", it.email)
                intent.putExtra("EXTRA_USERNAME", it.username)
            }
            startActivity(intent)
        }

        view.findViewById<View>(R.id.rowProfilePassword).setOnClickListener {
            startActivity(Intent(requireContext(), ChangePasswordActivity::class.java))
        }

        view.findViewById<View>(R.id.rowProfileTheme).setOnClickListener {
            startActivity(Intent(requireContext(), ThemeActivity::class.java))
        }

        view.findViewById<View>(R.id.rowProfileLanguage).setOnClickListener {
            startActivity(Intent(requireContext(), LanguageActivity::class.java))
        }

        view.findViewById<View>(R.id.rowProfileDelete).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.profile_delete_title))
                .setMessage(getString(R.string.profile_delete_message))
                .setPositiveButton(getString(R.string.profile_delete_positive)) { _, _ ->
                    viewModel.deleteAccount()
                }
                .setNegativeButton(getString(R.string.profile_delete_negative), null)
                .show()
        }
    }
    
    private fun observeViewModel(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.profile != null) {
                        currentProfile = state.profile
                        view.findViewById<TextView>(R.id.tvProfileName).text = state.profile.fullName
                        view.findViewById<TextView>(R.id.tvProfileEmail).text = state.profile.email
                        view.findViewById<TextView>(R.id.tvProfileUsername).text = state.profile.username
                        view.findViewById<TextView>(R.id.tvProfilePro).visibility = if (state.profile.isPro) View.VISIBLE else View.GONE
                    }
                    
                    if (state.storage != null) {
                        view.findViewById<TextView>(R.id.tvStorageTotal).text = state.storage.usedFormatted
                        
                        view.findViewById<View>(R.id.storageItemImages).findViewById<TextView>(R.id.tvStorageSize).text = state.storage.imagesFormatted
                        view.findViewById<View>(R.id.storageItemDocuments).findViewById<TextView>(R.id.tvStorageSize).text = state.storage.documentsFormatted
                        view.findViewById<View>(R.id.storageItemVideos).findViewById<TextView>(R.id.tvStorageSize).text = state.storage.videosFormatted
                        view.findViewById<View>(R.id.storageItemOther).findViewById<TextView>(R.id.tvStorageSize).text = state.storage.otherFormatted
                    }

                    val aiSettings = state.aiSettings
                    view.findViewById<View>(R.id.rowProfileAiOcr).findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.settingSwitch).apply {
                        setOnCheckedChangeListener(null)
                        isChecked = aiSettings.autoOcrEnabled
                        setOnCheckedChangeListener { _, isChecked -> viewModel.updateAutoOcr(isChecked) }
                    }
                    view.findViewById<View>(R.id.rowProfileAiObject).findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.settingSwitch).apply {
                        setOnCheckedChangeListener(null)
                        isChecked = aiSettings.autoObjectEnabled
                        setOnCheckedChangeListener { _, isChecked -> viewModel.updateAutoObject(isChecked) }
                    }
                    view.findViewById<View>(R.id.rowProfileAiMetadata).findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.settingSwitch).apply {
                        setOnCheckedChangeListener(null)
                        isChecked = aiSettings.aiMetadataEnabled
                        setOnCheckedChangeListener { _, isChecked -> viewModel.updateAiMetadata(isChecked) }
                    }

                    if (state.isAccountDeleted) {
                        Toast.makeText(requireContext(), getString(R.string.profile_account_deleted), Toast.LENGTH_SHORT).show()
                        performLogout()
                    }

                    if (state.errorMessage != null) {
                        Toast.makeText(requireContext(), state.errorMessage.asString(requireContext()), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupStaticRows(view: View) {
        bindRow(view, R.id.rowProfileEdit, R.drawable.user, R.string.profile_edit_profile)
        bindRow(view, R.id.rowProfilePassword, R.drawable.lock, R.string.profile_change_password)
        bindRow(view, R.id.rowProfileDevices, R.drawable.smartphone, R.string.profile_manage_devices)
        bindRow(view, R.id.rowProfileSecurity, R.drawable.shield_check, R.string.profile_security)
        bindRow(view, R.id.rowProfileNotifications, R.drawable.bell, R.string.profile_notifications, noBorder = true)

        bindRow(view, R.id.rowProfileTheme, R.drawable.moon, R.string.profile_theme)
        bindRow(view, R.id.rowProfileLanguage, R.drawable.languages, R.string.profile_language, noBorder = true)

        bindToggleRow(view, R.id.rowProfileAiOcr, R.drawable.scan, R.string.profile_auto_ocr)
        bindToggleRow(view, R.id.rowProfileAiObject, R.drawable.sparkles, R.string.profile_auto_object)
        bindToggleRow(view, R.id.rowProfileAiMetadata, R.drawable.tag, R.string.profile_ai_metadata, noBorder = true)

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
        noBorder: Boolean = false
    ) {
        val row = root.findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.toggleIcon).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.profile_text_secondary)
        }
        row.findViewById<TextView>(R.id.toggleLabel).setText(textRes)
        if (noBorder) {
            row.background = ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
        }
    }

    private fun setupLogout(root: View) {
        root.findViewById<View>(R.id.rowProfileLogout).setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            LoginPreferencesRepository(requireContext().applicationContext).clearRememberedLogin()
            AuthRepository(
                authApiService = AuthNetworkModule.authApiService,
                gson = AuthNetworkModule.gson
            ).clearLocalSession()

            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish()
        }
    }
}
