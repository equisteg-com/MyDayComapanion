package com.equisteg.mydaycompanion.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Shows live on-device permission grants; no analytics or server call is made here.
 * This is user transparency, NOT a claim of certification under every jurisdiction.
 */
@Composable
fun PrivacyReadinessCard(
    appLockEnabled: Boolean,
    externalSpeechAllowed: Boolean,
) {
    val context = LocalContext.current
    fun permitted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val microphone = permitted(Manifest.permission.RECORD_AUDIO)
    val calendar = permitted(Manifest.permission.READ_CALENDAR)
    val notifications = if (Build.VERSION.SDK_INT >= 33)
        permitted(Manifest.permission.POST_NOTIFICATIONS) else true

    Card(
        modifier = Modifier.fillMaxWidth().testTag("privacy-readiness"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Privacy check", style = MaterialTheme.typography.titleMedium)
            Text("Your tasks and plans remain on this device. The APK requests no internet permission.",
                style = MaterialTheme.typography.bodyMedium)
            PermissionStatusRow("App lock", if (appLockEnabled) "On" else "Off")
            PermissionStatusRow("Microphone permission", if (microphone) "Granted" else "Not granted")
            PermissionStatusRow("Calendar access", if (calendar) "Granted" else "Not granted")
            PermissionStatusRow("Notifications", if (notifications) "Allowed" else "Not allowed")
            PermissionStatusRow("Phone speech provider", if (externalSpeechAllowed) "Allowed" else "Off")
            Text(
                "Your phone's speech provider may process audio online if enabled. Below, you can " +
                    "export your data, create an encrypted backup, restore it, or delete everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(title: String, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
        Text(status, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
