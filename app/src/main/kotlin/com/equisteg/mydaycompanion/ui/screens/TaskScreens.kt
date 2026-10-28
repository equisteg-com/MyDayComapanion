package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.tools.SetAlarmTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM")
private val clockText = Regex("^(\\d{1,2})(?:[:.]?(\\d{2}))?\\s*([ap])?\\.?m?\\.?$")

/** Reads "9", "9:30", "0930", "6pm", "6.15 pm". Blank means "no time"; anything else unreadable is null. */
internal fun parseClock(text: String): Result<LocalTime?> {
    val t = text.trim().lowercase()
    if (t.isEmpty()) return Result.success(null)
    val m = clockText.matchEntire(t) ?: return Result.failure(IllegalArgumentException(text))
    var hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
    when (m.groupValues[3]) {
        "a" -> if (hour == 12) hour = 0
        "p" -> if (hour < 12) hour += 12
    }
    if (hour !in 0..23 || minute !in 0..59 || (m.groupValues[3].isNotEmpty() && m.groupValues[1].toInt() !in 1..12)) {
        return Result.failure(IllegalArgumentException(text))
    }
    return Result.success(LocalTime.of(hour, minute))
}

/** One task with its subtasks under it; tap the title to edit, tick the box to mark it done. */
@Composable
fun TaskRows(all: List<Task>, onToggle: (String) -> Unit, onEdit: (String) -> Unit) {
    // A skipped routine task stays stored (so the routine doesn't add it back) but is not shown.
    val tasks = all.filter { it.status != TaskStatus.CANCELLED }
    val children = tasks.filter { it.parentId != null }.groupBy { it.parentId }
    val ids = tasks.map { it.id }.toSet()
    // Subtasks whose parent sits on another day are shown on their own.
    val roots = tasks.filter { it.parentId == null || it.parentId !in ids }
    Column(Modifier.testTag("task-list")) {
        roots.forEach { t ->
            TaskRow(t, indent = false, onToggle, onEdit)
            children[t.id].orEmpty().forEach { TaskRow(it, indent = true, onToggle, onEdit) }
        }
    }
}

@Composable
private fun TaskRow(task: Task, indent: Boolean, onToggle: (String) -> Unit, onEdit: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = if (indent) 24.dp else 0.dp)) {
        Checkbox(checked = task.status == TaskStatus.DONE, onCheckedChange = { onToggle(task.id) }, modifier = Modifier.testTag("toggle-${task.id}"))
        Column(Modifier.weight(1f).clickable { onEdit(task.id) }.testTag("task-${task.id}")) {
            Text((if (task.personal) "🔒 " else "") + task.title)
            val details = listOfNotNull(
                "${task.durationMinutes} min",
                task.preferredStart?.let { "from $it" },
                task.deadline?.let { "by ${it.toLocalTime()}" },
                task.notes?.let { "note" },
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TaskEditDialog(
    task: Task,
    subtasks: List<Task>,
    busy: Boolean,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit,
    onAddSubtask: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    onDeviceAction: (String, JsonObject, String) -> Unit = { _, _, _ -> },
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            TaskEditForm(task, subtasks, busy, onSave, onDelete, onAddSubtask, onDismiss, Modifier.padding(20.dp), onDeviceAction)
        }
    }
}

/** The edit fields, kept outside the dialog window so they can be tested on their own. */
@Composable
fun TaskEditForm(
    task: Task,
    subtasks: List<Task>,
    busy: Boolean,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit,
    onAddSubtask: (String, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onDeviceAction: (String, JsonObject, String) -> Unit = { _, _, _ -> },
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.notes.orEmpty()) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var minutes by remember(task.id) { mutableStateOf(task.durationMinutes.toString()) }
    var date by remember(task.id) { mutableStateOf(task.date) }
    var start by remember(task.id) { mutableStateOf(task.preferredStart?.toString().orEmpty()) }
    var deadline by remember(task.id) { mutableStateOf(task.deadline?.toLocalTime()?.toString().orEmpty()) }
    var personal by remember(task.id) { mutableStateOf(task.personal) }
    var newStep by remember(task.id) { mutableStateOf("") }

    val duration = minutes.trim().toIntOrNull()?.takeIf { it in Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES }
    val startTime = parseClock(start)
    val deadlineTime = parseClock(deadline)
    val error = when {
        title.isBlank() || title.length > Task.MAX_TITLE_LENGTH -> "Give the task a title (up to ${Task.MAX_TITLE_LENGTH} characters)."
        duration == null -> "Minutes must be between ${Task.MIN_DURATION_MINUTES} and ${Task.MAX_DURATION_MINUTES}."
        startTime.isFailure -> "Start time: try 9, 9:30 or 6pm."
        deadlineTime.isFailure -> "Deadline: try 17:00 or 5pm."
        deadlineTime.getOrNull() != null && date == null -> "Pick a day for the deadline."
        notes.length > Task.MAX_NOTES_LENGTH -> "Notes are too long."
        else -> null
    }

    Column(modifier.verticalScroll(rememberScrollState()).testTag("task-edit"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (task.parentId == null) "Edit task" else "Edit step", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("edit-title"))
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth().testTag("edit-notes"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Priority.entries.forEach { p ->
                FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        OutlinedTextField(
            minutes,
            { minutes = it.filter(Char::isDigit).take(3) },
            label = { Text("Minutes") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("edit-minutes"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date?.format(shortDay) ?: "No day yet", modifier = Modifier.weight(1f).testTag("edit-date"))
            TextButton(onClick = { date = (date ?: LocalDate.now()).minusDays(1) }) { Text("‹") }
            TextButton(onClick = { date = (date ?: LocalDate.now().minusDays(1)).plusDays(1) }, modifier = Modifier.testTag("edit-next-day")) { Text("›") }
            if (date != null) TextButton(onClick = { date = null }) { Text("No day") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { start = it }, label = { Text("Start around") }, singleLine = true, modifier = Modifier.weight(1f).testTag("edit-start"))
            OutlinedTextField(deadline, { deadline = it }, label = { Text("Done by") }, singleLine = true, modifier = Modifier.weight(1f).testTag("edit-deadline"))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Personal")
                Text("Changes need your fingerprint, screen lock or a challenge answer.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = personal, onCheckedChange = { personal = it }, modifier = Modifier.testTag("edit-personal"))
        }

        if (task.parentId == null) {
            Text("Steps", style = MaterialTheme.typography.titleSmall)
            subtasks.forEach { Text("• ${it.title} (${it.durationMinutes} min)", style = MaterialTheme.typography.bodyMedium) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newStep, { newStep = it }, label = { Text("Add a step") }, singleLine = true, modifier = Modifier.weight(1f).testTag("edit-new-step"))
                TextButton(
                    onClick = { onAddSubtask(newStep, Task.DEFAULT_DURATION_MINUTES); newStep = "" },
                    enabled = !busy && newStep.isNotBlank() && subtasks.size < Task.MAX_SUBTASKS,
                    modifier = Modifier.testTag("edit-add-step"),
                ) { Text("Add") }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("edit-error")) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.testTag("edit-delete")) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(
                onClick = {
                    val d = date
                    onSave(
                        task.copy(
                            title = title.trim(),
                            notes = notes.trim().ifEmpty { null },
                            priority = priority,
                            durationMinutes = duration!!,
                            date = d,
                            preferredStart = startTime.getOrNull(),
                            deadline = deadlineTime.getOrNull()?.let { d!!.atTime(it) },
                            personal = personal,
                        ),
                    )
                },
                enabled = !busy && error == null,
                modifier = Modifier.testTag("edit-save"),
            ) { Text("Save") }
        }

        // Below Save so the form's own buttons stay where they were.
        if (task.parentId == null) DeviceActionsSection(task, busy, onDeviceAction)
    }
}

private const val PERSONAL_LABEL = "Reminder"

/**
 * Hand the task to another app (build doc F8). Each button asks for Confirm and then the
 * fingerprint, face or screen lock; the other app opens filled in and the owner finishes there.
 */
@Composable
private fun DeviceActionsSection(task: Task, busy: Boolean, onAction: (String, JsonObject, String) -> Unit) {
    var contact by remember(task.id) { mutableStateOf("") }
    val time = task.preferredStart ?: task.deadline?.toLocalTime()
    val isEmail = contact.contains('@')
    // The confirm text shows exactly what goes to the other app. A personal task's title never
    // leaves the app: the alarm and calendar get a neutral label, and maps and messages are off.
    val label = if (task.personal) PERSONAL_LABEL else task.title
    val shown = "\"$label\""
    fun obj(vararg p: Pair<String, Any>) = JsonObject(p.associate { (k, v) -> k to if (v is Int) JsonPrimitive(v) else JsonPrimitive(v.toString()) })

    Text("Do it with another app", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { onAction("device.setAlarm", SetAlarmTool.args(time!!, label), "Set an alarm for $time labelled $shown?") },
            enabled = !busy && time != null,
            modifier = Modifier.testTag("action-alarm"),
        ) { Text("Alarm") }
        TextButton(
            onClick = {
                onAction(
                    "device.calendarInsert",
                    obj("title" to label, "date" to task.date!!, "start" to time!!, "durationMinutes" to task.durationMinutes),
                    "Add $shown to your calendar at $time?",
                )
            },
            enabled = !busy && time != null && task.date != null,
            modifier = Modifier.testTag("action-calendar"),
        ) { Text("Calendar") }
        TextButton(
            onClick = { onAction("device.navigate", obj("query" to task.title), "Search maps for $shown?") },
            enabled = !busy && !task.personal,
            modifier = Modifier.testTag("action-navigate"),
        ) { Text("Directions") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(contact, { contact = it.take(254) }, label = { Text("Phone or e-mail") }, singleLine = true, modifier = Modifier.weight(1f).testTag("action-contact"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { onAction("device.dial", obj("number" to contact.trim()), "Call ${contact.trim()}?") },
            enabled = !busy && contact.isNotBlank() && !isEmail,
            modifier = Modifier.testTag("action-call"),
        ) { Text("Call") }
        TextButton(
            onClick = {
                onAction(
                    "device.composeMessage",
                    obj("channel" to if (isEmail) "EMAIL" else "SMS", "to" to contact.trim(), "body" to task.title),
                    if (isEmail) "Draft an e-mail to ${contact.trim()} saying $shown?" else "Draft a text to ${contact.trim()} saying $shown?",
                )
            },
            enabled = !busy && contact.isNotBlank() && !task.personal,
            modifier = Modifier.testTag("action-message"),
        ) { Text(if (isEmail) "E-mail" else "Text") }
    }
}
