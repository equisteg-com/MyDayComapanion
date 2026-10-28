package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.ui.AgentSuggestion
import com.equisteg.mydaycompanion.ui.ChatTurn
import com.equisteg.mydaycompanion.ui.FocusSession
import com.equisteg.mydaycompanion.ui.TaskChoice
import java.util.Locale

/** Quick questions under the input; each one is sent as if typed. */
val QUICK_ASKS = listOf("What's next?", "What's left?", "Roll over", "Focus 25 min")

/** The last lines of the chat: the owner's on the right, the agent's on the left. In memory only. */
@Composable
fun ChatBubbles(chat: List<ChatTurn>, modifier: Modifier = Modifier) {
    if (chat.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag("chat").animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chat.takeLast(SHOWN).forEach { turn ->
            val colors = MaterialTheme.colorScheme
            Box(Modifier.fillMaxWidth(), contentAlignment = if (turn.fromOwner) Alignment.CenterEnd else Alignment.CenterStart) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (turn.fromOwner) 18.dp else 4.dp,
                        bottomEnd = if (turn.fromOwner) 4.dp else 18.dp,
                    ),
                    color = if (turn.fromOwner) colors.primaryContainer else colors.secondaryContainer,
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Text(
                        turn.text,
                        color = if (turn.fromOwner) colors.onPrimaryContainer else colors.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag(if (turn.fromOwner) "chat-owner" else "chat-agent")
                            .then(if (turn.fromOwner) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite }),
                    )
                }
            }
        }
    }
}

private const val SHOWN = 6

/** The quick-question chips; placed in the same scrolling row as Plan it and Speak. */
@Composable
fun QuickAsks(enabled: Boolean, onAsk: (String) -> Unit) {
    QUICK_ASKS.forEachIndexed { i, ask ->
        SuggestionChip(onClick = { onAsk(ask) }, label = { Text(ask) }, enabled = enabled, modifier = Modifier.testTag("ask-$i"))
    }
}

/** What the agent offers to do; the button is the owner's approval for exactly this. */
@Composable
fun SuggestionCard(suggestion: AgentSuggestion, busy: Boolean, onAccept: () -> Unit, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().testTag("suggestion"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(suggestion.text, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, enabled = !busy, modifier = Modifier.testTag("suggestion-accept")) { Text(suggestion.actionLabel) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("suggestion-dismiss")) { Text("No thanks") }
            }
        }
    }
}

@Composable
fun ChoiceCard(choice: TaskChoice, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("choice"), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(choice.text, style = MaterialTheme.typography.titleMedium)
            choice.options.forEach { t: Task ->
                TextButton(onClick = { onChoose(t.id) }, modifier = Modifier.testTag("choice-${t.id}")) {
                    Text(listOfNotNull(t.title, t.date?.let { "· $it" }).joinToString(" "))
                }
            }
            TextButton(onClick = onDismiss) { Text("None of these") }
        }
    }
}

/** The running focus session: time left, what it's for, and Stop. */
@Composable
fun FocusCard(focus: FocusSession, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().testTag("focus"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.secondary, contentColor = colors.onSecondary),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Focus", style = MaterialTheme.typography.labelLarge)
                Text(
                    String.format(Locale.ROOT, "%d:%02d", focus.remainingSeconds / 60, focus.remainingSeconds % 60),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.testTag("focus-left"),
                )
                focus.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.testTag("focus-stop")) { Text("Stop", color = colors.onSecondary) }
        }
    }
}

/** Offered on today when earlier days left tasks unfinished. */
@Composable
fun RollOverBanner(count: Int, busy: Boolean, onMove: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().testTag("rollover")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count == 1) "1 unfinished task from an earlier day" else "$count unfinished tasks from earlier days",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onMove, enabled = !busy, modifier = Modifier.testTag("rollover-accept")) { Text("Review") }
        }
    }
}
