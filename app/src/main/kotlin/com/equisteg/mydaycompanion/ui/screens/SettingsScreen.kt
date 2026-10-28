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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.domain.ai.LocalModelStatus
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.UiState

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
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.onBackupDestinationChosen(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onRestoreFileChosen(uri)
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
        LostPhoneSection(state, viewModel::onLockScreenText) {
            try {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                viewModel.onInfo("Open Android Settings → Security on this phone.")
            }
        }

        HorizontalDivider()
        WidgetSection(state, viewModel::onWidgetTitles)

        HorizontalDivider()
        Text("Your data", style = MaterialTheme.typography.titleMedium)
        BackupSection(state, onBackUp = { backupLauncher.launch(viewModel.backupFileName()) }, onRestore = { restoreLauncher.launch(arrayOf("*/*")) })
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
                Text("Events from the phone's calendar are planned around. Read-only: the calendar is never changed. Event times and titles are kept in your saved plans (encrypted) and in your own exports.", style = MaterialTheme.typography.bodySmall)
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
            else "This phone has no on-device speech recognition, so voice input is off. Audio is never sent to an online service; you can type instead.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (viewModel.smartModelAllowed) {
            HorizontalDivider()
            SmartModelSection(state, viewModel::onSmartModel, viewModel::onDownloadModel)
        }

        HorizontalDivider()
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Microphone: asked the first time you tap 🎤, used only while you speak. " +
                "Calendar: asked when you switch on \"Show my calendar in plans\", read-only. " +
                "Alarms, calls, messages, directions and calendar entries open the right app for you to finish; each needs your confirmation and screen lock. " +
                "Notifications: asked once, for reminders and the morning brief. If you say no, everything else still works; " +
                "you can allow them later in Android Settings > Apps > MyDayCompanion > Notifications. " +
                "On a locked phone a reminder only says \"Reminder\", unless you choose to show the text (above). " +
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

/**
 * Gemini Nano (build doc D-03). Runs in the phone's AICore service on supported phones; the
 * built-in rules understand tasks everywhere else and whenever the model's answer doesn't check out.
 */
@Composable
private fun SmartModelSection(state: UiState, onToggle: (Boolean) -> Unit, onDownload: () -> Unit) {
    Text("Smarter understanding", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Use Gemini Nano on this phone")
            Text(
                "Understands looser wording and more kinds of dates. The model runs on this phone in Android's AICore service, and this app has no internet access. " +
                    "Its answers are checked like everything else, and you still approve each plan.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = state.smartModel && state.modelStatus != LocalModelStatus.UNAVAILABLE,
            onCheckedChange = onToggle,
            // Off can always be turned on (that is when the phone is checked); on needs a usable model.
            enabled = !state.busy && (!state.smartModel || (state.modelStatus != null && state.modelStatus != LocalModelStatus.UNAVAILABLE)),
            modifier = Modifier.testTag("smart-toggle"),
        )
    }
    Text(
        when {
            !state.smartModel -> "Switched off. The built-in rules understand your tasks; Gemini Nano isn't used."
            else -> when (state.modelStatus) {
                null -> "Checking this phone…"
                LocalModelStatus.AVAILABLE -> "Ready. Gemini Nano is understanding your tasks."
                LocalModelStatus.DOWNLOADABLE -> "This phone can use Gemini Nano. Android's AICore service downloads it once (the app itself stays offline)."
                LocalModelStatus.DOWNLOADING -> state.modelDownload ?: "Android is downloading Gemini Nano…"
                LocalModelStatus.UNAVAILABLE -> "This phone doesn't support Gemini Nano. The built-in rules understand your tasks instead."
            }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("model-status"),
    )
    if (state.smartModel && state.modelStatus == LocalModelStatus.DOWNLOADABLE) {
        state.modelDownload?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = onDownload, enabled = !state.busy, modifier = Modifier.testTag("model-download")) { Text("Download Gemini Nano") }
    }
}

/**
 * F14: what protects the owner if the phone is lost or stolen, and the two things to set up on the
 * phone itself. Remote erase is Android's Find My Device; this app has no network access to do it.
 */
@Composable
private fun LostPhoneSection(state: UiState, onLockScreenText: (Boolean) -> Unit, onOpenSecurity: () -> Unit) {
    Text("If your phone is lost or stolen", style = MaterialTheme.typography.titleMedium)
    Text(
        "Your data is encrypted with a key that only this phone's secure hardware can unlock. With app lock on, " +
            "someone holding your unlocked phone still needs your fingerprint, face or screen lock to open the agent, and " +
            "a challenge answer to delete, export or change security. Wrong answers lock the agent, or wipe it if you turned that on.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "1. Turn on Find My Device (Android Settings → Google → Find My Device). From another device you can then lock or " +
            "erase the phone at find.google.com, which erases this app too.\n" +
            "2. Turn on Theft protection where your phone offers it (Android Settings → Google → All services → Theft protection).",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onOpenSecurity, modifier = Modifier.testTag("open-security-settings")) { Text("Open Android security settings") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Show reminder text on the lock screen")
            Text("Off: a locked phone only shows \"Reminder\". Personal tasks never show their title.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = state.lockScreenText,
            onCheckedChange = onLockScreenText,
            modifier = Modifier.testTag("lock-screen-text"),
        )
    }
}

/** F19: the home-screen widget is outside the app lock, so names are the owner's choice. */
@Composable
private fun WidgetSection(state: UiState, onWidgetTitles: (Boolean) -> Unit) {
    Text("Home-screen widget", style = MaterialTheme.typography.titleMedium)
    Text(
        "Long-press your home screen, tap Widgets and pick MyDayCompanion to see what's left today. Tapping it opens the app.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Show names on the widget")
            Text("Off: the widget shows only times, "Task" and "Event". Personal tasks never show their name.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = state.widgetTitles,
            onCheckedChange = onWidgetTitles,
            modifier = Modifier.testTag("widget-titles"),
        )
    }
}
