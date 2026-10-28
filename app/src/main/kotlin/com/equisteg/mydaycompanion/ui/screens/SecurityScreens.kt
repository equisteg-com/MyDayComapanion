package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.equisteg.mydaycompanion.domain.security.ChallengeHasher
import com.equisteg.mydaycompanion.domain.security.PersonalApproval
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import com.equisteg.mydaycompanion.ui.ChallengePrompt
import com.equisteg.mydaycompanion.ui.UiState

/** Covers the whole app until the owner unlocks with the device credential (build doc §8.2). */
@Composable
fun LockScreen(state: UiState, onUnlock: () -> Unit, onPanicWipe: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp).testTag("lock-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MyDayCompanion is locked", style = MaterialTheme.typography.headlineSmall)
        Text("Unlock with your fingerprint, face or screen lock.")
        Button(onClick = onUnlock, enabled = !state.busy, modifier = Modifier.testTag("unlock")) { Text("Unlock") }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (state.hasChallenges) {
            TextButton(onClick = onPanicWipe, enabled = !state.busy, modifier = Modifier.testTag("panic-wipe")) {
                Text("Wipe this agent now (needs a challenge answer)")
            }
        }
    }
}

@Composable
fun ChallengeDialog(prompt: ChallengePrompt, busy: Boolean, onAnswer: (String) -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            ChallengeForm(prompt, busy, onAnswer, onCancel, Modifier.padding(24.dp))
        }
    }
}

/** The question, a hidden answer field and the buttons; kept outside the dialog so it can be tested on its own. */
@Composable
fun ChallengeForm(prompt: ChallengePrompt, busy: Boolean, onAnswer: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var answer by remember(prompt.questionId) { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Confirm it's you", style = MaterialTheme.typography.titleLarge)
        Text(prompt.question)
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("challenge-answer"),
        )
        prompt.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = { onAnswer(answer) }, enabled = !busy && answer.isNotBlank(), modifier = Modifier.testTag("challenge-submit")) { Text("Confirm") }
        }
    }
}

/** Settings → Security: app lock, wipe-on-failure, personal approvals and challenge questions. */
@Composable
fun SecuritySection(
    state: UiState,
    onChange: (SecuritySettings) -> Unit,
    onSaveChallenges: (List<Pair<String, String>>) -> Unit,
    onRemoveChallenges: () -> Unit,
) {
    val s = state.security
    var editing by remember { mutableStateOf(false) }

    Text("Security", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("App lock", modifier = Modifier.weight(1f))
        Switch(
            checked = s.appLockEnabled,
            onCheckedChange = { onChange(s.copy(appLockEnabled = it)) },
            enabled = !state.busy,
            modifier = Modifier.testTag("app-lock-switch"),
        )
    }
    if (s.appLockEnabled) {
        Text("Lock after", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            SecuritySettings.LOCK_AFTER_CHOICES.forEach { m ->
                FilterChip(
                    selected = s.lockAfterMinutes == m,
                    onClick = { if (s.lockAfterMinutes != m) onChange(s.copy(lockAfterMinutes = m)) },
                    label = { Text(if (m == 0) "At once" else "$m min") },
                    enabled = !state.busy,
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Wipe after 10 wrong answers in a day")
            Text("Off by default. Turn on only with a recent backup.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = s.wipeOnRepeatedFailure,
            onCheckedChange = { onChange(s.copy(wipeOnRepeatedFailure = it)) },
            enabled = !state.busy && state.hasChallenges,
            modifier = Modifier.testTag("wipe-switch"),
        )
    }
    Text("Approve personal items with", style = MaterialTheme.typography.bodySmall)
    PersonalApproval.entries.forEach { option ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = s.personalApproval == option,
                onClick = { if (s.personalApproval != option) onChange(s.copy(personalApproval = option)) },
                enabled = !state.busy && (option == PersonalApproval.DEVICE_CREDENTIAL || state.hasChallenges),
            )
            Text(if (option == PersonalApproval.DEVICE_CREDENTIAL) "Fingerprint, face or screen lock" else "A challenge question")
        }
    }

    Text("Challenge questions", style = MaterialTheme.typography.bodyLarge)
    Text(
        if (state.hasChallenges) {
            "Set up. One is asked before deleting everything or changing security, and when your screen lock is not available."
        } else {
            "Not set up. Add three questions only you can answer. Answers are stored as a one-way hash and cannot be recovered."
        },
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { editing = true }, enabled = !state.busy, modifier = Modifier.testTag("edit-challenges")) {
            Text(if (state.hasChallenges) "Change questions" else "Set up questions")
        }
        if (state.hasChallenges) {
            TextButton(onClick = onRemoveChallenges, enabled = !state.busy) { Text("Remove") }
        }
    }
    Text(
        "If your phone is lost: lock or erase it from find.google.com (Find My Device). Turn it on now in " +
            "Android Settings → Google → Find My Device.",
        style = MaterialTheme.typography.bodySmall,
    )

    if (editing) {
        ChallengeSetupDialog(
            onSave = { editing = false; onSaveChallenges(it) },
            onCancel = { editing = false },
        )
    }
}

@Composable
internal fun ChallengeSetupDialog(onSave: (List<Pair<String, String>>) -> Unit, onCancel: () -> Unit) {
    val questions = remember { mutableStateListOf(*ChallengeHasher.SUGGESTED_QUESTIONS.take(ChallengeHasher.QUESTION_COUNT).toTypedArray()) }
    val answers = remember { mutableStateListOf(*Array(ChallengeHasher.QUESTION_COUNT) { "" }) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Challenge questions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Edit the questions if you like. Answers are not case-sensitive.", style = MaterialTheme.typography.bodySmall)
                for (i in 0 until ChallengeHasher.QUESTION_COUNT) {
                    OutlinedTextField(
                        value = questions[i],
                        onValueChange = { if (it.length <= ChallengeHasher.MAX_QUESTION_CHARS) questions[i] = it },
                        label = { Text("Question ${i + 1}") },
                        modifier = Modifier.fillMaxWidth().testTag("question-$i"),
                    )
                    OutlinedTextField(
                        value = answers[i],
                        onValueChange = { answers[i] = it },
                        label = { Text("Answer ${i + 1}") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("answer-$i"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(questions.zip(answers)) },
                enabled = answers.all { ChallengeHasher.isAcceptableAnswer(it) },
                modifier = Modifier.testTag("save-challenges"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
