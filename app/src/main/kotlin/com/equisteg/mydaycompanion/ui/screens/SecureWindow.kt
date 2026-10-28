package com.equisteg.mydaycompanion.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Blocks screenshots, screen recording and the recent-apps preview while this composable is on
 * screen (build doc §11: memory, challenge questions and the backup passphrase; OQ-26: the whole
 * app while the app lock is on). Counted per window, so one caller leaving doesn't clear the flag
 * while another still needs it.
 */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val count = secureHolders.getOrDefault(activity, 0)
        if (count == 0) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        secureHolders[activity] = count + 1
        onDispose {
            val left = secureHolders.getOrDefault(activity, 1) - 1
            if (left <= 0) {
                secureHolders.remove(activity)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                secureHolders[activity] = left
            }
        }
    }
}

// Main thread only (composition); weak keys so a finished activity is not kept.
private val secureHolders = java.util.WeakHashMap<Activity, Int>()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
