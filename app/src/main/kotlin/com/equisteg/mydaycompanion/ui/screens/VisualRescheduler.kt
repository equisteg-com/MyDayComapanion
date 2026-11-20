package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.productivity.TimeShift
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Drag a flexible task card vertically to propose a new start; the up/down controls are
 * equivalent for TalkBack, keyboard, and people who cannot use gestures.
 * Time is a PREFERENCE, not a reservation: fixed calendar events and deadlines still win.
 */
@Composable
fun VisualRescheduler(
    plan: DayPlan,
    tasks: List<Task>,
    preferences: UserPreferences,
    busy: Boolean,
    onMove: (Task, Task) -> Unit,
) {
    val byId = remember(tasks) { tasks.associateBy { it.id } }
    val candidates = plan.blocks.asSequence().filter { it.kind == BlockKind.TASK }
        .mapNotNull { block ->
            val task = byId[block.refId] ?: return@mapNotNull null
            if (task.status != TaskStatus.OPEN || task.personal || task.routineId != null ||
                task.date != plan.date) null else task to block.start
        }.take(10).toList()
    if (candidates.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth().testTag("visual-rescheduler"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Visual rescheduler", style = MaterialTheme.typography.titleMedium)
            Text("Long-press and drag a flexible task up or down, or use the −/+ controls to shift " +
                "15 minutes. Actual placement may change around meetings, deadlines and buffers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((task, scheduled) in candidates) {
                TimeDragRow(
                    task = task, scheduled = scheduled, preferences = preferences,
                    busy = busy, onMoved = { updated -> onMove(task, updated) },
                )
            }
            if (plan.blocks.count { it.kind == BlockKind.TASK } > candidates.size) {
                Text("Showing ${candidates.size} movable blocks. Open a task to edit any other time.",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (plan.issues.isNotEmpty()) {
                Text("${plan.issues.size} scheduling warning(s): review your timeline after moving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("reschedule-conflicts"))
            }
        }
    }
}

@Composable
private fun TimeDragRow(
    task: Task,
    scheduled: LocalTime,
    preferences: UserPreferences,
    busy: Boolean,
    onMoved: (Task) -> Unit,
) {
    val density = LocalDensity.current
    val pixelsPerStep = with(density) { 44.dp.toPx() }
    var dragDy by remember(task.id, scheduled) { mutableFloatStateOf(0f) }
    var dragging by remember(task.id, scheduled) { mutableStateOf(false) }

    fun edit(steps: Int) {
        if (busy) return
        TimeShift.change(task, scheduled, steps, preferences.dayStart, preferences.dayEnd)
            .getOrNull()?.let(onMoved)
    }
    val prospectiveSteps = (dragDy / pixelsPerStep).roundToInt()
    val preview = if (dragging && prospectiveSteps != 0) {
        TimeShift.change(task, scheduled, prospectiveSteps.coerceIn(-48, 48),
            preferences.dayStart, preferences.dayEnd).getOrNull()?.preferredStart
    } else null

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .testTag("time-drag-${task.id}")
            .pointerInput(task.id, scheduled, preferences.dayStart, preferences.dayEnd, busy) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; dragDy = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDy += amount.y
                    },
                    onDragEnd = {
                        val steps = (dragDy / pixelsPerStep).roundToInt().coerceIn(-48, 48)
                        dragging = false
                        dragDy = 0f
                        if (steps != 0) edit(steps)
                    },
                    onDragCancel = { dragging = false; dragDy = 0f },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text("Scheduled ${scheduled} · ${preview?.let { "drop at $it" } ?: "hold + drag"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("time-preview-${task.id}"))
        }
        TextButton(
            onClick = { edit(-1) },
            enabled = !busy && TimeShift.change(task, scheduled, -1,
                preferences.dayStart, preferences.dayEnd).isSuccess,
            modifier = Modifier.testTag("shift-earlier-${task.id}"),
        ) { Text("−15m") }
        TextButton(
            onClick = { edit(1) },
            enabled = !busy && TimeShift.change(task, scheduled, 1,
                preferences.dayStart, preferences.dayEnd).isSuccess,
            modifier = Modifier.testTag("shift-later-${task.id}"),
        ) { Text("+15m") }
    }
}
