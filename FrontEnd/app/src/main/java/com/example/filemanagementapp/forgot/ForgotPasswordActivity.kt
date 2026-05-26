package com.example.filemanagementapp.forgot

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.example.filemanagementapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var defaultContent: View
    private lateinit var successContent: View
    private lateinit var emailFieldContainer: View
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var sendResetButton: MaterialButton
    private lateinit var sentEmailChip: TextView
    private var lastSubmittedEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupEmailField()
        setupActions()
    }

    private fun bindViews() {
        defaultContent = findViewById(R.id.defaultContent)
        successContent = findViewById(R.id.successContent)
        emailFieldContainer = findViewById(R.id.emailFieldContainer)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        emailEditText = findViewById(R.id.emailEditText)
        sendResetButton = findViewById(R.id.sendResetButton)
        sentEmailChip = findViewById(R.id.sentEmailChip)
    }

    private fun setupEmailField() {
        emailEditText.doAfterTextChanged {
            val email = it?.toString().orEmpty().trim()
            val isValid = isValidEmail(email)
            emailInputLayout.endIconMode =
                if (isValid) TextInputLayout.END_ICON_CUSTOM else TextInputLayout.END_ICON_NONE
            if (isValid) {
                emailInputLayout.endIconDrawable = getDrawable(R.drawable.check_circle)
                emailInputLayout.setEndIconTintList(getColorStateList(R.color.forgot_green))
            }
            sendResetButton.isEnabled = isValid
        }
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.topBackButton).setOnClickListener {
            if (successContent.visibility == View.VISIBLE) {
                showDefaultState()
            } else {
                finish()
            }
        }
        findViewById<View>(R.id.backToLoginButton).setOnClickListener { finish() }
        findViewById<View>(R.id.successBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.resendLinkText).setOnClickListener { showDefaultState() }
        sendResetButton.setOnClickListener {
            val email = emailEditText.text?.toString().orEmpty().trim()
            when {
                email.isEmpty() -> {
                    emailEditText.requestFocus()
                    shakeEmailField()
                }

                !isValidEmail(email) -> {
                    shakeEmailField()
                }

                else -> {
                    lastSubmittedEmail = email
                    sentEmailChip.text = email
                    showSuccessState()
                }
            }
        }
    }

    private fun showSuccessState() {
        defaultContent.visibility = View.GONE
        successContent.visibility = View.VISIBLE
    }

    private fun showDefaultState() {
        defaultContent.visibility = View.VISIBLE
        successContent.visibility = View.GONE
        if (lastSubmittedEmail.isNotEmpty()) {
            emailEditText.setText(lastSubmittedEmail)
            emailEditText.setSelection(lastSubmittedEmail.length)
        }
    }

    private fun shakeEmailField() {
        emailFieldContainer.animate().cancel()
        emailFieldContainer.translationX = 0f
        emailFieldContainer.animate()
            .translationX(-18f)
            .setDuration(50L)
            .withEndAction {
                emailFieldContainer.animate()
                    .translationX(16f)
                    .setDuration(70L)
                    .withEndAction {
                        emailFieldContainer.animate()
                            .translationX(-10f)
                            .setDuration(70L)
                            .withEndAction {
                                emailFieldContainer.animate()
                                    .translationX(6f)
                                    .setDuration(60L)
                                    .withEndAction {
                                        emailFieldContainer.animate()
                                            .translationX(0f)
                                            .setDuration(50L)
                                            .start()
                                    }
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
