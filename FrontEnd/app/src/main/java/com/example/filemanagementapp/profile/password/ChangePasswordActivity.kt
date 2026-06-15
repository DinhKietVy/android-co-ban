package com.example.filemanagementapp.profile.password

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.auth.local.LoginPreferencesRepository
import com.example.filemanagementapp.data.auth.network.AuthNetworkModule
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.example.filemanagementapp.login.LoginActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ChangePasswordActivity : AppCompatActivity() {

    private val viewModel: ChangePasswordViewModel by viewModels()

    private lateinit var toolbarChangePassword: MaterialToolbar
    private lateinit var etOldPassword: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnChangePassword: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        bindViews()
        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    private fun bindViews() {
        toolbarChangePassword = findViewById(R.id.toolbarChangePassword)
        etOldPassword = findViewById(R.id.etOldPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        toolbarChangePassword.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        btnChangePassword.setOnClickListener {
            val oldPassword = etOldPassword.text.toString()
            val newPassword = etNewPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()
            
            if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, getString(R.string.change_password_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPassword != confirmPassword) {
                Toast.makeText(this, getString(R.string.change_password_mismatch), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPassword.length < 6) {
                Toast.makeText(this, getString(R.string.change_password_too_short), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewModel.changePassword(oldPassword, newPassword)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    btnChangePassword.isEnabled = !state.isLoading
                    progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    
                    state.successMessage?.let {
                        Toast.makeText(this@ChangePasswordActivity, getString(R.string.change_password_success), Toast.LENGTH_LONG).show()
                        viewModel.clearMessages()
                        
                        // Handle auto logout
                        logoutAndRedirect()
                    }
                    
                    state.errorMessage?.let {
                        Toast.makeText(this@ChangePasswordActivity, it.asString(this@ChangePasswordActivity), Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun logoutAndRedirect() {
        lifecycleScope.launch {
            LoginPreferencesRepository(applicationContext).clearRememberedLogin()
            AuthRepository(
                authApiService = AuthNetworkModule.authApiService,
                gson = AuthNetworkModule.gson
            ).clearLocalSession()

            startActivity(
                Intent(this@ChangePasswordActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }
}
