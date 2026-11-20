package com.equisteg.mydaycompanion.domain.productivity

import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** Explicit user-initiated local edits; every write still goes through the normal security policy. */
enum class BatchEditAction(val label: String) {
    COMPLETE("Mark done"),
    REOPEN("Reopen"),
    PRIORITY_HIGH("Set high priority"),
    MOVE_TOMORROW("Move to tomorrow"),
}

data class BatchEditDraft(
    val changes: List<Pair<Task, Task>>,
    val affectedDates: Set<LocalDate>,
)

/**
 * All-or-nothing preflight of up to 20 non-sensitive root tasks.
 * The caller executes these with the audited tool orchestrator and stops if any write fails.
 * Applying the actions is not an atomic database transaction: never describe it as one.
 */
object BatchEditPlanner {
    const val MAX_TASKS = 20

    fun plan(
        allTasks: List<Task>,
        selectedIds: Set<String>,
        action: BatchEditAction,
        today: LocalDate,
    ): Result<BatchEditDraft> = runCatching {
        require(selectedIds.isNotEmpty() && selectedIds.size <= MAX_TASKS) {
            "Select between 1 and $MAX_TASKS tasks."
        }
        val byId = allTasks.associateBy { it.id }
        require(byId.size == allTasks.size) { "Duplicate task IDs in the current snapshot." }
        val changes = selectedIds.sorted().map { id ->
            val task = requireNotNull(byId[id]) { "A selected task no longer exists." }
            require(!task.personal && task.parentId == null && task.routineId == null &&
                task.status != TaskStatus.CANCELLED) {
                "Personal tasks, steps, routines and skipped tasks require individual review."
            }
            val edited = when (action) {
                BatchEditAction.COMPLETE -> {
                    require(task.status == TaskStatus.OPEN) { "Choose open tasks to complete." }
                    task.copy(status = TaskStatus.DONE)
                }
                BatchEditAction.REOPEN -> {
                    require(task.status == TaskStatus.DONE) { "Choose completed tasks to reopen." }
                    task.copy(status = TaskStatus.OPEN)
                }
                BatchEditAction.PRIORITY_HIGH -> {
                    require(task.priority != Priority.HIGH) { "Selected task is already high priority." }
                    task.copy(priority = Priority.HIGH)
                }
                BatchEditAction.MOVE_TOMORROW -> {
                    require(task.status == TaskStatus.OPEN && task.date != null &&
                        !task.date.isAfter(today)) {
                        "Move only open tasks dated today or earlier; future plans are preserved."
                    }
                    val target = today.plusDays(1)
                    task.copy(date = target, deadline = task.deadline?.let { target.atTime(it.toLocalTime()) })
                }
            }
            task to edited
        }
        BatchEditDraft(
            changes,
            changes.flatMap { (before, after) -> listOfNotNull(before.date, after.date) }.toSet(),
        )
    }
}

data class MonthDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val planned: Int,
    val done: Int,
    val overdueOpen: Int,
)

/**
 * One-pass counts and a locale-aware month grid, including adjacent calendar days.
 * Never includes cancelled tasks or subtasks in headline completion statistics.
 */
object MonthOverview {
    fun grid(month: YearMonth, tasks: List<Task>, today: LocalDate, locale: Locale = Locale.getDefault()): List<MonthDay> {
        val first = month.atDay(1)
        val firstWeekday = WeekFields.of(locale).firstDayOfWeek
        val offset = (first.dayOfWeek.value - firstWeekday.value + 7) % 7
        val start = first.minusDays(offset.toLong())
        val totalCells = ((offset + month.lengthOfMonth() + 6) / 7 * 7).coerceIn(28, 42)
        val counts = HashMap<LocalDate, IntArray>()
        val last = start.plusDays(totalCells.toLong() - 1)
        for (task in tasks) {
            if (task.status == TaskStatus.CANCELLED || task.parentId != null) continue
            val day = task.date ?: continue
            if (day.isBefore(start) || day.isAfter(last)) continue
            val array = counts.getOrPut(day) { IntArray(3) }
            array[0]++
            if (task.status == TaskStatus.DONE) array[1]++
            if (task.status == TaskStatus.OPEN &&
                (task.deadline?.isBefore(today.atStartOfDay()) == true || day.isBefore(today))) {
                array[2]++
            }
        }
        return (0 until totalCells).map { n ->
            val date = start.plusDays(n.toLong())
            val c = counts[date]
            MonthDay(date, YearMonth.from(date) == month, c?.get(0) ?: 0,
                c?.get(1) ?: 0, c?.get(2) ?: 0)
        }
    }
}

data class ProductivityReport(
    val scheduled: Int,
    val completed: Int,
    val dueSoon: Int,
    val overdue: Int,
    val backlog: Int,
    val remainingMinutes: Int,
    val recommendation: String,
)

/** Contextual, explainable and deterministic; no cloud/model/data collection. */
object ProductivityCoach {
    fun report(tasks: List<Task>, day: LocalDate): ProductivityReport {
        val roots = tasks.asSequence().filter { it.parentId == null && it.status != TaskStatus.CANCELLED }.toList()
        val planned = roots.filter { it.date == day }
        val open = planned.filter { it.status == TaskStatus.OPEN }
        val overdue = roots.count { it.status == TaskStatus.OPEN &&
            (it.date?.isBefore(day) == true || it.deadline?.toLocalDate()?.isBefore(day) == true) }
        val dueSoon = roots.count { it.status == TaskStatus.OPEN && it.deadline != null &&
            !it.deadline.toLocalDate().isBefore(day) &&
            !it.deadline.toLocalDate().isAfter(day.plusDays(2)) }
        val backlog = roots.count { it.status == TaskStatus.OPEN && it.date == null }
        val remaining = open.sumOf { it.durationMinutes }
        val advice = when {
            overdue > 0 -> "$overdue overdue task(s). Review and carry forward only what still matters."
            dueSoon > 0 -> "$dueSoon upcoming deadline(s). Prioritize the most urgent first."
            remaining > 360 -> "About ${remaining / 60} hours remain today. Consider moving lower-priority work."
            backlog > 0 -> "$backlog unscheduled task(s). Give the important ones a day."
            planned.isNotEmpty() && open.isEmpty() -> "Everything planned for this day is complete."
            open.isEmpty() -> "Start by adding one small, specific task to your day."
            else -> "Your plan has ${open.size} task(s) left. Start with the next short task."
        }
        return ProductivityReport(planned.size, planned.size - open.size, dueSoon, overdue,
            backlog, remaining, advice)
    }
}

/** Editable, offline prompt templates. No subscription or hidden provider dependency. */
data class CaptureTemplate(val id: String, val group: String, val label: String, val prompt: String)

object CaptureTemplates {
    val builtIn = listOf(
        CaptureTemplate("daily", "Work", "Plan my workday", "Today review inbox for 20 minutes, work on my top priority for 90 minutes, then review progress for 15 minutes"),
        CaptureTemplate("meeting", "Work", "Meeting prep", "Tomorrow prepare meeting agenda for 30 minutes, attend team meeting at 10am for 45 minutes, write follow-up notes for 20 minutes"),
        CaptureTemplate("deep-work", "Focus", "Deep work", "Tomorrow focus on my project for 90 minutes starting at 9am, take a break, then review for 30 minutes"),
        CaptureTemplate("study", "Learning", "Study session", "Today study for 60 minutes, practice examples for 30 minutes, recap for 15 minutes"),
        CaptureTemplate("wellness", "Wellness", "Healthy day", "Tomorrow walk at 7am for 30 minutes, stretch for 15 minutes, journal for 10 minutes"),
        CaptureTemplate("shopping", "Personal", "Errands", "Tomorrow buy groceries for 45 minutes, pay bills for 20 minutes, tidy my room for 30 minutes"),
        CaptureTemplate("deadline", "Work", "Finish a deadline", "Tomorrow finish my report by 5pm for 90 minutes, review it for 30 minutes"),
        CaptureTemplate("routine", "Habits", "Build a routine", "Walk every weekday at 7am for 30 minutes"),
    )
}
