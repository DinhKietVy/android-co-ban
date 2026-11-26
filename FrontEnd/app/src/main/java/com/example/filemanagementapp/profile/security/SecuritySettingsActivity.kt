package com.example.filemanagementapp.profile.security

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.local.profile.SettingsPreferencesRepository
import com.example.filemanagementapp.security.AppSecurityManager
import com.example.filemanagementapp.security.BiometricAuthHelper
import com.example.filemanagementapp.security.DeviceSecurityChecker
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

class SecuritySettingsActivity : AppCompatActivity() {

    private lateinit var settingsRepository: SettingsPreferencesRepository
    private lateinit var switchBiometricLock: MaterialSwitch
    private lateinit var switchScreenSecurity: MaterialSwitch
    private lateinit var switchE2eeUpload: MaterialSwitch
    private lateinit var tvIntegrityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_security_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settingsRepository = SettingsPreferencesRepository(applicationContext)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        switchBiometricLock = findViewById(R.id.switchBiometricLock)
        switchScreenSecurity = findViewById(R.id.switchScreenSecurity)
        switchE2eeUpload = findViewById(R.id.switchE2eeUpload)
        tvIntegrityStatus = findViewById(R.id.tvDeviceIntegrityStatus)

        renderDeviceIntegrity()
        observeSettings()
    }

    private fun renderDeviceIntegrity() {
        val status = DeviceSecurityChecker.checkEnvironment(this)
        when {
            status.isRooted -> {
                tvIntegrityStatus.text = getString(R.string.security_integrity_rooted)
                tvIntegrityStatus.setTextColor(ContextCompat.getColor(this, R.color.profile_danger))
            }
            status.isEmulator -> {
                tvIntegrityStatus.text = getString(R.string.security_integrity_emulator)
                tvIntegrityStatus.setTextColor(ContextCompat.getColor(this, R.color.profile_warning))
            }
            else -> {
                tvIntegrityStatus.text = getString(R.string.security_integrity_safe)
                tvIntegrityStatus.setTextColor(ContextCompat.getColor(this, R.color.profile_primary))
            }
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.securitySettingsFlow.collect { settings ->
                    switchBiometricLock.setOnCheckedChangeListener(null)
                    switchBiometricLock.isChecked = settings.biometricLockEnabled
                    switchBiometricLock.setOnCheckedChangeListener { _, isChecked ->
                        handleBiometricToggle(isChecked)
                    }

                    switchScreenSecurity.setOnCheckedChangeListener(null)
                    switchScreenSecurity.isChecked = settings.screenSecurityEnabled
                    switchScreenSecurity.setOnCheckedChangeListener { _, isChecked ->
                        lifecycleScope.launch {
                            settingsRepository.updateScreenSecurity(isChecked)
                        }
                    }

                    switchE2eeUpload.setOnCheckedChangeListener(null)
                    switchE2eeUpload.isChecked = settings.e2eeUploadEnabled
                    switchE2eeUpload.setOnCheckedChangeListener { _, isChecked ->
                        lifecycleScope.launch {
                            settingsRepository.updateE2eeUpload(isChecked)
                        }
                    }
                }
            }
        }
    }

    private fun handleBiometricToggle(enable: Boolean) {
        if (!enable) {
            lifecycleScope.launch {
                settingsRepository.updateBiometricLock(false)
            }
            return
        }

        if (!BiometricAuthHelper.canAuthenticate(this)) {
            switchBiometricLock.isChecked = false
            Toast.makeText(this, R.string.security_biometric_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        BiometricAuthHelper.authenticate(
            activity = this,
            onSuccess = {
                AppSecurityManager.markUnlocked()
                lifecycleScope.launch {
                    settingsRepository.updateBiometricLock(true)
                }
            },
            onError = { message ->
                switchBiometricLock.setOnCheckedChangeListener(null)
                switchBiometricLock.isChecked = false
                switchBiometricLock.setOnCheckedChangeListener { _, isChecked ->
                    handleBiometricToggle(isChecked)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
    }
}
