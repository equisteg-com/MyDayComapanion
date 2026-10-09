package com.equisteg.mydaycompanion.domain.model

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
) {
    companion object {
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

/** A recurring commitment (e.g. "work out every weekday"). Basic routines arrive in Phase 1. */
data class Routine(
    val id: String,
    val title: String,
    val frequency: RoutineFrequency,
    val preferredStart: LocalTime?,
    val durationMinutes: Int,
    val enabled: Boolean = true,
    val createdAt: Instant,
)

data class UserPreferences(
    val dayStart: LocalTime = LocalTime.of(9, 0),
    val dayEnd: LocalTime = LocalTime.of(21, 0),
    val bufferMinutes: Int = 10,
    val speechOutputEnabled: Boolean = false,
)
