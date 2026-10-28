package com.equisteg.mydaycompanion.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onCalendarInPlans(true) else viewModel.onInfo("Calendar stays off. You can allow it later in Android Settings.")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.onExportDestinationChosen(uri)
    }

    Column(
        modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Privacy", style = MaterialTheme.typography.titleLarge)
        Text(
            "Everything stays on this phone in an encrypted database. MyDayCompanion has no internet " +
                "permission, no account, no cloud backup and no analytics.",
        )
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider()
        YourDaySection(state, viewModel::onPreferencesChange)

        HorizontalDivider()
        SecuritySection(state, viewModel::onSecuritySettingsChange, viewModel::onSaveChallenges, viewModel::onRemoveChallenges)

        HorizontalDivider()
        Text("Your data", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { exportLauncher.launch("mydaycompanion-export.json") }, enabled = !state.busy) {
            Text("Export all data")
        }
        Button(
            onClick = viewModel::onDeleteEverything,
            enabled = !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("delete-all"),
        ) { Text("Delete everything") }

        HorizontalDivider()
        Text("Voice and calendar", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show my calendar in plans")
                Text("Events from the phone's calendar are planned around. Read-only; nothing is changed or copied out.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.calendarInPlans,
                onCheckedChange = { on ->
                    when {
                        !on -> viewModel.onCalendarInPlans(false)
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED -> viewModel.onCalendarInPlans(true)
                        else -> askCalendar.launch(Manifest.permission.READ_CALENDAR)
                    }
                },
                enabled = !state.busy,
                modifier = Modifier.testTag("calendar-toggle"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Speak replies")
                Text("Says what was added out loud. \"Read\" on My day reads the day's summary any time.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.preferences.speechOutputEnabled,
                onCheckedChange = { viewModel.onPreferencesChange(state.preferences.copy(speechOutputEnabled = it)) },
                enabled = !state.busy,
                modifier = Modifier.testTag("speak-toggle"),
            )
        }
        Text(
            if (isOnDeviceRecognition(context)) "Voice input runs on this phone; your speech doesn't leave it."
            else "This phone has no on-device speech recognition, so 🎤 uses the phone's default speech service, which may send audio to its provider (for example Google). You can always type instead.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Microphone: asked the first time you tap 🎤, used only while you speak. " +
                "Calendar: asked when you switch on \"Show my calendar in plans\", read-only. " +
                "Alarms, calls, messages, directions and calendar entries open the right app for you to finish; each needs your confirmation and screen lock. " +
                "Notifications: asked once, for reminders and the morning brief. If you say no, everything else still works; " +
                "you can allow them later in Android Settings > Apps > MyDayCompanion > Notifications. " +
                "On the lock screen a reminder only says \"Reminder\", never what it is for. " +
                "The app also starts briefly after the phone restarts, only to put your reminders back. " +
                "It uses the system fingerprint, face or screen-lock prompt to confirm it's you; it never sees your fingerprint.",
        )

        HorizontalDivider()
        Text("Features", style = MaterialTheme.typography.titleMedium)
        Flag.entries.forEach { f ->
            val status = when {
                f.locked -> "off (not available in this version)"
                f.defaultEnabled -> "on"
                else -> "coming later"
            }
            Text("${f.key.replace('_', ' ')}: $status", style = MaterialTheme.typography.bodySmall)
        }
    }
}
