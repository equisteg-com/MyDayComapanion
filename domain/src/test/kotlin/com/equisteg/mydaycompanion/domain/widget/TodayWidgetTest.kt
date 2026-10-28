package com.equisteg.mydaycompanion.domain.widget

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

class TodayWidgetTest {
    private val day = LocalDate.of(2026, 10, 6)
    private val now = Instant.parse("2026-10-06T07:00:00Z")
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private val tasks = listOf(
        Task("t1", "Finish the proposal", createdAt = now),
        Task("t2", "Pick up medicine", createdAt = now, personal = true),
        Task("t3", "Call Raj", createdAt = now, status = TaskStatus.DONE),
        Task("t4", "Buy milk", createdAt = now),
        Task("t5", "Gym", createdAt = now),
    )
    private val plan = DayPlan(
        day,
        listOf(
            PlanBlock("t3", "Call Raj", BlockKind.TASK, t(9), t(9, 15)),
            PlanBlock("t1", "Finish the proposal", BlockKind.TASK, t(9, 30), t(11)),
            PlanBlock("e1", "Team meeting", BlockKind.FIXED_EVENT, t(11), t(12)),
            PlanBlock("t2", "Pick up medicine", BlockKind.TASK, t(13), t(13, 30)),
            PlanBlock("t4", "Buy milk", BlockKind.TASK, t(17), t(17, 30)),
            PlanBlock("t5", "Gym", BlockKind.TASK, t(8), t(8, 45)),
        ),
        emptyList(),
        emptyList(),
        "test",
    )

    @Test
    fun `names stay hidden unless the owner turns them on, and a personal task never shows its name`() {
        val hidden = TodayWidget.summarize(plan, tasks, t(10), showTitles = false)
        assertEquals("4 left today · now until 11:00", hidden.headline)
        assertEquals(listOf(WidgetLine(t(9, 30), "Task"), WidgetLine(t(11), "Event"), WidgetLine(t(13), "Personal task")), hidden.lines)

        val shown = TodayWidget.summarize(plan, tasks, t(10), showTitles = true)
        assertEquals(listOf("Finish the proposal", "Team meeting", "Personal task"), shown.lines.map { it.text })
    }

    @Test
    fun `finished and past items drop off, and an empty day says so`() {
        val morning = TodayWidget.summarize(plan, tasks, t(7), showTitles = true)
        assertEquals("5 left today · next at 08:00", morning.headline)
        assertEquals("Gym", morning.lines.first().text)
        assertEquals(TodayWidget.MAX_LINES, morning.lines.size)

        assertEquals(WidgetSummary("Nothing else planned today", emptyList()), TodayWidget.summarize(plan, tasks, t(18), showTitles = true))
        assertEquals(WidgetSummary("Nothing else planned today", emptyList()), TodayWidget.summarize(null, tasks, t(7), showTitles = true))
    }
}
