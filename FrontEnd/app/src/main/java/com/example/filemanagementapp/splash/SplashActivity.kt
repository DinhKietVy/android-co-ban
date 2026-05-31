package com.example.filemanagementapp.splash

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.filemanagementapp.R
import androidx.lifecycle.lifecycleScope
import com.example.filemanagementapp.login.LoginActivity
import com.example.filemanagementapp.data.auth.local.LoginPreferencesRepository
import com.example.filemanagementapp.data.auth.network.AuthNetworkModule
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.example.filemanagementapp.main.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private val runningAnimators = mutableListOf<Animator>()
    private lateinit var rootView: View
    private lateinit var loginPreferencesRepository: LoginPreferencesRepository
    private lateinit var authRepository: AuthRepository
    private var navigationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        rootView = findViewById(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        loginPreferencesRepository = LoginPreferencesRepository(applicationContext)
        authRepository = AuthRepository(
            authApiService = AuthNetworkModule.authApiService,
            gson = AuthNetworkModule.gson
        )
        startSplashAnimations()
        scheduleNavigation()
    }

    override fun onDestroy() {
        navigationJob?.cancel()
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
        super.onDestroy()
    }

    private fun startSplashAnimations() {
        val topOrb = findViewById<View>(R.id.topOrb)
        val bottomOrb = findViewById<View>(R.id.bottomOrb)
        val centerContent = findViewById<View>(R.id.centerContent)
        val logoContainer = findViewById<View>(R.id.logoContainer)
        val logoBackground = findViewById<View>(R.id.logoBackground)
        val sparkleBadge = findViewById<View>(R.id.sparkleBadge)
        val titleText = findViewById<View>(R.id.titleText)
        val subtitleText = findViewById<View>(R.id.subtitleText)
        val loadingGroup = findViewById<View>(R.id.loadingGroup)
        val dots = listOf(
            findViewById<View>(R.id.loadingDot1),
            findViewById(R.id.loadingDot2),
            findViewById(R.id.loadingDot3)
        )

        centerContent.alpha = 0f
        centerContent.translationY = 32f
        logoContainer.scaleX = 0f
        logoContainer.scaleY = 0f
        logoContainer.rotation = -180f
        sparkleBadge.scaleX = 0f
        sparkleBadge.scaleY = 0f
        titleText.alpha = 0f
        titleText.translationY = 20f
        subtitleText.alpha = 0f
        subtitleText.translationY = 20f
        loadingGroup.alpha = 0f

        startAnimator(
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(topOrb, View.SCALE_X, 1f, 1.1f, 1f),
                    ObjectAnimator.ofFloat(topOrb, View.SCALE_Y, 1f, 1.1f, 1f),
                    ObjectAnimator.ofFloat(topOrb, View.ALPHA, 0.30f, 0.40f, 0.30f)
                )
                duration = 8000L
                repeatInfinitely()
                interpolator = AccelerateDecelerateInterpolator()
            }
        )

        startAnimator(
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(bottomOrb, View.SCALE_X, 1f, 1.15f, 1f),
                    ObjectAnimator.ofFloat(bottomOrb, View.SCALE_Y, 1f, 1.15f, 1f),
                    ObjectAnimator.ofFloat(bottomOrb, View.ALPHA, 0.25f, 0.35f, 0.25f)
                )
                startDelay = 1000L
                duration = 10000L
                repeatInfinitely()
                interpolator = AccelerateDecelerateInterpolator()
            }
        )

        startAnimator(
            ObjectAnimator.ofFloat(centerContent, View.ALPHA, 0f, 1f).apply {
                duration = 350L
                interpolator = DecelerateInterpolator()
            }
        )

        startAnimator(
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(centerContent, View.TRANSLATION_Y, 32f, 0f),
                    ObjectAnimator.ofFloat(logoContainer, View.SCALE_X, 0f, 1f),
                    ObjectAnimator.ofFloat(logoContainer, View.SCALE_Y, 0f, 1f),
                    ObjectAnimator.ofFloat(logoContainer, View.ROTATION, -180f, 0f)
                )
                startDelay = 200L
                duration = 700L
                interpolator = DecelerateInterpolator(1.6f)
            }
        )

        startAnimator(
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(titleText, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(titleText, View.TRANSLATION_Y, 20f, 0f),
                    ObjectAnimator.ofFloat(subtitleText, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(subtitleText, View.TRANSLATION_Y, 20f, 0f)
                )
                startDelay = 600L
                duration = 500L
                interpolator = DecelerateInterpolator()
            }
        )

        startAnimator(
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(sparkleBadge, View.SCALE_X, 0f, 1f),
                    ObjectAnimator.ofFloat(sparkleBadge, View.SCALE_Y, 0f, 1f)
                )
                startDelay = 500L
                duration = 450L
                interpolator = DecelerateInterpolator(2f)
            }
        )

        startAnimator(
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(logoBackground, View.SCALE_X, 1f, 1.04f, 1f),
                    ObjectAnimator.ofFloat(logoBackground, View.SCALE_Y, 1f, 1.04f, 1f)
                )
                startDelay = 900L
                duration = 3000L
                repeatInfinitely()
                interpolator = AccelerateDecelerateInterpolator()
            }
        )

        startAnimator(
            ObjectAnimator.ofFloat(loadingGroup, View.ALPHA, 0f, 1f).apply {
                startDelay = 1000L
                duration = 500L
                interpolator = DecelerateInterpolator()
            }
        )

        dots.forEachIndexed { index, dot ->
            startAnimator(
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(dot, View.SCALE_X, 1f, 1.3f, 1f),
                        ObjectAnimator.ofFloat(dot, View.SCALE_Y, 1f, 1.3f, 1f),
                        ObjectAnimator.ofFloat(dot, View.ALPHA, 0.5f, 1f, 0.5f)
                    )
                    startDelay = 1000L + index * 200L
                    duration = 1200L
                    repeatInfinitely()
                    interpolator = AccelerateDecelerateInterpolator()
                }
            )
        }
    }

    private fun startAnimator(animator: Animator) {
        runningAnimators += animator
        animator.start()
    }

    private fun scheduleNavigation() {
        navigationJob = lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            val savedPreferences = loginPreferencesRepository.preferencesFlow.first()
            val destinationIntent = authRepository.autoLogin()
                .map { user ->
                    Intent(this@SplashActivity, MainActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_USERNAME, user.username)
                        putExtra(LoginActivity.EXTRA_PROVIDER, user.provider.name)
                    }
                }
                .getOrElse {
                    Intent(this@SplashActivity, LoginActivity::class.java).apply {
                        if (savedPreferences.rememberedUsername.isNotBlank()) {
                            putExtra(
                                LoginActivity.EXTRA_PREFILLED_USERNAME,
                                savedPreferences.rememberedUsername
                            )
                        }
                    }
                }

            navigateTo(destinationIntent)
        }
    }

    private fun navigateTo(intent: Intent) {
        if (isFinishing || isDestroyed) return
        val options = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
        finish()
    }

    private fun AnimatorSet.repeatInfinitely() {
        childAnimations.forEach { child ->
            if (child is ValueAnimator) {
                child.repeatCount = ValueAnimator.INFINITE
                child.repeatMode = ValueAnimator.RESTART
            }
        }
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1800L
    }
}
