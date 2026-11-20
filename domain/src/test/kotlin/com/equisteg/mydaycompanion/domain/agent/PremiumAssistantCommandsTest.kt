package com.equisteg.mydaycompanion.domain.agent

import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PremiumAssistantCommandsTest {
    private val date = LocalDate.of(2026, 11, 20)
    private val now = LocalTime.of(10, 0)
    private val at = Instant.parse("2026-11-20T00:00:00Z")
    private fun task(id: String, dateOffset: Long, done: Boolean = false,
                     dueAt: LocalTime? = null, title: String = id) =
        Task(id = id, title = title, createdAt = at, date = date.plusDays(dateOffset),
            deadline = dueAt?.let { date.plusDays(dateOffset).atTime(it) },
            status = if (done) TaskStatus.DONE else TaskStatus.OPEN,
            durationMinutes = 30)

    @Test fun `new assistant questions are recognized without using a model or network`() {
        val parser = AgentCommandParser()
        assertEquals(AgentCommand.Deadlines, parser.parse("show my deadlines", date, now))
        assertEquals(AgentCommand.Deadlines, parser.parse("What's overdue deadlines?", date, now))
        assertEquals(AgentCommand.WeekSummary, parser.parse("week summary", date, now))
        assertEquals(AgentCommand.WeekSummary, parser.parse("my plan this week", date, now))
        assertEquals(AgentCommand.WhatsNext, parser.parse("what's next", date, now))
    }

    @Test fun `deadlines list open tasks and identify overdue commitments`() {
        val tasks = listOf(
            task("late", -1, dueAt = LocalTime.of(16, 0), title = "Missed form"),
            task("soon", 1, dueAt = LocalTime.of(9, 30), title = "Report"),
            task("finished", -2, done = true, dueAt = LocalTime.of(9, 0), title = "Done item"),
        )
        val reply = AgentReplies.deadlines(tasks, date, now)
        assertContains(reply, "Overdue: Missed form")
        assertContains(reply, "Report")
        assertFalse(reply.contains("Done item"))
    }

    @Test fun `week insight excludes yesterday and cancelled tasks`() {
        val tasks = listOf(task("before", -1), task("today", 0), task("soon", 2),
            task("finished", 3, done = true), task("far", 10),
            task("cancel", 1).copy(status = TaskStatus.CANCELLED))
        val reply = AgentReplies.weekSummary(tasks, date)
        assertContains(reply, "3 tasks")
        assertContains(reply, "2 open")
        assertContains(reply, "1 hours 0 minutes")
        assertFalse(reply.contains("before"))
    }

    @Test fun `empty questions return helpful guidance`() {
        assertContains(AgentReplies.deadlines(emptyList(), date, now), "No deadlines")
        assertContains(AgentReplies.weekSummary(emptyList(), date), "No tasks")
    }
}
