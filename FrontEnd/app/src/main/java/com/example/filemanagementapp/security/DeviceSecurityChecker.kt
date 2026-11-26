package com.example.filemanagementapp.security

import android.content.Context
import android.os.Build
import com.scottyab.rootbeer.RootBeer

data class DeviceSecurityStatus(
    val isRooted: Boolean,
    val isEmulator: Boolean
) {
    val isCompromised: Boolean get() = isRooted || isEmulator
}

/**
 * Checks device integrity using RootBeer and emulator heuristics.
 * Normal features may warn the user; high-risk features (Secure Vault)
 * can enforce [isEnvironmentTrustedForVault].
 */
object DeviceSecurityChecker {

    fun checkEnvironment(context: Context): DeviceSecurityStatus {
        val isRooted = runCatching {
            RootBeer(context.applicationContext).isRooted
        }.getOrDefault(false)

        return DeviceSecurityStatus(
            isRooted = isRooted,
            isEmulator = detectEmulator()
        )
    }

    fun isEnvironmentTrustedForVault(context: Context): Boolean {
        val status = checkEnvironment(context)
        return !status.isRooted
    }

    private fun detectEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("emu") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for x86") ||
            manufacturer.contains("genymotion") ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product == "google_sdk" ||
            product.contains("sdk_gphone") ||
            product.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }
}
