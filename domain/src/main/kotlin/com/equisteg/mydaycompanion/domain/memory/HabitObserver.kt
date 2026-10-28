package com.equisteg.mydaycompanion.domain.memory

import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Notices habits from finished tasks, so the agent can propose "You usually do Gym around 07:30"
 * (build doc F9). Deterministic and on the phone; it only proposes, and the owner decides.
 *
 * A habit is the same task title finished on [minDays] different days within [windowDays], at
 * times no more than [maxSpreadMinutes] apart. Personal tasks are never used, so a proposal never
 * shows their titles.
 */
class HabitObserver(
    private val windowDays: Long = 21,
    private val minDays: Int = 3,
    private val maxSpreadMinutes: Int = 120,
    private val maxProposalsPerRun: Int = 2,
) {
    data class Habit(val content: String, val confidence: Double)

    private val policy = MemoryPolicy()

    private fun candidate(h: Habit, now: Instant) = MemoryItem(
        "check", MemoryType.BEHAVIOUR, h.content, MemorySource.AGENT_PROPOSED, h.confidence, MemoryStatus.PROPOSED,
        MemorySensitivity.LOW, Retention.DAYS_365, createdAt = now, updatedAt = now,
    )

    fun habits(tasks: List<Task>, existing: List<MemoryItem>, now: Instant, zone: ZoneId): List<Habit> {
        val since = now.minus(windowDays, ChronoUnit.DAYS)
        return tasks
            .asSequence()
            .filter { it.status == TaskStatus.DONE && !it.personal && it.parentId == null && it.updatedAt >= since && it.updatedAt <= now }
            .groupBy { memoryKey(it.title) }
            .values
            .mapNotNull { group ->
                // One completion per day: the first one that day.
                val perDay = group.groupBy { it.updatedAt.atZone(zone).toLocalDate() }.values.map { day -> day.minBy { it.updatedAt } }
                if (perDay.size < minDays) return@mapNotNull null
                val minutes = perDay.map { it.updatedAt.atZone(zone).toLocalTime().let { t -> t.hour * 60 + t.minute } }.sorted()
                if (minutes.last() - minutes.first() > maxSpreadMinutes) return@mapNotNull null
                val median = minutes[minutes.size / 2]
                val rounded = ((median + 7) / 15 * 15).coerceAtMost(23 * 60 + 45)
                val title = perDay.maxBy { it.updatedAt }.title.trim()
                Habit("You usually do \"$title\" around ${LocalTime.of(rounded / 60, rounded % 60)}", (perDay.size / 5.0).coerceAtMost(1.0))
            }
            // Already known (kept, proposed or turned down), or something the memory rules would refuse.
            .filter { h -> existing.none { it.coversHabit(h.content) } && policy.admit(candidate(h, now)) is MemoryDecision.Accepted }
            .sortedByDescending { it.confidence }
            .take(maxProposalsPerRun)
    }
}
