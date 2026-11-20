package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.productivity.TaskSearch
import com.equisteg.mydaycompanion.domain.productivity.TaskStateFilter

/**
 * Search is device-only; no network call, telemetry or additional permission.
 * This panel is collapsed by default so existing day-planning interactions remain fast.
 */
@Composable
fun TaskDiscovery(
    allTasks: List<Task>,
    onToggle: (String) -> Unit,
    onEdit: (String) -> Unit,
    openRequest: Int = 0,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openRequest) {
        if (openRequest > 0) expanded = true
    }
    var query by rememberSaveable { mutableStateOf("") }
    var statusName by rememberSaveable { mutableStateOf(TaskStateFilter.ALL.name) }
    var priorityName by rememberSaveable { mutableStateOf("ANY") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("task-discovery"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Find your tasks", style = MaterialTheme.typography.titleMedium)
                    Text("Search tasks and notes on this device.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("task-search-toggle"),
                ) { Text(if (expanded) "Close" else "Search") }
            }

            if (expanded) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(TaskSearch.MAX_QUERY_CHARS) },
                    label = { Text("Search tasks or notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task-search-input"),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TaskStateFilter.entries.forEach { option ->
                        FilterChip(
                            selected = statusName == option.name,
                            onClick = { statusName = option.name },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.testTag("task-status-${option.name}"),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("ANY") .plus(Priority.entries.map { it.name }).forEach { option ->
                        FilterChip(
                            selected = priorityName == option,
                            onClick = { priorityName = option },
                            label = { Text(if (option == "ANY") "Any priority" else option.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.testTag("task-priority-$option"),
                        )
                    }
                }
                val status = TaskStateFilter.valueOf(statusName)
                val priority = Priority.entries.firstOrNull { it.name == priorityName }
                val results = remember(allTasks, query, status, priority) {
                    TaskSearch.find(allTasks, query, status, priority)
                }
                Text(
                    if (results.hasMore) "Showing newest ${results.items.size} of ${results.totalMatches} matching tasks"
                    else "${results.totalMatches} matching tasks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("task-search-count"),
                )
                if (results.items.isEmpty()) {
                    Text("Nothing matches. Try another word or remove a filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("task-search-empty"))
                } else {
                    TaskRows(results.items, onToggle, onEdit)
                }
            }
        }
    }
}
