package com.equisteg.mydaycompanion.domain.insights

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime

/** Tasks planned for one day and how many of them are done. Subtasks and skipped tasks don't count. */
data class DayCount(val date: LocalDate, val planned: Int, val done: Int) {
    val fraction: Float get() = if (planned == 0) 0f else done.toFloat() / planned
}

/**
 * The small "how is my week going" view on My day. Worked out on the phone from the task list
 * the app already shows; nothing is stored or sent anywhere.
 */
data class Insights(
    val today: DayCount,
    /** The last seven days, oldest first, ending with today. */
    val week: List<DayCount>,
    /** Days in a row, up to today, with at least one task done. Today counts once something is done. */
    val streakDays: Int,
    val doneThisWeek: Int,
    /** Minutes of finished work today, from the tasks' planned durations. */
    val doneMinutesToday: Int,
)

/** The block running now and the next one to start, for the Now / Next line. */
data class NowNext(val now: PlanBlock?, val next: PlanBlock?)

object DayInsights {
    const val WEEK_DAYS = 7
    private const val MAX_STREAK_DAYS = 366

    fun of(tasks: List<Task>, today: LocalDate): Insights {
        val counted = tasks.filter { it.parentId == null && it.status != TaskStatus.CANCELLED && it.date != null }
        val byDate = counted.groupBy { it.date!! }
        fun count(date: LocalDate): DayCount {
            val day = byDate[date].orEmpty()
            return DayCount(date, day.size, day.count { it.status == TaskStatus.DONE })
        }
        val week = (WEEK_DAYS - 1 downTo 0).map { count(today.minusDays(it.toLong())) }
        var streak = 0
        var day = if (count(today).done > 0) today else today.minusDays(1)
        while (streak < MAX_STREAK_DAYS && count(day).done > 0) {
            streak++
            day = day.minusDays(1)
        }
        return Insights(
            today = count(today),
            week = week,
            streakDays = streak,
            doneThisWeek = week.sumOf { it.done },
            doneMinutesToday = byDate[today].orEmpty().filter { it.status == TaskStatus.DONE }.sumOf { it.durationMinutes },
        )
    }

    /** Finished tasks are skipped, so "Next" is always something still to do. */
    fun nowNext(plan: DayPlan?, tasks: List<Task>, now: LocalTime): NowNext {
        val done = tasks.filter { it.status != TaskStatus.OPEN }.map { it.id }.toSet()
        val open = plan?.blocks.orEmpty().filter { it.kind != BlockKind.TASK || it.refId !in done }.sortedBy { it.start }
        return NowNext(
            now = open.firstOrNull { it.start <= now && now < it.end },
            next = open.firstOrNull { it.start > now },
        )
    }
}
