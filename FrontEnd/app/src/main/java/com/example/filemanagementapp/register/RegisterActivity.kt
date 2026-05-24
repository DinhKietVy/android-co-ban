package com.example.filemanagementapp.register

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.CheckBox
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.filemanagementapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RegisterActivity : AppCompatActivity() {
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var confirmPasswordInputLayout: TextInputLayout
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var confirmPasswordEditText: TextInputEditText
    private lateinit var termsCheckbox: CheckBox
    private lateinit var createAccountButton: MaterialButton
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViews()
        setupPasswordToggles()
        setupTermsCheckbox()
    }

    private fun setupViews() {
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        termsCheckbox = findViewById(R.id.termsCheckbox)
        createAccountButton = findViewById(R.id.createAccountButton)
    }

    private fun setupPasswordToggles() {
        passwordInputLayout.setEndIconOnClickListener {
            isPasswordVisible = !isPasswordVisible
            updatePasswordVisibility(
                inputLayout = passwordInputLayout,
                editText = passwordEditText,
                isVisible = isPasswordVisible
            )
        }

        confirmPasswordInputLayout.setEndIconOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            updatePasswordVisibility(
                inputLayout = confirmPasswordInputLayout,
                editText = confirmPasswordEditText,
                isVisible = isConfirmPasswordVisible
            )
        }
    }

    private fun setupTermsCheckbox() {
        termsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            createAccountButton.isEnabled = isChecked
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
