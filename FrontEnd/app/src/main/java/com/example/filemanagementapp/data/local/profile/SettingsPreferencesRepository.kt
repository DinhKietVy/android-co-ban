package com.example.filemanagementapp.data.local.profile

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val SETTINGS_PREFERENCES_NAME = "settings_preferences"

private val Context.settingsDataStore by preferencesDataStore(
    name = SETTINGS_PREFERENCES_NAME
)

data class AiFeaturesSettings(
    val autoOcrEnabled: Boolean = false,
    val autoObjectEnabled: Boolean = false,
    val aiMetadataEnabled: Boolean = false
)

class SettingsPreferencesRepository(private val context: Context) {

    private object Keys {
        val autoOcrEnabled = booleanPreferencesKey("auto_ocr_enabled")
        val autoObjectEnabled = booleanPreferencesKey("auto_object_enabled")
        val aiMetadataEnabled = booleanPreferencesKey("ai_metadata_enabled")
        val themeMode = intPreferencesKey("theme_mode")
    }

    val aiSettingsFlow: Flow<AiFeaturesSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AiFeaturesSettings(
                autoOcrEnabled = preferences[Keys.autoOcrEnabled] ?: false,
                autoObjectEnabled = preferences[Keys.autoObjectEnabled] ?: false,
                aiMetadataEnabled = preferences[Keys.aiMetadataEnabled] ?: false
            )
        }

    val themeModeFlow: Flow<Int> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.themeMode] ?: androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

    suspend fun updateAutoOcr(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.autoOcrEnabled] = enabled
        }
    }

    suspend fun updateAutoObject(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.autoObjectEnabled] = enabled
        }
    }

    suspend fun updateAiMetadata(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.aiMetadataEnabled] = enabled
        }
    }

    suspend fun updateThemeMode(mode: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.themeMode] = mode
        }
    }
}
