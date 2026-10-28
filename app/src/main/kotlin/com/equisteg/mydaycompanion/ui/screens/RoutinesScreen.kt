package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.ui.MainViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

/** Repeating items: add one, pause it, switch its reminder, or delete it. */
@Composable
fun RoutinesScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Routines", style = MaterialTheme.typography.titleLarge)
        Text("Things you do again and again. Each one is added to your day on the days it is due, and planned around everything else.")
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        if (routines.isEmpty()) {
            Text("No routines yet. Add one below, or type something like \"gym every weekday at 7am\" on My day.", style = MaterialTheme.typography.bodyMedium)
        }
        routines.forEach { r -> RoutineRow(r, state.busy, viewModel::onUpdateRoutine, viewModel::onDeleteRoutine) }

        HorizontalDivider()
        NewRoutineForm(state.busy, viewModel::onCreateRoutine)
    }
}

internal fun routineSummary(r: Routine): String {
    val days = when (r.frequency) {
        RoutineFrequency.DAILY -> "Every day"
        RoutineFrequency.WEEKDAYS -> "Weekdays"
        RoutineFrequency.WEEKLY -> "Every ${r.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "week"}"
    }
    val at = r.preferredStart?.let { " at $it" }.orEmpty()
    return "$days$at, ${r.durationMinutes} min" + if (r.remind) "" else ", no reminder"
}

@Composable
private fun RoutineRow(r: Routine, busy: Boolean, onUpdate: (Routine) -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("routine-${r.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Text(routineSummary(r) + if (r.enabled) "" else " (paused)", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = r.enabled,
                onCheckedChange = { onUpdate(r.copy(enabled = it)) },
                enabled = !busy,
                modifier = Modifier.testTag("routine-enabled-${r.id}"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onUpdate(r.copy(remind = !r.remind)) }, enabled = !busy) {
                Text(if (r.remind) "Turn reminder off" else "Turn reminder on")
            }
            TextButton(onClick = { onDelete(r.id) }, enabled = !busy, modifier = Modifier.testTag("routine-delete-${r.id}")) { Text("Delete") }
        }
    }
}

@Composable
private fun NewRoutineForm(busy: Boolean, onCreate: (Routine) -> Unit) {
    var title by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(RoutineFrequency.DAILY) }
    var day by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var time by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("30") }
    var remind by remember { mutableStateOf(true) }

    val parsedTime = parseClock(time)
    val duration = minutes.toIntOrNull()?.takeIf { it in Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES }
    val valid = title.isNotBlank() && title.length <= Task.MAX_TITLE_LENGTH && parsedTime.isSuccess && duration != null

    Text("Add a routine", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(title, { title = it }, label = { Text("What") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("routine-title"))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutineFrequency.entries.forEach { f ->
            FilterChip(
                selected = frequency == f,
                onClick = { frequency = f },
                label = { Text(when (f) { RoutineFrequency.DAILY -> "Every day"; RoutineFrequency.WEEKDAYS -> "Weekdays"; RoutineFrequency.WEEKLY -> "Once a week" }) },
                modifier = Modifier.testTag("routine-frequency-${f.name}"),
            )
        }
    }
    if (frequency == RoutineFrequency.WEEKLY) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { d ->
                FilterChip(selected = day == d, onClick = { day = d }, label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            time, { time = it }, label = { Text("Time (optional)") }, singleLine = true, isError = parsedTime.isFailure,
            modifier = Modifier.weight(1f).testTag("routine-time"),
        )
        OutlinedTextField(
            minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("Minutes") }, singleLine = true, isError = duration == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Remind me", Modifier.weight(1f))
        Switch(checked = remind, onCheckedChange = { remind = it })
    }
    Button(
        enabled = valid && !busy,
        onClick = {
            onCreate(
                Routine(
                    id = "", title = title.trim(), frequency = frequency, preferredStart = parsedTime.getOrNull(), durationMinutes = duration!!,
                    enabled = true, createdAt = Instant.EPOCH, dayOfWeek = day.takeIf { frequency == RoutineFrequency.WEEKLY }, remind = remind,
                ),
            )
            title = ""
            time = ""
        },
        modifier = Modifier.testTag("routine-save"),
    ) { Text("Save routine") }
}
