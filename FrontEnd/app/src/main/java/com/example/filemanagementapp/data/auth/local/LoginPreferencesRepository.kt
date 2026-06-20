package com.example.filemanagementapp.data.auth.local

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val LOGIN_PREFERENCES_NAME = "login_preferences"

private val Context.loginDataStore by preferencesDataStore(
    name = LOGIN_PREFERENCES_NAME
)

data class SavedLoginPreferences(
    val rememberedUsername: String = "",
    val isRememberMeChecked: Boolean = false,
    val avatarUrl: String? = null
)

class LoginPreferencesRepository(
    private val context: Context
) {
    private object Keys {
        val rememberedUsername = stringPreferencesKey("remembered_username")
        val rememberMeChecked = booleanPreferencesKey("remember_me_checked")
        val avatarUrl = stringPreferencesKey("avatar_url")
    }

    val preferencesFlow: Flow<SavedLoginPreferences> =
        context.loginDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map(::toSavedPreferences)

    suspend fun saveRememberedLogin(username: String, rememberMe: Boolean, avatarUrl: String? = null) {
        context.loginDataStore.edit { preferences ->
            if (rememberMe) {
                preferences[Keys.rememberedUsername] = username
                preferences[Keys.rememberMeChecked] = true
            } else {
                preferences.remove(Keys.rememberedUsername)
                preferences[Keys.rememberMeChecked] = false
            }
            if (avatarUrl != null) {
                preferences[Keys.avatarUrl] = avatarUrl
            } else {
                preferences.remove(Keys.avatarUrl)
            }
        }
    }

    suspend fun clearRememberedLogin() {
        context.loginDataStore.edit(::clearSavedLogin)
    }

    private fun toSavedPreferences(preferences: Preferences): SavedLoginPreferences {
        return SavedLoginPreferences(
            rememberedUsername = preferences[Keys.rememberedUsername].orEmpty(),
            isRememberMeChecked = preferences[Keys.rememberMeChecked] ?: false,
            avatarUrl = preferences[Keys.avatarUrl]
        )
    }

    private fun clearSavedLogin(preferences: MutablePreferences) {
        preferences.remove(Keys.rememberedUsername)
        preferences[Keys.rememberMeChecked] = false
        preferences.remove(Keys.avatarUrl)
    }
}
