package com.equisteg.mydaycompanion.domain.planner

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.PlanIssue
import com.equisteg.mydaycompanion.domain.model.PlanIssueType
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalTime

/**
 * First-fit scheduler over the free time left between fixed events.
 *
 * Ordering: tasks with a deadline on the plan day first (earliest deadline first), then by
 * priority (high first), then by the order the user gave them. Tasks that do not fit are reported
 * as unscheduled rather than silently squeezed in; fixed events are never moved.
 */
class DeterministicPlanner : PlannerEngine {
    override val version: String = "deterministic-planner/1"

    override fun plan(request: PlanRequest): DayPlan {
        val issues = mutableListOf<PlanIssue>()
        val dayStartMin = request.dayStart.toMinuteOfDay()
        val dayEndMin = request.dayEnd.toMinuteOfDay()
        val earliestMin = maxOf(dayStartMin, request.notBefore?.toMinuteOfDay()?.roundUpTo(5) ?: dayStartMin)

        val events = request.fixedEvents.filter { it.date == request.date }.sortedBy { it.start }
        val eventBlocks = events.map {
            PlanBlock(it.id, it.title, BlockKind.FIXED_EVENT, it.start, it.end)
        }
        for (i in 1 until events.size) {
            if (events[i].start < events[i - 1].end) {
                issues += PlanIssue(
                    PlanIssueType.FIXED_EVENT_CONFLICT,
                    events[i].id,
                    "Two fixed events overlap; the user has to choose which to keep.",
                )
            }
        }
        events.filter { it.start.toMinuteOfDay() < dayStartMin || it.end.toMinuteOfDay() > dayEndMin }.forEach {
            issues += PlanIssue(PlanIssueType.OUTSIDE_DAY_WINDOW, it.id, "A fixed event falls outside the planning window.")
        }

        var free = freeSlots(earliestMin, dayEndMin, eventBlocks)
        val taskBlocks = mutableListOf<PlanBlock>()
        val unscheduled = mutableListOf<String>()

        val candidates = request.tasks
            .withIndex()
            .filter { it.value.status == TaskStatus.OPEN && (it.value.date == null || it.value.date == request.date) }
            .sortedWith(
                compareBy<IndexedValue<Task>>(
                    { if (it.value.deadline?.toLocalDate() == request.date) 0 else 1 },
                    { it.value.deadline?.takeIf { d -> d.toLocalDate() == request.date }?.toLocalTime() ?: LocalTime.MAX },
                    { -it.value.priority.weight },
                    { it.index },
                ),
            )
            .map { it.value }

        for (task in candidates) {
            val duration = task.durationMinutes.coerceIn(Task.MIN_DURATION_MINUTES, Task.MAX_DURATION_MINUTES)
            val slotIndex = free.indexOfFirst { it.last - it.first >= duration }
            if (slotIndex < 0) {
                unscheduled += task.id
                issues += PlanIssue(PlanIssueType.OVERLOADED, task.id, "Not enough free time left in the day for this task.")
                continue
            }
            val slot = free[slotIndex]
            val start = slot.first
            val end = start + duration
            taskBlocks += PlanBlock(task.id, task.title, BlockKind.TASK, start.toLocalTime(), end.toLocalTime())

            val deadline = task.deadline
            if (deadline != null && deadline.toLocalDate() == request.date && end > deadline.toLocalTime().toMinuteOfDay()) {
                issues += PlanIssue(PlanIssueType.DEADLINE_AT_RISK, task.id, "This task cannot finish before its deadline.")
            }

            val nextStart = end + request.bufferMinutes
            free = free.toMutableList().also {
                if (nextStart < slot.last) it[slotIndex] = nextStart..slot.last else it.removeAt(slotIndex)
            }
        }

        val blocks = (eventBlocks + taskBlocks).sortedWith(compareBy({ it.start }, { it.kind.ordinal }))
        return DayPlan(request.date, blocks, unscheduled, issues, version)
    }

    /** Free intervals (in minutes of day, inclusive start / exclusive end encoded as first..last). */
    private fun freeSlots(from: Int, to: Int, busy: List<PlanBlock>): List<IntRange> {
        val slots = mutableListOf<IntRange>()
        var cursor = from
        for (block in busy.sortedBy { it.start }) {
            val bStart = block.start.toMinuteOfDay()
            val bEnd = block.end.toMinuteOfDay()
            if (bStart > cursor) slots += cursor..minOf(bStart, to)
            cursor = maxOf(cursor, bEnd)
            if (cursor >= to) break
        }
        if (cursor < to) slots += cursor..to
        return slots.filter { it.last > it.first }
    }
}

internal fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

internal fun Int.toLocalTime(): LocalTime = LocalTime.of((this / 60).coerceAtMost(23), this % 60)

private fun Int.roundUpTo(step: Int): Int = ((this + step - 1) / step) * step
