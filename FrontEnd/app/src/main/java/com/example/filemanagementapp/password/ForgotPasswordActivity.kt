package com.example.filemanagementapp.password

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ImageButton
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
import com.example.filemanagementapp.login.data.network.AuthNetworkModule
import com.example.filemanagementapp.login.data.repository.AuthRepository
import com.example.filemanagementapp.password.ui.ForgotPasswordEvent
import com.example.filemanagementapp.password.ui.ForgotPasswordStep
import com.example.filemanagementapp.password.ui.ForgotPasswordUiState
import com.example.filemanagementapp.password.ui.ForgotPasswordViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var topBackButton: ImageButton
    private lateinit var defaultContent: View
    private lateinit var otpContent: View
    private lateinit var newPasswordContent: View
    private lateinit var successContent: View
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var otpInputLayout: TextInputLayout
    private lateinit var newPasswordInputLayout: TextInputLayout
    private lateinit var confirmPasswordInputLayout: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var otpEditText: TextInputEditText
    private lateinit var newPasswordEditText: TextInputEditText
    private lateinit var confirmPasswordEditText: TextInputEditText
    private lateinit var sendResetButton: MaterialButton
    private lateinit var verifyOtpButton: MaterialButton
    private lateinit var resetPasswordButton: MaterialButton
    private lateinit var backToLoginButton: View
    private lateinit var successBackButton: View
    private lateinit var resendOtpText: TextView
    private lateinit var resendLinkText: TextView
    private lateinit var sentEmailChip: TextView
    private lateinit var successTitleText: TextView
    private lateinit var successDescriptionText: TextView
    private lateinit var successInfoText: TextView

    private val viewModel: ForgotPasswordViewModel by viewModels {
        ForgotPasswordViewModel.Factory(
            AuthRepository(
                authApiService = AuthNetworkModule.authApiService,
                gson = AuthNetworkModule.gson
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupTextWatchers()
        setupActions()
        observeViewModel()
    }

    private fun bindViews() {
        topBackButton = findViewById(R.id.topBackButton)
        defaultContent = findViewById(R.id.defaultContent)
        otpContent = findViewById(R.id.otpContent)
        newPasswordContent = findViewById(R.id.newPasswordContent)
        successContent = findViewById(R.id.successContent)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        otpInputLayout = findViewById(R.id.otpInputLayout)
        newPasswordInputLayout = findViewById(R.id.newPasswordInputLayout)
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout)
        emailEditText = findViewById(R.id.emailEditText)
        otpEditText = findViewById(R.id.otpEditText)
        newPasswordEditText = findViewById(R.id.newPasswordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        sendResetButton = findViewById(R.id.sendResetButton)
        verifyOtpButton = findViewById(R.id.verifyOtpButton)
        resetPasswordButton = findViewById(R.id.resetPasswordButton)
        backToLoginButton = findViewById(R.id.backToLoginButton)
        successBackButton = findViewById(R.id.successBackButton)
        resendOtpText = findViewById(R.id.resendOtpText)
        resendLinkText = findViewById(R.id.resendLinkText)
        sentEmailChip = findViewById(R.id.sentEmailChip)
        successTitleText = findViewById(R.id.successTitleText)
        successDescriptionText = findViewById(R.id.successDescriptionText)
        successInfoText = findViewById(R.id.successInfoText)
    }

    private fun setupTextWatchers() {
        emailEditText.doAfterTextChanged {
            viewModel.onEmailChanged(it?.toString().orEmpty())
        }
        otpEditText.doAfterTextChanged {
            viewModel.onOtpChanged(it?.toString().orEmpty())
        }
        newPasswordEditText.doAfterTextChanged {
            viewModel.onNewPasswordChanged(it?.toString().orEmpty())
        }
        confirmPasswordEditText.doAfterTextChanged {
            viewModel.onConfirmPasswordChanged(it?.toString().orEmpty())
        }
    }

    private fun setupActions() {
        topBackButton.setOnClickListener {
            when (viewModel.uiState.value.step) {
                ForgotPasswordStep.EMAIL -> finish()
                ForgotPasswordStep.OTP -> viewModel.returnToEmailStep()
                ForgotPasswordStep.RESET -> viewModel.returnToOtpStep()
                ForgotPasswordStep.SUCCESS -> finish()
            }
        }

        backToLoginButton.setOnClickListener { finish() }
        successBackButton.setOnClickListener { finish() }
        resendLinkText.setOnClickListener { finish() }
        sendResetButton.setOnClickListener { viewModel.sendResetEmail() }
        resendOtpText.setOnClickListener { viewModel.sendResetEmail(isResend = true) }
        verifyOtpButton.setOnClickListener { viewModel.verifyOtp() }
        resetPasswordButton.setOnClickListener { viewModel.resetPassword() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: ForgotPasswordUiState) {
        updateEditText(emailEditText, state.email)
        updateEditText(otpEditText, state.otp)
        updateEditText(newPasswordEditText, state.newPassword)
        updateEditText(confirmPasswordEditText, state.confirmPassword)

        defaultContent.visibility =
            if (state.step == ForgotPasswordStep.EMAIL) View.VISIBLE else View.GONE
        otpContent.visibility = if (state.step == ForgotPasswordStep.OTP) View.VISIBLE else View.GONE
        newPasswordContent.visibility =
            if (state.step == ForgotPasswordStep.RESET) View.VISIBLE else View.GONE
        successContent.visibility =
            if (state.step == ForgotPasswordStep.SUCCESS) View.VISIBLE else View.GONE

        emailInputLayout.error = state.emailError
        otpInputLayout.error = state.otpError
        newPasswordInputLayout.error = state.newPasswordError
        confirmPasswordInputLayout.error = state.confirmPasswordError

        val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()
        emailInputLayout.endIconMode =
            if (isEmailValid && state.emailError == null) {
                TextInputLayout.END_ICON_CUSTOM
            } else {
                TextInputLayout.END_ICON_NONE
            }
        if (isEmailValid && state.emailError == null) {
            emailInputLayout.endIconDrawable = getDrawable(R.drawable.check_circle)
            emailInputLayout.setEndIconTintList(getColorStateList(R.color.forgot_green))
        }

        val isAnyLoading =
            state.isSendingEmail || state.isVerifyingOtp || state.isResettingPassword

        emailEditText.isEnabled = !isAnyLoading
        otpEditText.isEnabled = !isAnyLoading
        newPasswordEditText.isEnabled = !isAnyLoading
        confirmPasswordEditText.isEnabled = !isAnyLoading
        backToLoginButton.isEnabled = !isAnyLoading
        successBackButton.isEnabled = !isAnyLoading
        topBackButton.isEnabled = !isAnyLoading

        sendResetButton.isEnabled = isEmailValid && !isAnyLoading
        verifyOtpButton.isEnabled = state.otp.length == 6 && !isAnyLoading
        resetPasswordButton.isEnabled =
            state.newPassword.isNotBlank() &&
            state.confirmPassword.isNotBlank() &&
            !isAnyLoading
        resendOtpText.isEnabled = !isAnyLoading

        sendResetButton.text = when {
            state.isSendingEmail && state.step == ForgotPasswordStep.OTP ->
                getString(R.string.forgot_resend_loading)

            state.isSendingEmail -> getString(R.string.forgot_send_loading)
            else -> getString(R.string.forgot_send)
        }

        verifyOtpButton.text = if (state.isVerifyingOtp) {
            getString(R.string.forgot_verify_loading)
        } else {
            getString(R.string.forgot_verify_otp)
        }

        resetPasswordButton.text = if (state.isResettingPassword) {
            getString(R.string.forgot_reset_loading)
        } else {
            getString(R.string.forgot_reset_password_button)
        }
        resendOtpText.text = if (state.isSendingEmail && state.step == ForgotPasswordStep.OTP) {
            getString(R.string.forgot_resend_loading)
        } else {
            getString(R.string.forgot_resend)
        }

        sentEmailChip.text = state.submittedEmail.ifBlank { state.email.trim() }
        successTitleText.text = getString(R.string.forgot_reset_success_title)
        successDescriptionText.text = getString(R.string.forgot_reset_success_message)
        successInfoText.text = getString(R.string.forgot_reset_success_info)
        resendLinkText.visibility = View.GONE
    }

    private fun handleEvent(event: ForgotPasswordEvent) {
        when (event) {
            is ForgotPasswordEvent.ShowMessage -> {
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateEditText(editText: TextInputEditText, value: String) {
        if (editText.text?.toString().orEmpty() != value) {
            editText.setText(value)
            editText.setSelection(editText.text?.length ?: 0)
        }
    }
}
