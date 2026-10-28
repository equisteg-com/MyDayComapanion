package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equisteg.mydaycompanion.domain.insights.DayInsights
import com.equisteg.mydaycompanion.ui.MainViewModel
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE")

@Composable
fun PlanScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val statusById = tasks.associate { it.id to it.status }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val dayTasks = tasks.filter { it.date == state.selectedDate }.sortedBy { it.createdAt }
    val backlog = tasks.filter { it.date == null }.sortedBy { it.createdAt }
    val today = viewModel.today()
    val now = viewModel.nowTime()
    val isToday = state.selectedDate == today
    val insights = remember(tasks, today) { DayInsights.of(tasks, today) }
    val dayCount = remember(tasks, state.selectedDate) { DayInsights.of(tasks, state.selectedDate).today }
    // Keeps the last proposal on screen while its card animates away.
    var shownProposal by remember { mutableStateOf(state.proposal) }
    if (state.proposal != null) shownProposal = state.proposal

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isToday) greeting(now) else state.selectedDate.format(weekdayFormat),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("day-title"),
                    )
                    Text(
                        if (isToday) "Today · ${state.selectedDate.format(dayFormat)}" else state.selectedDate.format(dayFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.selectDate(state.selectedDate.minusDays(1)) }, modifier = Modifier.testTag("day-previous")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                if (!isToday) {
                    AssistChip(onClick = { viewModel.selectDate(today) }, label = { Text("Today") }, modifier = Modifier.testTag("day-today"))
                }
                IconButton(onClick = { viewModel.selectDate(state.selectedDate.plusDays(1)) }, modifier = Modifier.testTag("day-next")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("What do you need to do?", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::onInputChange,
                        modifier = Modifier.fillMaxWidth().testTag("input"),
                        placeholder = { Text("tmrw gym 6am, call Raj around 4ish, dentist fri 3-4pm, finish report by eod") },
                        minLines = 2,
                        shape = MaterialTheme.shapes.medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = viewModel::onSubmit,
                            enabled = !state.busy && state.input.isNotBlank(),
                            modifier = Modifier.testTag("submit"),
                        ) { Text("Plan it") }
                        if (viewModel.voiceEnabled) VoiceInputButton(enabled = !state.busy, onText = viewModel::onVoiceText, onMessage = viewModel::onInfo)
                    }
                    state.message?.let {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(it, modifier = Modifier.padding(12.dp).testTag("message"), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = state.proposal != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                shownProposal?.let { proposal ->
                    Card(
                        Modifier.fillMaxWidth().testTag("proposal"),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            proposal.previewPlans.forEach { preview ->
                                Text("Proposed plan for ${preview.date.format(dayFormat)}", style = MaterialTheme.typography.titleMedium)
                                Timeline(preview, statusById = emptyMap(), onToggle = null, tag = "preview-${preview.date}")
                            }
                            proposal.warnings.forEach { Text("ℹ $it", style = MaterialTheme.typography.bodySmall) }
                            Text("Nothing is saved until you confirm.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::onAcceptProposal, enabled = !state.busy && state.proposal != null, modifier = Modifier.testTag("accept")) {
                                    Text(if (proposal.previewPlans.size > 1) "Add to my days" else "Add to my day")
                                }
                                OutlinedButton(onClick = viewModel::onDiscardProposal, enabled = state.proposal != null) { Text("Discard") }
                            }
                        }
                    }
                }
            }
        }

        item {
            DayHero(dayCount, if (isToday) DayInsights.nowNext(plan, tasks, now) else null)
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Plan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::onReplan, enabled = !state.busy) { Text("Replan") }
                TextButton(onClick = viewModel::onReadMyDay, enabled = !state.busy, modifier = Modifier.testTag("read-day")) { Text("Read") }
            }
        }

        val saved = plan
        if (saved == null || saved.blocks.isEmpty()) {
            item {
                Text(
                    "No plan for this day yet. Type or speak what you need to do, and I'll fit it around your day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Timeline(saved, statusById, onToggle = viewModel::onToggleTask, tag = "plan-${saved.date}", now = now.takeIf { isToday }, onEdit = { editingId = it })
            }
        }

        if (dayTasks.isNotEmpty()) {
            item {
                Text("Tasks", style = MaterialTheme.typography.titleMedium)
                Text("Tap a task to edit it, add steps or notes, or mark it personal.", style = MaterialTheme.typography.bodySmall)
                TaskRows(dayTasks, viewModel::onToggleTask) { editingId = it }
            }
        }
        if (backlog.isNotEmpty()) {
            item {
                Text("No day yet", style = MaterialTheme.typography.titleMedium)
                TaskRows(backlog, viewModel::onToggleTask) { editingId = it }
            }
        }

        item { InsightsCard(insights) }
    }

    tasks.firstOrNull { it.id == editingId }?.let { task ->
        TaskEditDialog(
            task = task,
            subtasks = tasks.filter { it.parentId == task.id },
            busy = state.busy,
            onSave = { viewModel.onSaveTask(it); editingId = null },
            onDelete = { viewModel.onDeleteTask(task.id); editingId = null },
            onAddSubtask = { title, minutes -> viewModel.onAddSubtask(task.id, title, minutes) },
            onDismiss = { editingId = null },
            onDeviceAction = { id, args, title -> viewModel.onDeviceAction(id, args, title); editingId = null },
        )
    }
}
