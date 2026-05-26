package com.example.filemanagementapp.login

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
import com.example.filemanagementapp.forgot.ForgotPasswordActivity
import com.example.filemanagementapp.login.data.local.LoginPreferencesRepository
import com.example.filemanagementapp.login.data.network.AuthNetworkModule
import com.example.filemanagementapp.login.data.repository.AuthRepository
import com.example.filemanagementapp.login.google.GoogleAuthUiClient
import com.example.filemanagementapp.login.ui.LoginEvent
import com.example.filemanagementapp.login.ui.LoginUiState
import com.example.filemanagementapp.login.ui.LoginViewModel
import com.example.filemanagementapp.main.MainActivity
import com.example.filemanagementapp.register.RegisterActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var googleButton: MaterialButton
    private lateinit var forgotPasswordText: TextView
    private lateinit var signUpText: TextView
    private lateinit var rememberMeCheckbox: CheckBox

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.Factory(
            authRepository = AuthRepository(
                authApiService = AuthNetworkModule.authApiService,
                gson = AuthNetworkModule.gson
            ),
            loginPreferencesRepository = LoginPreferencesRepository(applicationContext)
        )
    }

    private lateinit var googleAuthUiClient: GoogleAuthUiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        googleAuthUiClient = GoogleAuthUiClient(this)

        bindViews()
        applyPrefilledUsername()
        setupTextWatchers()
        setupPasswordToggle()
        setupClicks()
        observeViewModel()
    }

    private fun bindViews() {
        emailInputLayout = findViewById(R.id.emailInputLayout)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        googleButton = findViewById(R.id.googleButton)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)
        signUpText = findViewById(R.id.signUpText)
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox)
    }

    private fun applyPrefilledUsername() {
        val prefilledUsername = intent.getStringExtra(EXTRA_PREFILLED_USERNAME).orEmpty()
        if (prefilledUsername.isNotBlank()) {
            emailEditText.setText(prefilledUsername)
            emailEditText.setSelection(prefilledUsername.length)
            viewModel.onUsernameChanged(prefilledUsername)
        }
    }

    private fun setupTextWatchers() {
        emailEditText.doAfterTextChanged { editable ->
            viewModel.onUsernameChanged(editable?.toString().orEmpty())
        }
        passwordEditText.doAfterTextChanged { editable ->
            viewModel.onPasswordChanged(editable?.toString().orEmpty())
        }
        rememberMeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onRememberMeChanged(isChecked)
        }
    }

    private fun setupPasswordToggle() {
        passwordInputLayout.setEndIconOnClickListener {
            viewModel.togglePasswordVisibility()
        }
    }

    private fun setupClicks() {
        loginButton.setOnClickListener {
            viewModel.login()
        }

        googleButton.setOnClickListener {
            loginWithGoogle()
        }

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        signUpText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::render)
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun render(state: LoginUiState) {
        if (emailEditText.text?.toString().orEmpty() != state.username) {
            emailEditText.setText(state.username)
            emailEditText.setSelection(emailEditText.text?.length ?: 0)
        }
        if (rememberMeCheckbox.isChecked != state.isRememberMeChecked) {
            rememberMeCheckbox.isChecked = state.isRememberMeChecked
        }

        emailInputLayout.error = state.usernameError
        passwordInputLayout.error = state.passwordError

        passwordEditText.transformationMethod = if (state.isPasswordVisible) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }
        passwordInputLayout.setEndIconDrawable(
            if (state.isPasswordVisible) R.drawable.eye_off else R.drawable.eye
        )
        passwordEditText.setSelection(passwordEditText.text?.length ?: 0)

        val isAnyLoading = state.isLoginLoading || state.isGoogleLoading
        emailEditText.isEnabled = !isAnyLoading
        passwordEditText.isEnabled = !isAnyLoading
        rememberMeCheckbox.isEnabled = !isAnyLoading
        loginButton.isEnabled = !isAnyLoading
        googleButton.isEnabled = !isAnyLoading

        loginButton.text = if (state.isLoginLoading) {
            getString(R.string.login_loading)
        } else {
            getString(R.string.login_submit)
        }

        googleButton.text = if (state.isGoogleLoading) {
            getString(R.string.login_google_loading)
        } else {
            getString(R.string.login_google)
        }
    }

    private fun handleEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.NavigateToMain -> {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        putExtra(EXTRA_USERNAME, event.user.username)
                        putExtra(EXTRA_PROVIDER, event.user.provider.name)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }

            is LoginEvent.ShowMessage -> {
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loginWithGoogle() {
        lifecycleScope.launch {
            viewModel.onGoogleLoginStarted()
            val result = googleAuthUiClient.signIn()
            viewModel.onGoogleLoginFinished(result)
        }
    }

    companion object {
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PROVIDER = "extra_provider"
        const val EXTRA_PREFILLED_USERNAME = "extra_prefilled_username"
    }
}
