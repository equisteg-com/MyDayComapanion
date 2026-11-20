package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.productivity.CaptureTemplates
import com.equisteg.mydaycompanion.domain.productivity.ProductivityCoach
import java.time.LocalDate

/** Starter prompts are editable: tapping one NEVER creates or approves a task. */
@Composable
fun CaptureTemplateGallery(
    enabled: Boolean,
    onChoose: (String) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf("All") }
    val categories = remember { listOf("All") + CaptureTemplates.builtIn.map { it.group }.distinct() }
    Column(Modifier.fillMaxWidth().testTag("capture-templates"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Start with a template", style = MaterialTheme.typography.titleSmall)
        Text("Tap a sample to edit it in the box above. Nothing is saved until you review and approve.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { group ->
                FilterChip(
                    selected = category == group,
                    onClick = { category = group },
                    label = { Text(group) },
                    modifier = Modifier.testTag("template-group-${group.lowercase()}"),
                )
            }
        }
        val shown = remember(category) {
            CaptureTemplates.builtIn.filter { category == "All" || it.group == category }
        }
        shown.forEach { item ->
            OutlinedButton(
                onClick = { onChoose(item.prompt) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("template-${item.id}"),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(item.label, style = MaterialTheme.typography.labelLarge)
                    Text(item.prompt, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Actionable report from stored local tasks. No analytics SDK, account or internet permission. */
@Composable
fun ActionableProgressCard(
    tasks: List<Task>,
    selectedDate: LocalDate,
    busy: Boolean,
    onDeadlines: () -> Unit,
    onRollOver: () -> Unit,
    onFocus: () -> Unit,
    onPlanNextDay: () -> Unit,
) {
    val report = remember(tasks, selectedDate) { ProductivityCoach.report(tasks, selectedDate) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("actionable-progress"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Actionable progress", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${report.completed}/${report.scheduled}", style = MaterialTheme.typography.headlineMedium)
                    Text("done on this day", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("${report.remainingMinutes / 60}h ${report.remainingMinutes % 60}m",
                        style = MaterialTheme.typography.headlineMedium)
                    Text("remaining work", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(report.recommendation, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("progress-coaching"))
            val flags = listOfNotNull(
                report.overdue.takeIf { it > 0 }?.let { "$it overdue" },
                report.dueSoon.takeIf { it > 0 }?.let { "$it due soon" },
                report.backlog.takeIf { it > 0 }?.let { "$it unscheduled" },
            )
            if (flags.isNotEmpty()) {
                Text(flags.joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report.overdue > 0) {
                    AssistChip(
                        onClick = onRollOver, enabled = !busy,
                        label = { Text("Review overdue") },
                        modifier = Modifier.testTag("coach-overdue"),
                    )
                }
                if (report.dueSoon > 0) {
                    AssistChip(
                        onClick = onDeadlines, enabled = !busy,
                        label = { Text("See deadlines") },
                        modifier = Modifier.testTag("coach-deadlines"),
                    )
                }
                AssistChip(
                    onClick = onFocus, enabled = !busy,
                    label = { Text("Focus 25 min") },
                    modifier = Modifier.testTag("coach-focus"),
                )
                AssistChip(
                    onClick = onPlanNextDay, enabled = !busy,
                    label = { Text("Plan tomorrow") },
                    modifier = Modifier.testTag("coach-next-day"),
                )
            }
        }
    }
}
