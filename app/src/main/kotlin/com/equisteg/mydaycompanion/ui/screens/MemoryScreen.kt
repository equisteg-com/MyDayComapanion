package com.equisteg.mydaycompanion.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemorySensitivity
import com.equisteg.mydaycompanion.domain.memory.MemorySource
import com.equisteg.mydaycompanion.ui.MainViewModel

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

/**
 * What the agent remembers about the owner (F9). Suggestions wait here until the owner keeps them
 * or says no; nothing is used until then. Kept memories help Gemini Nano read the owner's words;
 * private ones are kept but never given to the model.
 */
@Composable
fun MemoryScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var private by rememberSaveable { mutableStateOf(false) }
    // Leaving the Memory tab hides private memories again.
    DisposableEffect(Unit) { onDispose { viewModel.onHidePrivate() } }

    LazyColumn(modifier.fillMaxSize().padding(16.dp).testTag("memory-screen"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Memory", style = MaterialTheme.typography.titleLarge)
            Text(
                "What your agent knows about how you like your day. It only uses what you keep here, and you can make it forget anything.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.message?.let { msg -> item { Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("memory-message")) } }

        if (state.proposedMemories.isNotEmpty()) {
            item { Text("Suggestions", style = MaterialTheme.typography.titleMedium) }
            items(state.proposedMemories, key = { it.id }) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().testTag("suggestion-${m.id}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.content, style = MaterialTheme.typography.bodyLarge)
                        Text("Noticed from your finished tasks. Not used unless you keep it.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.onKeepMemory(m.id) }, enabled = !state.busy && viewModel.memoryEnabled, modifier = Modifier.testTag("keep-${m.id}")) { Text("Keep") }
                            OutlinedButton(onClick = { viewModel.onRejectMemory(m.id) }, enabled = !state.busy, modifier = Modifier.testTag("reject-${m.id}")) { Text("No thanks") }
                        }
                    }
                }
            }
        }

        val hasPrivate = state.keptMemories.any { it.sensitivity == MemorySensitivity.SENSITIVE }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remembered", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // Private memories stay hidden from whoever has the app open until the owner verifies (OQ-22 a).
                if (hasPrivate) {
                    if (state.privateShown) {
                        TextButton(onClick = viewModel::onHidePrivate, modifier = Modifier.testTag("hide-private")) { Text("Hide private") }
                    } else {
                        TextButton(onClick = viewModel::onShowPrivate, enabled = !state.busy, modifier = Modifier.testTag("show-private")) { Text("Show private") }
                    }
                }
            }
        }
        if (state.keptMemories.isEmpty()) {
            item { Text("Nothing yet. Add something below, or keep a suggestion.", style = MaterialTheme.typography.bodySmall) }
        }
        items(state.keptMemories, key = { it.id }) { m ->
            KeptMemory(m, hidden = m.sensitivity == MemorySensitivity.SENSITIVE && !state.privateShown, busy = state.busy) { viewModel.onDeleteMemory(m.id) }
        }

        if (viewModel.memoryEnabled) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tell your agent", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= MAX_CHARS) draft = it },
                        label = { Text("For example: I work from home on Fridays") },
                        modifier = Modifier.fillMaxWidth().testTag("memory-input"),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = private, onCheckedChange = { private = it }, modifier = Modifier.testTag("memory-private"))
                        Column(Modifier.weight(1f)) {
                            Text("Keep private")
                            Text("Stored encrypted but never given to the model. Forgetting it needs your verification.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick = { viewModel.onAddMemory(draft, private); draft = ""; private = false },
                        enabled = !state.busy && draft.isNotBlank(),
                        modifier = Modifier.testTag("memory-save"),
                    ) { Text("Remember") }
                }
            }
        } else {
            item { Text("New memories are switched off in this version. You can still delete what is stored.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun KeptMemory(m: MemoryItem, hidden: Boolean, busy: Boolean, onForget: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("memory-${m.id}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (hidden) {
                Text("Private memory", fontStyle = FontStyle.Italic, modifier = Modifier.testTag("memory-hidden-${m.id}"))
            } else {
                Text(m.content)
            }
            Text(
                listOfNotNull(
                    if (m.source == MemorySource.USER_EXPLICIT) "You told it" else "You kept a suggestion",
                    if (m.sensitivity == MemorySensitivity.SENSITIVE) "private, not given to the model" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onForget, enabled = !busy, modifier = Modifier.testTag("forget-${m.id}")) { Text("Forget") }
    }
}

private const val MAX_CHARS = 280
