package com.equisteg.mydaycompanion.domain.planner

import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.Task
import java.time.LocalDate
import java.time.LocalTime

data class PlanRequest(
    val date: LocalDate,
    val tasks: List<Task>,
    val fixedEvents: List<FixedEvent>,
    val dayStart: LocalTime = LocalTime.of(9, 0),
    val dayEnd: LocalTime = LocalTime.of(21, 0),
    val bufferMinutes: Int = 10,
    /** When replanning the current day, nothing is scheduled before this time. */
    val notBefore: LocalTime? = null,
)

/**
 * Produces a day plan. Implementations must be deterministic: the same request always yields the
 * same plan. AI may suggest durations or priorities, but schedule constraints are enforced here.
 */
interface PlannerEngine {
    val version: String
    fun plan(request: PlanRequest): DayPlan
}

/**
 * The minutes of the day a plan may use: the owner's day window, grown to include fixed events and
 * tasks the owner gave a time (they said when; the window is only the default for everything else).
 * Never crosses midnight. Shared by the planner and [PlanValidator] so both judge the same window.
 */
object PlanWindow {
    fun of(request: PlanRequest): IntRange {
        var start = request.dayStart.hour * 60 + request.dayStart.minute
        var end = request.dayEnd.hour * 60 + request.dayEnd.minute
        request.fixedEvents.filter { it.date == request.date }.forEach {
            start = minOf(start, it.start.hour * 60 + it.start.minute)
            end = maxOf(end, it.end.hour * 60 + it.end.minute)
        }
        request.tasks.filter { it.date == null || it.date == request.date }.forEach {
            val preferred = it.preferredStart ?: return@forEach
            val p = preferred.hour * 60 + preferred.minute
            start = minOf(start, p)
            end = maxOf(end, p + it.durationMinutes)
        }
        return start..minOf(end, LAST_MINUTE)
    }

    private const val LAST_MINUTE = 23 * 60 + 59
}
