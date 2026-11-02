package com.equisteg.mydaycompanion.domain.planner

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.EventSource
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.PlanIssue
import com.equisteg.mydaycompanion.domain.model.PlanIssueType
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalTime

/**
 * First-fit scheduler over the free time left between fixed events.
 *
 * Ordering: tasks with a deadline on the plan day first (earliest deadline first), then tasks
 * with a preferred start (earliest first), then by priority (high first), then by the order the
 * user gave them. A task with a preferred start goes to the free time nearest that time (later
 * is preferred over earlier). Tasks that do not fit are reported as unscheduled rather than
 * silently squeezed in; fixed events are never moved.
 *
 * Version 4: the day window grows to include anything the owner gave a clock time ("gym at 7am"
 * with a 9:00 day start, "medicine at 9pm" with a 21:00 day end), see [PlanWindow]; a timed task
 * that can't go at its time goes as close to it as possible instead of the first gap of the day.
 */
class DeterministicPlanner : PlannerEngine {
    override val version: String = "deterministic-planner/4"

    override fun plan(request: PlanRequest): DayPlan {
        val issues = mutableListOf<PlanIssue>()
        val window = PlanWindow.of(request)
        val dayStartMin = window.first
        val dayEndMin = window.last
        val ownStart = request.dayStart.toMinuteOfDay()
        val ownEnd = request.dayEnd.toMinuteOfDay()
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
                    if (events[i].source == EventSource.CALENDAR || events[i - 1].source == EventSource.CALENDAR) {
                        "Two events overlap; one comes from the phone's calendar, so change it there if needed."
                    } else {
                        "Two fixed events overlap; the user has to choose which to keep."
                    },
                )
            }
        }

        var free = freeSlots(earliestMin, dayEndMin, eventBlocks)
        val taskBlocks = mutableListOf<PlanBlock>()
        val unscheduled = mutableListOf<String>()

        // A task broken into subtasks is planned through its subtasks, so its time is not counted twice.
        val parents = request.tasks.filter { it.parentId != null && it.status != TaskStatus.CANCELLED }.mapNotNull { it.parentId }.toSet()
        val candidates = request.tasks
            .withIndex()
            .filter { it.value.status == TaskStatus.OPEN && it.value.id !in parents && (it.value.date == null || it.value.date == request.date) }
            .sortedWith(
                compareBy<IndexedValue<Task>>(
                    { if (it.value.deadline?.toLocalDate() == request.date) 0 else 1 },
                    { it.value.deadline?.takeIf { d -> d.toLocalDate() == request.date }?.toLocalTime() ?: LocalTime.MAX },
                    { it.value.preferredStart ?: LocalTime.MAX },
                    { -it.value.priority.weight },
                    { it.index },
                ),
            )
            .map { it.value }

        for (task in candidates) {
            val duration = task.durationMinutes.coerceIn(Task.MIN_DURATION_MINUTES, Task.MAX_DURATION_MINUTES)
            val preferred = task.preferredStart?.toMinuteOfDay()
            val placement = if (preferred == null) {
                // Untimed work stays inside the owner's own day window, even when timed items widened it.
                free.withIndex().firstNotNullOfOrNull { (i, slot) ->
                    val from = maxOf(slot.first, ownStart)
                    val to = minOf(slot.last, ownEnd)
                    if (to - from >= duration) i to from else null
                }
            } else {
                nearest(free, preferred, duration)
            }
            if (placement == null) {
                unscheduled += task.id
                issues += PlanIssue(PlanIssueType.OVERLOADED, task.id, "Not enough free time left in the day for this task.")
                continue
            }
            val (slotIndex, start) = placement
            val slot = free[slotIndex]
            val end = start + duration
            taskBlocks += PlanBlock(task.id, task.title, BlockKind.TASK, start.toLocalTime(), end.toLocalTime())

            val deadline = task.deadline
            if (deadline != null && deadline.toLocalDate() == request.date && end > deadline.toLocalTime().toMinuteOfDay()) {
                issues += PlanIssue(PlanIssueType.DEADLINE_AT_RISK, task.id, "This task cannot finish before its deadline.")
            }

            val nextStart = end + request.bufferMinutes
            val before = slot.first..(start - request.bufferMinutes)
            val remaining = listOfNotNull(
                before.takeIf { start > slot.first && it.last > it.first },
                (nextStart..slot.last).takeIf { nextStart < slot.last },
            )
            free = free.toMutableList().also {
                it.removeAt(slotIndex)
                it.addAll(slotIndex, remaining)
            }
        }

        val blocks = (eventBlocks + taskBlocks).sortedWith(compareBy({ it.start }, { it.kind.ordinal }))
        return DayPlan(request.date, blocks, unscheduled, issues, version)
    }

    /**
     * The start closest to [preferred] in any free interval that fits [duration]. Starting late
     * costs half as much as starting early, so "after 6" lands after 6 when it can.
     */
    private fun nearest(free: List<IntRange>, preferred: Int, duration: Int): Pair<Int, Int>? =
        free.withIndex()
            .filter { it.value.last - it.value.first >= duration }
            .map { (i, slot) -> i to preferred.coerceIn(slot.first, slot.last - duration) }
            .minByOrNull { (_, start) -> if (start >= preferred) start - preferred else 2 * (preferred - start) }

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
