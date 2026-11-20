package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.productivity.MonthDay
import com.equisteg.mydaycompanion.domain.productivity.MonthOverview
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Full month calendar with a linear large-font/narrow-screen fallback.
 * Date cells use ≥48dp height and announce progress to TalkBack.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    tasks: List<Task>,
    onSelectDate: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    val locale = Locale.getDefault()
    val cells = remember(month, tasks, today, locale) {
        MonthOverview.grid(month, tasks, today, locale)
    }
    val narrow = LocalConfiguration.current.screenWidthDp < 380 ||
        LocalDensity.current.fontScale > 1.3f

    Card(
        modifier = Modifier.fillMaxWidth().testTag("month-calendar"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("month-title"),
                )
                TextButton(onClick = { onMonthChange(month.minusMonths(1)) },
                    modifier = Modifier.testTag("month-previous")) { Text("‹ Previous") }
                TextButton(onClick = { onMonthChange(month.plusMonths(1)) },
                    modifier = Modifier.testTag("month-next")) { Text("Next ›") }
            }
            if (narrow) {
                Text("List view for easier reading and larger touch targets.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                cells.filter { it.inMonth }.forEach { day ->
                    MonthDayCell(day, day.date == selectedDate, compact = false,
                        modifier = Modifier.fillMaxWidth()) { onSelectDate(day.date) }
                }
            } else {
                val weekdayNames = cells.take(7).map { it.date.dayOfWeek }
                Row(Modifier.fillMaxWidth()) {
                    weekdayNames.forEach { weekday ->
                        Text(weekday.getDisplayName(TextStyle.NARROW, locale),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        week.forEach { day ->
                            MonthDayCell(day, day.date == selectedDate, compact = true,
                                modifier = Modifier.weight(1f)) { onSelectDate(day.date) }
                        }
                    }
                }
            }
            Text("Select any date to review its plan. Counts exclude cancelled tasks and steps.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthDayCell(day: MonthDay, selected: Boolean, compact: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        selected -> colors.primaryContainer
        !day.inMonth -> colors.surfaceContainerLow
        day.overdueOpen > 0 -> colors.tertiaryContainer
        else -> colors.surfaceContainerHigh
    }
    androidx.compose.material3.Surface(
        modifier = modifier.heightIn(min = if (compact) 52.dp else 48.dp)
            .testTag("month-day-${day.date}")
            .semantics {
                contentDescription = "${day.date}: ${day.done} of ${day.planned} tasks done" +
                    if (day.overdueOpen > 0) ", ${day.overdueOpen} overdue tasks" else ""
            },
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        if (compact) {
            Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.bodySmall,
                    color = if (day.inMonth) colors.onSurface else colors.onSurfaceVariant)
                Text(if (day.planned == 0) "·" else "${day.done}/${day.planned}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant)
            }
        } else {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(day.date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault())),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Text("${day.done}/${day.planned} done" +
                    if (day.overdueOpen > 0) " · ${day.overdueOpen} overdue" else "",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Switch between the seven-day strip and full month, keeping the user's chosen date. */
@Composable
fun CalendarOverview(
    selectedDate: LocalDate,
    today: LocalDate,
    tasks: List<Task>,
    onSelectDate: (LocalDate) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("WEEK") }
    var monthName by rememberSaveable(selectedDate.year, selectedDate.monthValue) {
        mutableStateOf(YearMonth.from(selectedDate).toString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "WEEK", onClick = { mode = "WEEK" },
                label = { Text("Week") }, modifier = Modifier.testTag("calendar-week"))
            FilterChip(selected = mode == "MONTH", onClick = { mode = "MONTH" },
                label = { Text("Month") }, modifier = Modifier.testTag("calendar-month"))
            TextButton(onClick = {
                onSelectDate(today)
                monthName = YearMonth.from(today).toString()
            }, modifier = Modifier.testTag("calendar-today")) { Text("Jump to today") }
        }
        if (mode == "WEEK") {
            WeekOverview(selectedDate, tasks, onSelectDate)
        } else {
            MonthCalendar(
                month = YearMonth.parse(monthName), selectedDate = selectedDate,
                today = today, tasks = tasks,
                onSelectDate = onSelectDate,
                onMonthChange = { monthName = it.toString() },
            )
        }
    }
}
