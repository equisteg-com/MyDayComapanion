package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.audit.AuditDecision
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.audit.AuditOutcome
import com.equisteg.mydaycompanion.domain.audit.HistoryFilter
import com.equisteg.mydaycompanion.domain.audit.HistoryRetention
import com.equisteg.mydaycompanion.ui.UiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault())

/** Plain names for tool ids; anything not listed shows its id. */
private val ACTION_NAMES = mapOf(
    "ai.extract" to "Understood your message",
    "tasks.create" to "Saved tasks",
    "tasks.update" to "Edited a task",
    "tasks.delete" to "Deleted a task",
    "tasks.complete" to "Ticked a task done",
    "tasks.reopen" to "Reopened a task",
    "tasks.addSubtask" to "Added a step",
    "plan.generate" to "Planned the day",
    "settings.update" to "Changed your day settings",
    "reminders.sync" to "Set reminders",
    "routines.create" to "Saved a routine",
    "routines.update" to "Changed a routine",
    "routines.delete" to "Deleted a routine",
    "routines.materialize" to "Added routine tasks",
    "device.setAlarm" to "Set an alarm",
    "device.dial" to "Opened the dialler",
    "device.composeMessage" to "Drafted a message",
    "device.navigate" to "Opened directions",
    "device.calendarInsert" to "Opened the calendar",
    "voice.speak" to "Read aloud",
    "memory.propose" to "Suggested a memory",
    "memory.add" to "Remembered something you said",
    "memory.activate" to "Kept a memory",
    "memory.reject" to "Turned down a memory",
    "memory.delete" to "Forgot a memory",
    "memory.purgeExpired" to "Removed expired memories",
    "history.prune" to "Removed old history",
    "data.export" to "Exported your data",
    "data.deleteAll" to "Deleted everything",
    "security.updateSettings" to "Changed security settings",
    "security.setChallenges" to "Changed challenge questions",
    "tasks.rollOver" to "Moved unfinished tasks",
    "settings.privacy" to "Changed a privacy switch",
    "unknown" to "Blocked an unknown action",
)

/** The local audit log (F16): every action, who started it, its risk, what the policy decided and what happened. */
@Composable
fun HistoryScreen(state: UiState, onFilter: (HistoryFilter) -> Unit, onKeepDays: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shown = state.shownHistory
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("History", style = MaterialTheme.typography.titleLarge)
            Text("Everything you and your agent did on this phone. It never contains what your tasks or memories say.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.historyFilter == f,
                        onClick = { onFilter(f) },
                        label = { Text(f.label) },
                        modifier = Modifier.testTag("filter-${f.name}"),
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item { Text(if (state.historyFilter == HistoryFilter.ALL) "Nothing has happened yet." else "Nothing matches this filter.", modifier = Modifier.testTag("history-empty")) }
        }
        items(shown, key = { it.id }) { e -> HistoryRow(e, fromBackup = state.restoredAt?.let { e.timestamp < it } == true) }
        item {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Keep history for", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HistoryRetention.CHOICES_DAYS.forEach { d ->
                        FilterChip(
                            selected = state.historyKeepDays == d,
                            onClick = { if (state.historyKeepDays != d) onKeepDays(d) },
                            label = { Text(if (d == 365) "1 year" else "$d days") },
                            enabled = !state.busy,
                            modifier = Modifier.testTag("keep-days-$d"),
                        )
                    }
                }
                Text("Older entries are removed each day. A shorter period removes them now.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryRow(e: AuditEntry, fromBackup: Boolean) {
    val problem = e.decision == AuditDecision.DENIED || e.outcome == AuditOutcome.FAILURE || e.outcome == AuditOutcome.VERIFICATION_FAILED
    Column(Modifier.testTag("history-${e.id}")) {
        Text(
            "${timeFormat.format(e.timestamp)}  ${ACTION_NAMES[e.action] ?: e.action}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            listOfNotNull(
                when (e.actor.name) { "USER" -> "you"; "AGENT" -> "assistant"; else -> "automatic" },
                e.riskLevel?.name,
                e.decision.name.lowercase().replace('_', ' '),
                e.outcome.name.lowercase().replace('_', ' '),
                e.reasonCode?.lowercase()?.replace('_', ' '),
                // Entries older than the last restore came in with the backup (OQ-24 c).
                if (fromBackup) "from backup" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
