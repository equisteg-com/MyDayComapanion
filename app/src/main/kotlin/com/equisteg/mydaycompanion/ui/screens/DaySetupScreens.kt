package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.ai.text.NumericDateOrder
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.ui.UiState
import java.time.LocalTime

private val START_HOURS = (5..11).toList()
private val END_HOURS = (17..23).toList()

@Composable
private fun <T> ChipRow(label: String, options: List<T>, selected: T, enabled: Boolean, text: (T) -> String, tag: String, onPick: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { if (o != selected) onPick(o) },
                label = { Text(text(o)) },
                enabled = enabled,
                modifier = Modifier.testTag("$tag-$o"),
            )
        }
    }
}

private fun hourText(h: Int) = when {
    h == 12 -> "12 pm"
    h > 12 -> "${h - 12} pm"
    else -> "$h am"
}

/** The planner's day window, buffer, date format and reminder lead time. Shared by Settings and first-run setup. */
@Composable
fun YourDayFields(prefs: UserPreferences, enabled: Boolean, onChange: (UserPreferences) -> Unit) {
    ChipRow("Day starts", START_HOURS, prefs.dayStart.hour, enabled, ::hourText, "day-start") { onChange(prefs.copy(dayStart = LocalTime.of(it, 0))) }
    ChipRow("Day ends", END_HOURS, prefs.dayEnd.hour, enabled, ::hourText, "day-end") { onChange(prefs.copy(dayEnd = LocalTime.of(it, 0))) }
    ChipRow("Gap between tasks", UserPreferences.BUFFER_CHOICES, prefs.bufferMinutes, enabled, { if (it == 0) "None" else "$it min" }, "buffer") {
        onChange(prefs.copy(bufferMinutes = it))
    }
    Text("When I write 3/4 I mean", style = MaterialTheme.typography.bodySmall)
    NumericDateOrder.entries.forEach { order ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = prefs.dateOrder == order,
                onClick = { if (prefs.dateOrder != order) onChange(prefs.copy(dateOrder = order)) },
                enabled = enabled,
                modifier = Modifier.testTag("date-order-${order.name}"),
            )
            Text(if (order == NumericDateOrder.DAY_MONTH) "3 April (day first)" else "March 4 (month first)")
        }
    }
    ChipRow(
        "Remind me before a task",
        UserPreferences.REMINDER_LEAD_CHOICES,
        prefs.reminderLeadMinutes,
        enabled,
        { if (it == 0) "At start" else "$it min" },
        "reminder-lead",
    ) { onChange(prefs.copy(reminderLeadMinutes = it)) }
}

@Composable
fun YourDaySection(state: UiState, onChange: (UserPreferences) -> Unit) {
    Text("Your day", style = MaterialTheme.typography.titleMedium)
    YourDayFields(state.preferences, enabled = !state.busy, onChange = onChange)
}

/**
 * First-run setup in four steps (build doc F10): what the agent is, your day, protecting the
 * agent, and a first sentence to try. Nothing is saved until the owner taps Start.
 */
@Composable
fun OnboardingScreen(
    state: UiState,
    onFinish: (UserPreferences) -> Unit,
    onSetUpQuestions: (List<Pair<String, String>>) -> Unit,
    modifier: Modifier = Modifier,
    onRestoreFromBackup: () -> Unit = {},
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var dayStart by rememberSaveable { mutableIntStateOf(state.preferences.dayStart.hour) }
    var dayEnd by rememberSaveable { mutableIntStateOf(state.preferences.dayEnd.hour) }
    var buffer by rememberSaveable { mutableIntStateOf(state.preferences.bufferMinutes) }
    var dateOrder by rememberSaveable { mutableStateOf(state.preferences.dateOrder) }
    var lead by rememberSaveable { mutableIntStateOf(state.preferences.reminderLeadMinutes) }
    var settingQuestions by rememberSaveable { mutableStateOf(false) }
    val draft = state.preferences.copy(
        dayStart = LocalTime.of(dayStart, 0),
        dayEnd = LocalTime.of(dayEnd, 0),
        bufferMinutes = buffer,
        dateOrder = dateOrder,
        reminderLeadMinutes = lead,
    )

    Column(
        modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()).testTag("onboarding"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Step ${step + 1} of $ONBOARDING_STEPS", style = MaterialTheme.typography.labelMedium)
        when (step) {
            0 -> {
                Text("Meet your day agent", style = MaterialTheme.typography.headlineSmall)
                Text("Tell it your day the way you'd text a friend: \"gym tmrw 6ish, call Raj after lunch, dentist fri 3-4pm\".")
                Text("It plans around your fixed times and does nothing until you approve.")
                Text(
                    "Everything stays on this phone in an encrypted database. No account, no internet, no analytics.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onRestoreFromBackup, enabled = !state.busy, modifier = Modifier.testTag("onboarding-restore")) {
                    Text("Restore from backup")
                }
                Text("Moving from another phone? Pick the backup file you made there.", style = MaterialTheme.typography.bodySmall)
            }
            1 -> {
                Text("Your day", style = MaterialTheme.typography.headlineSmall)
                YourDayFields(draft, enabled = true) {
                    dayStart = it.dayStart.hour
                    dayEnd = it.dayEnd.hour
                    buffer = it.bufferMinutes
                    dateOrder = it.dateOrder
                    lead = it.reminderLeadMinutes
                }
            }
            2 -> {
                Text("Protect your agent", style = MaterialTheme.typography.headlineSmall)
                Text("The app locks with your fingerprint, face or screen lock. Personal tasks and risky actions ask for it again.")
                Text(
                    if (state.hasChallenges) {
                        "Challenge questions are set up."
                    } else {
                        "Optional: add three challenge questions as a second check, and a way in if your screen lock fails."
                    },
                )
                if (!state.hasChallenges) {
                    OutlinedButton(onClick = { settingQuestions = true }, enabled = !state.busy, modifier = Modifier.testTag("onboarding-questions")) {
                        Text("Set up questions")
                    }
                }
                Text(
                    "If the phone is lost, find.google.com can lock or erase it. Turn on Find My Device in Android Settings → Google.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            else -> {
                Text("Try it", style = MaterialTheme.typography.headlineSmall)
                Text("Type something like this on the next screen:")
                Text("\"tmrw finish report by 5, call mom around 7ish, groceries 30 min\"", style = MaterialTheme.typography.bodyLarge)
                Text("You'll see the plan first. Tap a task later to edit it, add steps, or mark it personal.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") } else Text("")
            if (step < ONBOARDING_STEPS - 1) {
                Button(onClick = { step++ }, modifier = Modifier.testTag("onboarding-next")) { Text("Next") }
            } else {
                Button(onClick = { onFinish(draft) }, enabled = !state.busy && draft.isValid, modifier = Modifier.testTag("onboarding-start")) { Text("Start") }
            }
        }
    }

    if (settingQuestions) {
        ChallengeSetupDialog(
            onSave = { settingQuestions = false; onSetUpQuestions(it) },
            onCancel = { settingQuestions = false },
        )
    }
}

const val ONBOARDING_STEPS = 4
