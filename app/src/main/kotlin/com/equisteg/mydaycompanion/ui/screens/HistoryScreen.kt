package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault())

/** The local audit log: every action the assistant or the user took, and what the policy decided. */
@Composable
fun HistoryScreen(entries: List<AuditEntry>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("History", style = MaterialTheme.typography.titleLarge) }
        if (entries.isEmpty()) item { Text("Nothing has happened yet.") }
        items(entries, key = { it.id }) { e ->
            Column {
                Text("${timeFormat.format(e.timestamp)}  ${e.action}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(e.actor.name.lowercase(), e.riskLevel?.name, e.decision.name.lowercase(), e.outcome.name.lowercase(), e.reasonCode)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
