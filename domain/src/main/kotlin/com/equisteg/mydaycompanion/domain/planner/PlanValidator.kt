package com.equisteg.mydaycompanion.domain.planner

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus

/**
 * Independent check of a plan against its request. Used on every plan before it is shown or
 * saved, and by the evaluation harness. An AI-generated plan is not trusted to preserve task
 * identity, fixed-event identity, or every requested task's scheduled/unscheduled disposition.
 */
object PlanValidator {
    fun validate(plan: DayPlan, request: PlanRequest): List<String> {
        val errors = mutableListOf<String>()
        if (plan.date != request.date) errors += "Plan date does not match the request"

        val tasks = plan.blocks.filter { it.kind == BlockKind.TASK }
        val sorted = tasks.sortedBy { it.start }
        for (i in 1 until sorted.size) {
            if (sorted[i].start < sorted[i - 1].end) errors += "Task blocks overlap: ${sorted[i - 1].refId} and ${sorted[i].refId}"
        }

        val events = plan.blocks.filter { it.kind == BlockKind.FIXED_EVENT }
        val expectedEvents = request.fixedEvents.filter { it.date == request.date }
        val expectedEventIds = expectedEvents.map { it.id }.toSet()
        val actualEventIds = events.map { it.refId }
        if (actualEventIds.size != actualEventIds.toSet().size) errors += "A fixed event was scheduled more than once"
        if (actualEventIds.any { it !in expectedEventIds }) errors += "Plan contains an unknown fixed event"

        val window = PlanWindow.of(request)
        for (t in tasks) {
            val start = t.start.hour * 60 + t.start.minute
            val end = t.end.hour * 60 + t.end.minute
            if (start < window.first || end > window.last) errors += "Task ${t.refId} is outside the day window"
            if (request.notBefore != null && t.start < request.notBefore) errors += "Task ${t.refId} starts in the past"
            if (!t.end.isAfter(t.start)) errors += "Task ${t.refId} has no duration"
            // A model must not squeeze an hour of work into a one-minute block.
            val requestedDuration = request.tasks.firstOrNull { it.id == t.refId }
                ?.durationMinutes?.coerceIn(Task.MIN_DURATION_MINUTES, Task.MAX_DURATION_MINUTES)
            if (requestedDuration != null && end - start != requestedDuration) {
                errors += "Task ${t.refId} duration differs from the request"
            }
            for (event in events) {
                if (t.start < event.end && event.start < t.end) errors += "Task ${t.refId} overlaps fixed event ${event.refId}"
            }
        }
        for (event in expectedEvents) {
            val matches = events.filter { it.refId == event.id }
            if (matches.size != 1 || matches[0].start != event.start || matches[0].end != event.end) {
                errors += "Fixed event ${event.id} was moved, duplicated or dropped"
            }
        }

        // Keep this selection rule in sync with DeterministicPlanner: completed/cancelled
        // tasks, tasks for another day, and parent tasks with active subtasks are not candidates.
        val parents = request.tasks.filter { it.parentId != null && it.status != TaskStatus.CANCELLED }
            .mapNotNull { it.parentId }.toSet()
        val eligible = request.tasks.filter {
            it.status == TaskStatus.OPEN && it.id !in parents && (it.date == null || it.date == request.date)
        }.associateBy(Task::id)
        val scheduledIds = tasks.map { it.refId }
        val unscheduledIds = plan.unscheduledTaskIds
        if (scheduledIds.size != scheduledIds.toSet().size) errors += "A task was scheduled more than once"
        if (unscheduledIds.size != unscheduledIds.toSet().size) errors += "A task was marked unscheduled more than once"
        if (scheduledIds.any { it !in eligible }) errors += "Plan contains an unknown or ineligible task"
        if (unscheduledIds.any { it !in eligible }) errors += "Plan contains an unknown or ineligible unscheduled task"
        if (scheduledIds.any { it in unscheduledIds }) errors += "A task is both scheduled and unscheduled"
        val accounted = scheduledIds.toSet() + unscheduledIds.toSet()
        if (eligible.keys.any { it !in accounted }) errors += "Plan silently dropped one or more requested tasks"
        return errors
    }
}
