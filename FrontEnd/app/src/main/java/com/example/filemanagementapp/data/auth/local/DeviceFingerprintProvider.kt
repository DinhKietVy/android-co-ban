package com.example.filemanagementapp.data.auth.local

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Generates and caches a stable 64-char lowercase hex SHA-256 device fingerprint
 * matching the backend's expected format (/^[0-9a-f]{32,128}$/).
 */
object DeviceFingerprintProvider {
    private const val PREFS_NAME = "device_security_prefs"
    private const val KEY_FINGERPRINT = "device_fingerprint"
    private const val KEY_FALLBACK_ID = "fallback_device_id"

    @Volatile
    private var cachedFingerprint: String? = null

    fun initialize(context: Context) {
        if (cachedFingerprint == null) {
            cachedFingerprint = computeOrLoad(context.applicationContext)
        }
    }

    fun getFingerprint(): String? = cachedFingerprint

    @SuppressLint("HardwareIds")
    private fun computeOrLoad(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_FINGERPRINT, null)
        if (!existing.isNullOrBlank() && existing.length == 64) {
            return existing
        }

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.takeIf { it.isNotBlank() } ?: run {
            prefs.getString(KEY_FALLBACK_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_FALLBACK_ID, it).apply()
            }
        }

        val rawSeed = listOf(
            androidId,
            Build.MANUFACTURER.orEmpty(),
            Build.MODEL.orEmpty(),
            context.packageName
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawSeed.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }

        prefs.edit().putString(KEY_FINGERPRINT, hex).apply()
        return hex
    }
}
