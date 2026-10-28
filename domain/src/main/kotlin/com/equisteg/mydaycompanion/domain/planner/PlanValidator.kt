package com.equisteg.mydaycompanion.domain.planner

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan

/**
 * Independent check of a plan against its request. Used on every plan before it is shown or
 * saved, and by the evaluation harness, so a planner bug (or a future AI-proposed plan) can never
 * produce an impossible schedule unnoticed.
 */
object PlanValidator {
    fun validate(plan: DayPlan, request: PlanRequest): List<String> {
        val errors = mutableListOf<String>()
        val tasks = plan.blocks.filter { it.kind == BlockKind.TASK }
        val sorted = tasks.sortedBy { it.start }

        for (i in 1 until sorted.size) {
            if (sorted[i].start < sorted[i - 1].end) errors += "Task blocks overlap: ${sorted[i - 1].refId} and ${sorted[i].refId}"
        }
        val events = plan.blocks.filter { it.kind == BlockKind.FIXED_EVENT }
        for (t in tasks) {
            if (t.start < request.dayStart || t.end > request.dayEnd) errors += "Task ${t.refId} is outside the day window"
            if (request.notBefore != null && t.start < request.notBefore) errors += "Task ${t.refId} starts in the past"
            if (!t.end.isAfter(t.start)) errors += "Task ${t.refId} has no duration"
            for (e in events) {
                if (t.start < e.end && e.start < t.end) errors += "Task ${t.refId} overlaps fixed event ${e.refId}"
            }
        }
        for (e in request.fixedEvents.filter { it.date == request.date }) {
            val block = events.firstOrNull { it.refId == e.id }
            if (block == null || block.start != e.start || block.end != e.end) errors += "Fixed event ${e.id} was moved or dropped"
        }
        val ids = tasks.map { it.refId }
        if (ids.size != ids.toSet().size) errors += "A task was scheduled more than once"
        if (ids.any { it in plan.unscheduledTaskIds }) errors += "A task is both scheduled and unscheduled"
        return errors
    }
}
