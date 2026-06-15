package com.example.filemanagementapp.profile.edit

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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {

    private val viewModel: EditProfileViewModel by viewModels()

    private lateinit var toolbarEditProfile: MaterialToolbar
    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        bindViews()
        setupToolbar()
        populateInitialData()
        setupListeners()
        observeViewModel()
    }

    private fun bindViews() {
        toolbarEditProfile = findViewById(R.id.toolbarEditProfile)
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etUsername = findViewById(R.id.etUsername)
        btnSave = findViewById(R.id.btnSave)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        toolbarEditProfile.setNavigationOnClickListener {
            finish()
        }
    }

    private fun populateInitialData() {
        intent.extras?.let {
            etFullName.setText(it.getString("EXTRA_FULL_NAME", ""))
            etEmail.setText(it.getString("EXTRA_EMAIL", ""))
            etUsername.setText(it.getString("EXTRA_USERNAME", ""))
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            
            if (fullName.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, getString(R.string.edit_profile_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewModel.updateProfile(fullName, email)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    btnSave.isEnabled = !state.isLoading
                    progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    
                    state.successMessage?.let {
                        Toast.makeText(this@EditProfileActivity, it.asString(this@EditProfileActivity), Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                        setResult(RESULT_OK)
                        finish()
                    }
                    
                    state.errorMessage?.let {
                        Toast.makeText(this@EditProfileActivity, it.asString(this@EditProfileActivity), Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                }
            }
        }
    }
}
