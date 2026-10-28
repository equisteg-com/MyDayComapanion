package com.equisteg.mydaycompanion.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equisteg.mydaycompanion.domain.security.ApprovalStep
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.UiState
import com.equisteg.mydaycompanion.ui.screens.BackupPassphraseDialog
import com.equisteg.mydaycompanion.ui.screens.ChallengeDialog
import com.equisteg.mydaycompanion.ui.screens.HistoryScreen
import com.equisteg.mydaycompanion.ui.screens.LockScreen
import com.equisteg.mydaycompanion.ui.screens.MemoryScreen
import com.equisteg.mydaycompanion.ui.screens.OnboardingScreen
import com.equisteg.mydaycompanion.ui.screens.PlanScreen
import com.equisteg.mydaycompanion.ui.screens.RoutinesScreen
import com.equisteg.mydaycompanion.ui.screens.SettingsScreen

enum class Destination(val label: String, val icon: ImageVector) {
    PLAN("My day", Icons.Filled.DateRange),
    ROUTINES("Routines", Icons.Filled.Refresh),
    MEMORY("Memory", Icons.Filled.Star),
    HISTORY("History", Icons.Filled.Info),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    var current by rememberSaveable { mutableStateOf(Destination.PLAN) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.locked) {
        Scaffold { padding -> LockScreen(state, viewModel::onUnlockRequested, viewModel::onPanicWipe, Modifier.padding(padding)) }
        state.challenge?.let { ChallengeDialog(it, state.busy, viewModel::onChallengeAnswer, viewModel::onCancelChallenge) }
        return
    }

    if (state.showOnboarding) {
        val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onRestoreFileChosen(uri)
        }
        Scaffold { padding ->
            OnboardingScreen(state, viewModel::onFinishOnboarding, viewModel::onSaveChallenges, Modifier.padding(padding)) { restoreLauncher.launch(arrayOf("*/*")) }
        }
        ApprovalDialogs(viewModel, state)
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = current == d,
                        onClick = {
                            current = d
                            if (d == Destination.HISTORY) viewModel.refreshHistory()
                            if (d == Destination.MEMORY) viewModel.refreshMemories()
                        },
                        icon = {
                            val waiting = if (d == Destination.MEMORY) state.proposedMemories.size else 0
                            BadgedBox(badge = { if (waiting > 0) Badge(Modifier.testTag("memory-badge")) { Text("$waiting") } }) {
                                Icon(d.icon, contentDescription = null)
                            }
                        },
                        label = { Text(d.label) },
                        modifier = Modifier.testTag("nav-${d.name}"),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (current) {
            Destination.PLAN -> PlanScreen(viewModel, modifier)
            Destination.ROUTINES -> RoutinesScreen(viewModel, modifier)
            Destination.MEMORY -> MemoryScreen(viewModel, modifier)
            Destination.HISTORY -> HistoryScreen(state, viewModel::onHistoryFilter, viewModel::onHistoryKeepDays, modifier)
            Destination.SETTINGS -> SettingsScreen(viewModel, modifier)
        }
    }

    ApprovalDialogs(viewModel, state)
}

/** The challenge question and the Confirm step of a pending approval, shown over any screen. */
@Composable
private fun ApprovalDialogs(viewModel: MainViewModel, state: UiState) {
    state.backupPrompt?.let {
        BackupPassphraseDialog(it, state.busy, viewModel::onBackupPassphrase, viewModel::onRestorePassphrase, viewModel::onCancelBackupPrompt)
    }
    state.challenge?.let { ChallengeDialog(it, state.busy, viewModel::onChallengeAnswer, viewModel::onCancelChallenge) }

    state.pending?.takeIf { it.steps.firstOrNull() == ApprovalStep.TAP }?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::onCancelPending,
            title = { Text(pending.title) },
            text = { Text(pending.body) },
            confirmButton = { TextButton(onClick = viewModel::onConfirmPending, modifier = Modifier.testTag("confirm")) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = viewModel::onCancelPending) { Text("Cancel") } },
        )
    }
}
