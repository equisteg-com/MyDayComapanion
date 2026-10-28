package com.equisteg.mydaycompanion.domain.reminders

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderKind { TASK, EVENT, BRIEF }

/**
 * One local notification. [id] is stable for the same item and day, so re-syncing replaces rather
 * than duplicates. [title] is what the notification shows; for a personal task it is generic,
 * because notifications can appear on the lock screen.
 */
data class Reminder(
    val id: String,
    val kind: ReminderKind,
    val at: Instant,
    val title: String,
    val body: String,
    /** The task this reminder is for; lets the notification's Done button act on it. */
    val taskId: String? = null,
)

/**
 * Works out every reminder for the given days from the saved plans. Rules:
 * - a task reminds [UserPreferences.reminderLeadMinutes] before its planned start, or at the
 *   owner's explicit "remind me at" time;
 * - a fixed event reminds the same lead time before it starts;
 * - a morning brief fires at the start of the owner's day if anything is planned;
 * - a routine the owner set to "no reminder" gets none;
 * - nothing in the past, nothing for finished tasks, at most [MAX_PER_DAY] per day.
 */
object ReminderPlanner {
    const val MAX_PER_DAY = 40
    const val PERSONAL_TITLE = "Personal task"

    fun plan(
        plans: List<DayPlan>,
        tasks: List<Task>,
        preferences: UserPreferences,
        now: Instant,
        zone: ZoneId,
        routines: List<Routine> = emptyList(),
    ): List<Reminder> {
        val byId = tasks.associateBy { it.id }
        val silentRoutines = routines.filter { !it.remind }.map { it.id }.toSet()
        return plans.sortedBy { it.date }.flatMap { plan ->
            val day = buildList {
                if (plan.blocks.isNotEmpty()) {
                    add(
                        Reminder(
                            id = "brief:${plan.date}",
                            kind = ReminderKind.BRIEF,
                            at = plan.date.atTime(preferences.dayStart).atZone(zone).toInstant(),
                            title = "Your day",
                            body = DailyBrief.summary(plan, tasks),
                        ),
                    )
                }
                for (block in plan.blocks) {
                    val lead = preferences.reminderLeadMinutes.toLong()
                    when (block.kind) {
                        BlockKind.TASK -> {
                            val task = byId[block.refId] ?: continue
                            if (task.status != TaskStatus.OPEN || task.routineId in silentRoutines) continue
                            val at = task.remindAt?.let { plan.date.atTime(it) } ?: plan.date.atTime(block.start).minusMinutes(lead)
                            add(taskReminder(plan.date, task, at, block.start, zone))
                        }
                        BlockKind.FIXED_EVENT -> add(
                            Reminder(
                                id = "event:${block.refId}:${plan.date}",
                                kind = ReminderKind.EVENT,
                                // Lead time counted on the full date-time, so 00:10 minus 15 minutes is the evening before.
                                at = plan.date.atTime(block.start).minusMinutes(lead).atZone(zone).toInstant(),
                                title = block.title,
                                body = "Starts at ${block.start}",
                            ),
                        )
                    }
                }
            }
            day.filter { it.at.isAfter(now) }.sortedBy { it.at }.take(MAX_PER_DAY)
        }
    }

    private fun taskReminder(date: LocalDate, task: Task, at: LocalDateTime, start: LocalTime, zone: ZoneId) = Reminder(
        id = "task:${task.id}:$date",
        kind = ReminderKind.TASK,
        at = at.atZone(zone).toInstant(),
        title = if (task.personal) PERSONAL_TITLE else task.title,
        body = "Planned for $start",
        taskId = task.id,
    )
}

/** A short spoken or written summary of a day. Personal task titles are never included. */
object DailyBrief {
    fun summary(plan: DayPlan, tasks: List<Task>, day: String = "today"): String {
        val byId = tasks.associateBy { it.id }
        val taskBlocks = plan.blocks.filter { it.kind == BlockKind.TASK }
        val events = plan.blocks.count { it.kind == BlockKind.FIXED_EVENT }
        if (plan.blocks.isEmpty()) return "Nothing planned yet."
        val parts = buildList {
            if (taskBlocks.isNotEmpty()) add(plural(taskBlocks.size, "task"))
            if (events > 0) add(plural(events, "event"))
        }
        val first = plan.blocks.minBy { it.start }
        val firstTitle = if (byId[first.refId]?.personal == true) "a personal task" else first.title
        val late = if (plan.unscheduledTaskIds.isNotEmpty()) " ${plural(plan.unscheduledTaskIds.size, "task")} didn't fit." else ""
        return "${parts.joinToString(" and ")} $day. First up: $firstTitle at ${first.start}.$late"
    }

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}
