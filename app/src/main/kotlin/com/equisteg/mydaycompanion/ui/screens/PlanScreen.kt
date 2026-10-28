package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.ui.MainViewModel
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
fun PlanScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val statusById = tasks.associate { it.id to it.status }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val dayTasks = tasks.filter { it.date == state.selectedDate }.sortedBy { it.createdAt }
    val backlog = tasks.filter { it.date == null }.sortedBy { it.createdAt }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.padding(top = 8.dp))
            Text("What do you need to do?", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth().testTag("input"),
                placeholder = { Text("tmrw gym 6am, call Raj around 4ish, dentist fri 3-4pm, finish report by eod") },
                minLines = 2,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::onSubmit,
                    enabled = !state.busy && state.input.isNotBlank(),
                    modifier = Modifier.testTag("submit"),
                ) { Text("Plan it") }
                VoiceInputButton(enabled = !state.busy, onText = viewModel::onVoiceText, onMessage = viewModel::onInfo)
            }
            state.message?.let { Text(it, modifier = Modifier.padding(top = 8.dp).testTag("message")) }
        }

        state.proposal?.let { proposal ->
            item {
                Card(Modifier.fillMaxWidth().testTag("proposal")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        proposal.previewPlans.forEach { preview ->
                            Text("Proposed plan for ${preview.date.format(dayFormat)}", style = MaterialTheme.typography.titleMedium)
                            PlanBlocks(preview, statusById = emptyMap(), onToggle = null, tag = "preview-${preview.date}")
                        }
                        proposal.warnings.forEach { Text("ℹ $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Nothing is saved until you confirm.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::onAcceptProposal, enabled = !state.busy, modifier = Modifier.testTag("accept")) {
                                Text(if (proposal.previewPlans.size > 1) "Add to my days" else "Add to my day")
                            }
                            OutlinedButton(onClick = viewModel::onDiscardProposal) { Text("Discard") }
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.selectedDate.format(dayFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.selectDate(state.selectedDate.minusDays(1)) }) { Text("‹") }
                TextButton(onClick = { viewModel.selectDate(state.selectedDate.plusDays(1)) }) { Text("›") }
                TextButton(onClick = viewModel::onReplan, enabled = !state.busy) { Text("Replan") }
                TextButton(onClick = viewModel::onReadMyDay, enabled = !state.busy, modifier = Modifier.testTag("read-day")) { Text("Read") }
            }
        }

        val saved = plan
        if (saved == null || saved.blocks.isEmpty()) {
            item { Text("No plan for this day yet.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item { PlanBlocks(saved, statusById, onToggle = viewModel::onToggleTask, tag = "plan-${saved.date}", onEdit = { editingId = it }) }
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

@Composable
private fun PlanBlocks(plan: DayPlan, statusById: Map<String, TaskStatus>, onToggle: ((String) -> Unit)?, tag: String, onEdit: ((String) -> Unit)? = null) {
    Column(Modifier.testTag(tag)) {
        plan.blocks.forEach { block ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${block.start}–${block.end}", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (block.kind == BlockKind.FIXED_EVENT) "📌 ${block.title}" else block.title,
                    modifier = Modifier.weight(1f).then(
                        if (onEdit != null && block.kind == BlockKind.TASK) Modifier.clickable { onEdit(block.refId) } else Modifier,
                    ),
                )
                if (onToggle != null && block.kind == BlockKind.TASK) {
                    Checkbox(checked = statusById[block.refId] == TaskStatus.DONE, onCheckedChange = { onToggle(block.refId) })
                }
            }
        }
        plan.issues.forEach { Text("⚠ ${it.message}", style = MaterialTheme.typography.bodySmall) }
    }
}
