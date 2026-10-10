package com.example.filemanagementapp.data.drive.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DRIVE_PREFERENCES_NAME = "drive_preferences"

private val Context.driveDataStore by preferencesDataStore(
    name = DRIVE_PREFERENCES_NAME
)

data class DriveAccountInfo(
    val isConnected: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
    val lastSyncTime: Long = 0L
)

class DrivePreferencesRepository(
    private val context: Context
) {
    private object Keys {
        val isConnected = booleanPreferencesKey("drive_is_connected")
        val email = stringPreferencesKey("drive_email")
        val displayName = stringPreferencesKey("drive_display_name")
        val lastSyncTime = longPreferencesKey("drive_last_sync_time")
    }

    val accountInfoFlow: Flow<DriveAccountInfo> =
        context.driveDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs ->
                DriveAccountInfo(
                    isConnected = prefs[Keys.isConnected] ?: false,
                    email = prefs[Keys.email],
                    displayName = prefs[Keys.displayName],
                    lastSyncTime = prefs[Keys.lastSyncTime] ?: 0L
                )
            }

    suspend fun saveConnectedAccount(email: String, displayName: String?) {
        context.driveDataStore.edit { prefs ->
            prefs[Keys.isConnected] = true
            prefs[Keys.email] = email
            if (displayName != null) {
                prefs[Keys.displayName] = displayName
            }
            prefs[Keys.lastSyncTime] = System.currentTimeMillis()
        }
    }

    suspend fun clearAccount() {
        context.driveDataStore.edit { prefs ->
            prefs[Keys.isConnected] = false
            prefs.remove(Keys.email)
            prefs.remove(Keys.displayName)
            prefs.remove(Keys.lastSyncTime)
        }
    }
}
