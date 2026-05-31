package com.example.filemanagementapp.register

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.filemanagementapp.R
import com.example.filemanagementapp.login.LoginActivity
import com.example.filemanagementapp.data.auth.network.AuthNetworkModule
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.example.filemanagementapp.login.google.GoogleAuthUiClient
import com.example.filemanagementapp.main.MainActivity
import com.example.filemanagementapp.register.ui.RegisterEvent
import com.example.filemanagementapp.register.ui.RegisterUiState
import com.example.filemanagementapp.register.ui.RegisterViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    private lateinit var fullNameInputLayout: TextInputLayout
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var usernameInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var confirmPasswordInputLayout: TextInputLayout
    private lateinit var fullNameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var confirmPasswordEditText: TextInputEditText
    private lateinit var termsCheckbox: CheckBox
    private lateinit var createAccountButton: MaterialButton
    private lateinit var googleRegisterButton: MaterialButton
    private lateinit var signInText: TextView
    private lateinit var termsActionText: TextView

    private val viewModel: RegisterViewModel by viewModels {
        RegisterViewModel.Factory(
            AuthRepository(
                authApiService = AuthNetworkModule.authApiService,
                gson = AuthNetworkModule.gson
            )
        )
    }

    private lateinit var googleAuthUiClient: GoogleAuthUiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        googleAuthUiClient = GoogleAuthUiClient(this)

        bindViews()
        setupTextWatchers()
        setupPasswordToggles()
        setupClicks()
        observeViewModel()
    }

    private fun bindViews() {
        fullNameInputLayout = findViewById(R.id.fullNameInputLayout)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        usernameInputLayout = findViewById(R.id.usernameInputLayout)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout)
        fullNameEditText = findViewById(R.id.fullNameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        termsCheckbox = findViewById(R.id.termsCheckbox)
        createAccountButton = findViewById(R.id.createAccountButton)
        googleRegisterButton = findViewById(R.id.googleRegisterButton)
        signInText = findViewById(R.id.signInText)
        termsActionText = findViewById(R.id.termsActionText)
    }

    private fun setupTextWatchers() {
        fullNameEditText.doAfterTextChanged { viewModel.onFullNameChanged(it?.toString().orEmpty()) }
        emailEditText.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        usernameEditText.doAfterTextChanged { viewModel.onUsernameChanged(it?.toString().orEmpty()) }
        passwordEditText.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        confirmPasswordEditText.doAfterTextChanged {
            viewModel.onConfirmPasswordChanged(it?.toString().orEmpty())
        }
        termsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onTermsChanged(isChecked)
        }
    }

    private fun setupPasswordToggles() {
        passwordInputLayout.setEndIconOnClickListener {
            viewModel.togglePasswordVisibility()
        }

        confirmPasswordInputLayout.setEndIconOnClickListener {
            viewModel.toggleConfirmPasswordVisibility()
        }
    }

    private fun setupClicks() {
        createAccountButton.setOnClickListener {
            viewModel.register()
        }

        googleRegisterButton.setOnClickListener {
            registerWithGoogle()
        }

        signInText.setOnClickListener {
            finish()
        }

        termsActionText.setOnClickListener {
            Toast.makeText(this, R.string.register_terms_placeholder, Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: RegisterUiState) {
        fullNameInputLayout.error = state.fullNameError
        emailInputLayout.error = state.emailError
        usernameInputLayout.error = state.usernameError
        passwordInputLayout.error = state.passwordError
        confirmPasswordInputLayout.error = state.confirmPasswordError ?: state.termsError

        if (termsCheckbox.isChecked != state.isTermsAccepted) {
            termsCheckbox.isChecked = state.isTermsAccepted
        }

        updatePasswordVisibility(passwordInputLayout, passwordEditText, state.isPasswordVisible)
        updatePasswordVisibility(
            confirmPasswordInputLayout,
            confirmPasswordEditText,
            state.isConfirmPasswordVisible
        )

        val isAnyLoading = state.isRegisterLoading || state.isGoogleLoading
        fullNameEditText.isEnabled = !isAnyLoading
        emailEditText.isEnabled = !isAnyLoading
        usernameEditText.isEnabled = !isAnyLoading
        passwordEditText.isEnabled = !isAnyLoading
        confirmPasswordEditText.isEnabled = !isAnyLoading
        termsCheckbox.isEnabled = !isAnyLoading
        createAccountButton.isEnabled = state.isTermsAccepted && !isAnyLoading
        googleRegisterButton.isEnabled = state.isTermsAccepted && !isAnyLoading

        createAccountButton.text = if (state.isRegisterLoading) {
            getString(R.string.register_loading)
        } else {
            getString(R.string.register_submit)
        }

        googleRegisterButton.text = if (state.isGoogleLoading) {
            getString(R.string.register_google_loading)
        } else {
            getString(R.string.register_google)
        }
    }

    private fun handleEvent(event: RegisterEvent) {
        when (event) {
            is RegisterEvent.NavigateToLogin -> {
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_PREFILLED_USERNAME, event.username)
                    }
                )
                finish()
            }

            is RegisterEvent.NavigateToMain -> {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_USERNAME, event.user.username)
                        putExtra(LoginActivity.EXTRA_PROVIDER, event.user.provider.name)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }

            is RegisterEvent.ShowMessage -> {
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun registerWithGoogle() {
        lifecycleScope.launch {
            viewModel.onGoogleRegisterStarted()
            val result = googleAuthUiClient.signIn()
            viewModel.onGoogleRegisterFinished(result)
        }
    }

    private fun updatePasswordVisibility(
        inputLayout: TextInputLayout,
        editText: TextInputEditText,
        isVisible: Boolean
    ) {
        editText.transformationMethod = if (isVisible) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }
        inputLayout.setEndIconDrawable(if (isVisible) R.drawable.eye_off else R.drawable.eye)
        editText.setSelection(editText.text?.length ?: 0)
    }
}
