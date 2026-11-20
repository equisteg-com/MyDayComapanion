package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.productivity.BatchEditAction
import com.equisteg.mydaycompanion.domain.productivity.BatchEditPlanner
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
    onBatch: ((Set<String>, BatchEditAction) -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openRequest) {
        if (openRequest > 0) expanded = true
    }
    var query by rememberSaveable { mutableStateOf("") }
    var statusName by rememberSaveable { mutableStateOf(TaskStateFilter.ALL.name) }
    var priorityName by rememberSaveable { mutableStateOf("ANY") }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingBatch by remember { mutableStateOf<BatchEditAction?>(null) }

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
                if (onBatch != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (selectionMode) "${selectedIds.size} selected · max ${BatchEditPlanner.MAX_TASKS}"
                            else "Batch tools for one-off, non-private tasks",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                selectionMode = !selectionMode
                                selectedIds = emptyList()
                            },
                            modifier = Modifier.testTag("batch-mode"),
                        ) { Text(if (selectionMode) "Done selecting" else "Select tasks") }
                    }
                    if (selectionMode && selectedIds.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BatchEditAction.entries.forEach { action ->
                                OutlinedButton(onClick = { pendingBatch = action },
                                    modifier = Modifier.testTag("batch-action-${action.name}")) {
                                    Text(action.label)
                                }
                            }
                        }
                    }
                }
                if (results.items.isEmpty()) {
                    Text("Nothing matches. Try another word or remove a filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("task-search-empty"))
                } else if (selectionMode && onBatch != null) {
                    val protectedChildren = allTasks.filter { it.personal && it.parentId != null }
                        .map { it.parentId }.toSet()
                    results.items.forEach { task ->
                        val editable = task.parentId == null && task.routineId == null &&
                            !task.personal && task.status != TaskStatus.CANCELLED &&
                            task.id !in protectedChildren
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = task.id in selectedIds,
                                enabled = editable && (task.id in selectedIds ||
                                    selectedIds.size < BatchEditPlanner.MAX_TASKS),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + task.id else selectedIds - task.id
                                },
                                modifier = Modifier.testTag("batch-select-${task.id}"),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.bodyMedium)
                                Text(if (editable) "${task.status.name.lowercase()} · ${task.priority.name.lowercase()}"
                                    else "Individual review required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onEdit(task.id) }) { Text("Edit") }
                        }
                    }
                } else {
                    TaskRows(results.items, onToggle, onEdit)
                }
            }
        }
    }
    if (pendingBatch != null && onBatch != null) {
        val action = pendingBatch!!
        AlertDialog(
            onDismissRequest = { pendingBatch = null },
            title = { Text("${action.label} · ${selectedIds.size} tasks?") },
            text = {
                Text("Only non-private, one-off tasks can change in bulk. Each change is audited. " +
                    "If a task changed or a write fails, remaining edits stop. Moving dates also updates reminders.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedIds.toSet()
                        pendingBatch = null
                        onBatch(ids, action)
                        selectedIds = emptyList()
                        selectionMode = false
                    },
                    modifier = Modifier.testTag("batch-confirm"),
                ) { Text("Apply to ${selectedIds.size}") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatch = null }) { Text("Cancel") }
            },
        )
    }
}
