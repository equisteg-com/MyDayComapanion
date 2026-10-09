package com.equisteg.mydaycompanion.ui.screens

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
            Button(
                onClick = viewModel::onSubmit,
                enabled = !state.busy && state.input.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp).testTag("submit"),
            ) { Text("Plan it") }
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
            }
        }

        val saved = plan
        if (saved == null || saved.blocks.isEmpty()) {
            item { Text("No plan for this day yet.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item { PlanBlocks(saved, statusById, onToggle = viewModel::onCompleteTask, tag = "plan-${saved.date}") }
        }
    }
}

@Composable
private fun PlanBlocks(plan: DayPlan, statusById: Map<String, TaskStatus>, onToggle: ((String) -> Unit)?, tag: String) {
    Column(Modifier.testTag(tag)) {
        plan.blocks.forEach { block ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${block.start}–${block.end}", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (block.kind == BlockKind.FIXED_EVENT) "📌 ${block.title}" else block.title,
                    modifier = Modifier.weight(1f),
                )
                if (onToggle != null && block.kind == BlockKind.TASK) {
                    Checkbox(checked = statusById[block.refId] == TaskStatus.DONE, onCheckedChange = { onToggle(block.refId) })
                }
            }
        }
        plan.issues.forEach { Text("⚠ ${it.message}", style = MaterialTheme.typography.bodySmall) }
    }
}
