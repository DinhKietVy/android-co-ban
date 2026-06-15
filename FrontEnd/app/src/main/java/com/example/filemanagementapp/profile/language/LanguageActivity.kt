package com.example.filemanagementapp.profile.language

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.filemanagementapp.R
import com.google.android.material.appbar.MaterialToolbar

class LanguageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)

        findViewById<MaterialToolbar>(R.id.toolbarLanguage).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupLanguage)
        val radioSystem = findViewById<RadioButton>(R.id.radioSystem)
        val radioEn = findViewById<RadioButton>(R.id.radioEn)
        val radioVi = findViewById<RadioButton>(R.id.radioVi)

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            radioSystem.isChecked = true
        } else {
            val languageTag = currentLocales.get(0)?.language
            if (languageTag == "vi") {
                radioVi.isChecked = true
            } else {
                radioEn.isChecked = true
            }
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val appLocale: LocaleListCompat = when (checkedId) {
                R.id.radioEn -> LocaleListCompat.forLanguageTags("en")
                R.id.radioVi -> LocaleListCompat.forLanguageTags("vi")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }
}
