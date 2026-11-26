package com.example.filemanagementapp.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.example.filemanagementapp.data.local.profile.AppSecuritySettings
import com.example.filemanagementapp.data.local.profile.SettingsPreferencesRepository
import com.example.filemanagementapp.login.LoginActivity
import com.example.filemanagementapp.password.ForgotPasswordActivity
import com.example.filemanagementapp.register.RegisterActivity
import com.example.filemanagementapp.splash.SplashActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Enforces FLAG_SECURE (anti-screenshot, anti-screen-recording, and Recent Apps masking)
 * and Biometric App Lock across all activities.
 */
object AppSecurityManager : Application.ActivityLifecycleCallbacks {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var currentSettings = AppSecuritySettings()

    private var startedActivityCount = 0
    private var needsBiometricUnlock = true
    private var isPromptShowing = false
    private var lastActivityRef: java.lang.ref.WeakReference<Activity>? = null

    fun initialize(application: Application, settingsRepository: SettingsPreferencesRepository) {
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            settingsRepository.securitySettingsFlow.collect { settings ->
                currentSettings = settings
                lastActivityRef?.get()?.let { applyScreenSecurity(it) }
            }
        }
    }

    fun markUnlocked() {
        needsBiometricUnlock = false
    }

    private fun applyScreenSecurity(activity: Activity) {
        if (currentSettings.screenSecurityEnabled) {
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun shouldRequireBiometricLock(activity: Activity): Boolean {
        if (!currentSettings.biometricLockEnabled) return false
        return activity !is SplashActivity &&
            activity !is LoginActivity &&
            activity !is RegisterActivity &&
            activity !is ForgotPasswordActivity
    }

    private fun promptBiometricIfNeeded(activity: Activity) {
        if (!needsBiometricUnlock || isPromptShowing) return
        if (!shouldRequireBiometricLock(activity)) return
        val fragmentActivity = activity as? FragmentActivity ?: return
        if (!BiometricAuthHelper.canAuthenticate(fragmentActivity)) {
            needsBiometricUnlock = false
            return
        }

        isPromptShowing = true
        BiometricAuthHelper.authenticate(
            activity = fragmentActivity,
            onSuccess = {
                isPromptShowing = false
                needsBiometricUnlock = false
            },
            onError = {
                isPromptShowing = false
                // Move app to background if user cancels/fails biometric unlock
                fragmentActivity.moveTaskToBack(true)
            }
        )
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        applyScreenSecurity(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivityCount == 0 && currentSettings.biometricLockEnabled) {
            needsBiometricUnlock = true
        }
        startedActivityCount++
    }

    override fun onActivityResumed(activity: Activity) {
        lastActivityRef = java.lang.ref.WeakReference(activity)
        applyScreenSecurity(activity)
        promptBiometricIfNeeded(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0 && currentSettings.biometricLockEnabled) {
            needsBiometricUnlock = true
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (lastActivityRef?.get() === activity) {
            lastActivityRef = null
        }
    }
}
