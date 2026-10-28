package com.equisteg.mydaycompanion.domain.model

import com.equisteg.mydaycompanion.domain.ai.text.NumericDateOrder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Priority(val weight: Int) { LOW(1), MEDIUM(2), HIGH(3) }

enum class TaskStatus { OPEN, DONE, CANCELLED }

/**
 * A unit of work the user wants to do. [parentId] links a subtask to its parent task.
 * [date] is the day the user wants to do it on (null = unscheduled backlog).
 * [preferredStart] is a soft time ("in the morning", "after 6"): the planner places the task at
 * or after it when there is room, and anywhere in the day otherwise.
 */
data class Task(
    val id: String,
    val title: String,
    val priority: Priority = Priority.MEDIUM,
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val date: LocalDate? = null,
    val deadline: LocalDateTime? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val parentId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val preferredStart: LocalTime? = null,
    /** Free text the owner adds; never sent to the audit log. */
    val notes: String? = null,
    /** Personal items need the owner's device credential or a challenge answer for every change (build doc §6.1). */
    val personal: Boolean = false,
    /** Set when this task was generated from a routine for [date]; used so a routine is added once per day. */
    val routineId: String? = null,
    /** An explicit reminder time the owner asked for ("remind me at 5"); otherwise the lead time before the planned start applies. */
    val remindAt: LocalTime? = null,
) {
    companion object {
        const val MAX_NOTES_LENGTH = 2_000
        const val MAX_SUBTASKS = 20
        const val DEFAULT_DURATION_MINUTES = 30
        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 8 * 60
        const val MAX_TITLE_LENGTH = 120
    }
}

/** An immovable commitment such as a meeting. The planner never moves these. */
data class FixedEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val durationMinutes: Int,
    val source: EventSource = EventSource.USER,
) {
    val end: LocalTime get() = start.plusMinutes(durationMinutes.toLong())
}

enum class EventSource { USER, CALENDAR }

enum class RoutineFrequency { DAILY, WEEKDAYS, WEEKLY }

/**
 * A recurring commitment (e.g. "gym every weekday at 6am"). Each day it is due, one task is
 * generated for it (see RoutineExpander). [dayOfWeek] is used only by WEEKLY routines.
 */
data class Routine(
    val id: String,
    val title: String,
    val frequency: RoutineFrequency,
    val preferredStart: LocalTime?,
    val durationMinutes: Int,
    val enabled: Boolean = true,
    val createdAt: Instant,
    val dayOfWeek: DayOfWeek? = null,
    val remind: Boolean = true,
) {
    fun isDueOn(date: LocalDate): Boolean = enabled && when (frequency) {
        RoutineFrequency.DAILY -> true
        RoutineFrequency.WEEKDAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        RoutineFrequency.WEEKLY -> date.dayOfWeek == dayOfWeek
    }

    companion object {
        const val MAX_ROUTINES = 50
    }
}

data class UserPreferences(
    val dayStart: LocalTime = LocalTime.of(9, 0),
    val dayEnd: LocalTime = LocalTime.of(21, 0),
    val bufferMinutes: Int = 10,
    val speechOutputEnabled: Boolean = false,
    /** How "3/4" is read: 3 April (day first) or March 4 (month first). */
    val dateOrder: NumericDateOrder = NumericDateOrder.DAY_MONTH,
    /** How long before a task's start its reminder fires (used by reminders, part 4). */
    val reminderLeadMinutes: Int = 10,
    /** False until the owner finishes first-run setup. */
    val onboardingDone: Boolean = false,
) {
    /** The planner needs at least two hours between the start and end of the day. */
    val isValid: Boolean
        get() = dayStart.plusHours(2) <= dayEnd && dayStart < dayEnd && bufferMinutes in BUFFER_CHOICES && reminderLeadMinutes in REMINDER_LEAD_CHOICES

    companion object {
        val BUFFER_CHOICES = listOf(0, 5, 10, 15, 30)
        val REMINDER_LEAD_CHOICES = listOf(0, 5, 10, 15, 30, 60)
    }
}
