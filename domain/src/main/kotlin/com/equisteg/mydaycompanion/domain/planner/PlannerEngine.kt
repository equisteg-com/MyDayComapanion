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
