package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalDate

/** Keeps deadlines on the moved day, retaining their local clock time. */
internal fun moveToNextDay(task: Task, selectedDate: LocalDate): Task {
    require(task.date == selectedDate && task.status == TaskStatus.OPEN &&
        task.parentId == null && task.routineId == null)
    return task.copy(
        date = selectedDate.plusDays(1),
        deadline = task.deadline?.plusDays(1),
    )
}

internal data class ReversibleMove(val original: Task, val moved: Task) {
    fun canUndo(current: Task?): Boolean =
        current != null && current.copy(updatedAt = original.updatedAt) ==
            moved.copy(updatedAt = original.updatedAt)
}

/** An accessible alternative to drag-only rescheduling, with safe one-step undo. */
@Composable
fun QuickReschedule(
    day: LocalDate,
    tasks: List<Task>,
    canUndo: Boolean,
    busy: Boolean,
    onMove: (Task) -> Unit,
    onUndo: () -> Unit,
) {
    val movable = tasks.asSequence()
        .filter { it.date == day && it.status == TaskStatus.OPEN &&
            it.parentId == null && it.routineId == null }
        .take(3)
        .toList()
    if (movable.isEmpty() && !canUndo) return

    Card(
        modifier = Modifier.fillMaxWidth().testTag("quick-reschedule"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Plan changed?", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text("Move an unfinished task to tomorrow. Deadlines move with it, and your plan is rebuilt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            movable.forEach { task ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(task.title, modifier = Modifier.weight(1f).padding(top = 10.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    OutlinedButton(onClick = { onMove(task) }, enabled = !busy,
                        modifier = Modifier.testTag("move-tomorrow-${task.id}")) {
                        Text("Tomorrow")
                    }
                }
            }
            if (canUndo) {
                TextButton(onClick = onUndo, enabled = !busy,
                    modifier = Modifier.testTag("undo-quick-move")) {
                    Text("Undo last move")
                }
            }
        }
    }
}
