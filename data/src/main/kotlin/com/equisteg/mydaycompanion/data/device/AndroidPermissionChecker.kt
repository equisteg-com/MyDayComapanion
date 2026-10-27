package com.equisteg.mydaycompanion.data.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.equisteg.mydaycompanion.domain.policy.PermissionChecker

/** Asks the real Android permission system; the policy engine never trusts a cached answer. */
class AndroidPermissionChecker(private val context: Context) : PermissionChecker {
    override fun isGranted(androidPermission: String): Boolean {
        // POST_NOTIFICATIONS only exists as a runtime permission from Android 13.
        if (androidPermission == "android.permission.POST_NOTIFICATIONS" && Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, androidPermission) == PackageManager.PERMISSION_GRANTED
    }
}
