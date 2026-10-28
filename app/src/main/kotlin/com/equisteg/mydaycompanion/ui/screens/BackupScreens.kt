package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.equisteg.mydaycompanion.domain.backup.BackupCodec
import com.equisteg.mydaycompanion.domain.backup.PassphraseStrength
import com.equisteg.mydaycompanion.ui.BackupPrompt
import com.equisteg.mydaycompanion.ui.UiState

/** Settings → Your data: back up to a file, or restore from one (build doc §7.2, §7.3). */
@Composable
fun BackupSection(state: UiState, onBackUp: () -> Unit, onRestore: () -> Unit) {
    Text(
        "A backup is one encrypted file you keep, for example on a USB stick or your computer. " +
            "Use it to move to a new phone. Nothing is uploaded.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBackUp, enabled = !state.busy, modifier = Modifier.testTag("backup-create")) { Text("Back up") }
        OutlinedButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.testTag("backup-restore")) { Text("Restore") }
    }
}

/** The passphrase dialog; secure so the passphrase can't be captured in a screenshot or the recent-apps preview. */
@Composable
fun BackupPassphraseDialog(
    prompt: BackupPrompt,
    busy: Boolean,
    onCreate: (CharArray, CharArray) -> Unit,
    onRestore: (CharArray) -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            BackupPassphraseForm(prompt, busy, onCreate, onRestore, onCancel, Modifier.padding(24.dp))
        }
    }
}

@Composable
fun BackupPassphraseForm(
    prompt: BackupPrompt,
    busy: Boolean,
    onCreate: (CharArray, CharArray) -> Unit,
    onRestore: (CharArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val creating = prompt.kind == BackupPrompt.Kind.CREATE
    var passphrase by remember(prompt.kind) { mutableStateOf("") }
    var repeat by remember(prompt.kind) { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (creating) "Choose a backup passphrase" else "Open your backup", style = MaterialTheme.typography.titleLarge)
        Text(
            if (creating) {
                "At least ${BackupCodec.MIN_PASSPHRASE_CHARS} characters. A few unrelated words work well. " +
                    "Nobody can open the backup without it, and it can't be reset or recovered, not even by Equisteg."
            } else {
                "Type the passphrase you chose when you made this backup."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("backup-passphrase"),
        )
        if (creating) {
            Text(
                "Strength: ${PassphraseStrength.of(passphrase).label}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("backup-strength"),
            )
            OutlinedTextField(
                value = repeat,
                onValueChange = { repeat = it },
                label = { Text("Type it again") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("backup-passphrase-repeat"),
            )
        }
        prompt.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("backup-error"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(
                onClick = {
                    if (creating) onCreate(passphrase.toCharArray(), repeat.toCharArray()) else onRestore(passphrase.toCharArray())
                    passphrase = ""
                    repeat = ""
                },
                enabled = !busy && passphrase.isNotEmpty(),
                modifier = Modifier.testTag("backup-passphrase-submit"),
            ) { Text(if (creating) "Continue" else "Open") }
        }
    }
}
