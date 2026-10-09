package com.equisteg.mydaycompanion.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Text("This version asks for no runtime permissions. It uses the system fingerprint, face or screen-lock prompt to confirm it's you; it never sees your fingerprint. Reminders (notifications), voice (microphone) and calendar reading will each ask separately when they arrive, and can be turned off here.")

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
