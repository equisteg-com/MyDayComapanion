package com.equisteg.mydaycompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.AuthOutcome
import com.equisteg.mydaycompanion.ui.AuthRequest
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme
import com.equisteg.mydaycompanion.ui.theme.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Hosts the Compose UI and the system authentication prompt (build doc §6.2). A FragmentActivity
 * because BiometricPrompt needs one. The view model asks for a prompt through its state; the
 * result goes straight back, so a confirmation is only ever created after the system says yes.
 */
class MainActivity : FragmentActivity() {
    private lateinit var viewModel: MainViewModel

    // Reminders need this on Android 13+. Asked once, after first-run setup; "no" is respected and the app works without it.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onNotificationsAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val services = (application as MyDayCompanionApp).services
        viewModel = ViewModelProvider(this, MainViewModel.factory(services))[MainViewModel::class.java]

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = viewModel.onAppForeground()
                override fun onStop(owner: LifecycleOwner) = viewModel.onAppBackground()
            },
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.map { it.authRequest }.distinctUntilChanged().collect { request -> request?.let(::authenticate) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.map { it.preferencesLoaded && !it.showOnboarding && !it.locked }.distinctUntilChanged().collect { ready ->
                    if (ready) askForNotificationsOnce()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.map { it.wiped }.distinctUntilChanged().collect { wiped -> if (wiped) restart() }
            }
        }

        setContent {
            // Only the look is collected here, so typing doesn't rebuild the theme.
            val look by remember { viewModel.state.map { it.themeMode to it.dynamicColor }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(viewModel.state.value.themeMode to viewModel.state.value.dynamicColor)
            val dark = when (look.first) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme, not only the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            MyDayCompanionTheme(look.first, look.second) {
                AppNavigation(viewModel)
            }
        }
    }

    private fun authenticate(request: AuthRequest) {
        // Strong biometrics or the screen lock. Android 8-10 cannot combine strong biometrics with the
        // screen lock in one prompt, so those versions use the platform's default biometric class.
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.onDeviceAuthResult(AuthOutcome.UNAVAILABLE)
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    viewModel.onDeviceAuthResult(AuthOutcome.SUCCEEDED)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    viewModel.onDeviceAuthResult(
                        if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                            AuthOutcome.UNAVAILABLE
                        } else {
                            AuthOutcome.CANCELLED
                        },
                    )
                // onAuthenticationFailed (one unrecognised finger) keeps the prompt open; the system limits retries.
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(request.title)
                .setSubtitle(request.subtitle)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
                .build(),
        )
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("permission_requests", MODE_PRIVATE)
        if (prefs.getBoolean("notifications", false)) return
        prefs.edit { putBoolean("notifications", true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        // The scrims androidx.activity uses by default for 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }

    /** After a wipe the database and its key are gone; start a fresh process so nothing old stays in memory. */
    private fun restart() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) startActivity(launch)
        finishAffinity()
        exitProcess(0)
    }
}
