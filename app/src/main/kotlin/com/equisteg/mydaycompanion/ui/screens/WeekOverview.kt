package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

private val weekdayShort = DateTimeFormatter.ofPattern("EEE d")
private val weekMonth = DateTimeFormatter.ofPattern("d MMM")

data class WeekDaySummary(val date: LocalDate, val planned: Int, val done: Int)

/** Computation is independent from the UI and omits cancelled routine instances. */
fun weekSummaries(selectedDate: LocalDate, tasks: List<Task>, locale: Locale = Locale.getDefault()): List<WeekDaySummary> {
    val firstDay = selectedDate.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
    val planned = IntArray(7)
    val done = IntArray(7)
    for (task in tasks) {
        if (task.status == TaskStatus.CANCELLED) continue
        val date = task.date ?: continue
        val offset = java.time.temporal.ChronoUnit.DAYS.between(firstDay, date)
        if (offset !in 0L..6L) continue
        val index = offset.toInt()
        planned[index]++
        if (task.status == TaskStatus.DONE) done[index]++
    }
    return (0..6).map { offset ->
        WeekDaySummary(firstDay.plusDays(offset.toLong()), planned[offset], done[offset])
    }
}

/** Week-at-a-glance uses the phone's locale for week start, and supports accessible tap navigation. */
@Composable
fun WeekOverview(
    selectedDate: LocalDate,
    tasks: List<Task>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val week = remember(selectedDate, tasks) { weekSummaries(selectedDate, tasks) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("week-overview"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your week · ${week.first().date.format(weekMonth)}–${week.last().date.format(weekMonth)}",
                style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                week.forEach { day ->
                    FilterChip(
                        selected = day.date == selectedDate,
                        onClick = { onSelectDate(day.date) },
                        label = {
                            Column {
                                Text(day.date.format(weekdayShort))
                                Text("${day.done}/${day.planned} done", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier
                            .testTag("week-day-${day.date}")
                            .semantics {
                                contentDescription =
                                    "${day.date}: ${day.done} of ${day.planned} tasks completed. Select day."
                            },
                    )
                }
            }
        }
    }
}
