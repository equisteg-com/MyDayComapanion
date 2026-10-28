package com.equisteg.mydaycompanion.domain.insights

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayInsightsTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val at = Instant.parse("2026-10-28T07:00:00Z")
    private var n = 0
    private fun task(daysAgo: Long, done: Boolean, minutes: Int = 30, status: TaskStatus? = null, parent: String? = null) =
        Task("t${n++}", "x", date = today.minusDays(daysAgo), status = status ?: if (done) TaskStatus.DONE else TaskStatus.OPEN, durationMinutes = minutes, parentId = parent, createdAt = at)

    @Test
    fun `counts the week, the streak and today's finished minutes`() {
        val tasks = listOf(
            task(0, done = true, minutes = 45), task(0, done = false), task(0, done = true, minutes = 15),
            task(1, done = true), task(2, done = true), task(2, done = false),
            // a gap three days ago ends the streak
            task(3, done = false), task(4, done = true),
            task(0, done = true, parent = "t0"), task(0, done = false, status = TaskStatus.CANCELLED),
            Task("backlog", "x", status = TaskStatus.DONE, createdAt = at),
        )
        val insights = DayInsights.of(tasks, today)
        assertEquals(DayCount(today, 3, 2), insights.today)
        assertEquals(7, insights.week.size)
        assertEquals(today, insights.week.last().date)
        assertEquals(listOf(0, 0, 1, 0, 1, 1, 2), insights.week.map { it.done })
        assertEquals(3, insights.streakDays)
        assertEquals(5, insights.doneThisWeek)
        assertEquals(60, insights.doneMinutesToday)
    }

    @Test
    fun `an unfinished today keeps yesterday's streak, and an empty list is all zero`() {
        val insights = DayInsights.of(listOf(task(0, done = false), task(1, done = true), task(2, done = true)), today)
        assertEquals(2, insights.streakDays)
        assertEquals(0f, insights.today.fraction)

        val empty = DayInsights.of(emptyList(), today)
        assertEquals(0, empty.streakDays)
        assertEquals(0, empty.doneThisWeek)
    }

    @Test
    fun `now and next skip finished tasks`() {
        fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
        val tasks = listOf(
            Task("a", "Write", createdAt = at, status = TaskStatus.DONE),
            Task("b", "Call", createdAt = at),
            Task("c", "Gym", createdAt = at),
        )
        val plan = DayPlan(
            today,
            listOf(
                PlanBlock("a", "Write", BlockKind.TASK, t(9), t(10)),
                PlanBlock("e", "Stand-up", BlockKind.FIXED_EVENT, t(9, 30), t(9, 45)),
                PlanBlock("b", "Call", BlockKind.TASK, t(11), t(11, 30)),
                PlanBlock("c", "Gym", BlockKind.TASK, t(18), t(19)),
            ),
            emptyList(),
            emptyList(),
            "test",
        )
        val morning = DayInsights.nowNext(plan, tasks, t(9, 40))
        assertEquals("Stand-up", morning.now?.title)
        assertEquals("Call", morning.next?.title)

        val late = DayInsights.nowNext(plan, tasks, t(20))
        assertNull(late.now)
        assertNull(late.next)
        assertEquals(NowNext(null, null), DayInsights.nowNext(null, tasks, t(9)))
    }
}
