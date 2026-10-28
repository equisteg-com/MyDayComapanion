package com.equisteg.mydaycompanion.domain.agent

import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.domain.tools.RollOverTasksTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentCommandsTest {
    private val today = LocalDate.of(2026, 10, 6) // a Tuesday
    private val now = LocalTime.of(9, 0)
    private val parser = AgentCommandParser()
    private fun parse(s: String) = parser.parse(s, today, now)

    @Test
    fun `questions about the day`() {
        listOf("what's next?", "What now", "what should I do now", "next", "whats up next").forEach {
            assertEquals(AgentCommand.WhatsNext, parse(it), it)
        }
        listOf("what's left today", "how's my day going?", "summary", "Summarize my day").forEach {
            assertEquals(AgentCommand.DaySummary, parse(it), it)
        }
        listOf("roll over", "carry over from yesterday", "move unfinished tasks to today", "bring the leftover ones from yesterday to today").forEach {
            assertEquals(AgentCommand.RollOver, parse(it), it)
        }
    }

    @Test
    fun `moves name the task and the new day or time`() {
        assertEquals(AgentCommand.Move("gym", LocalDate.of(2026, 10, 9), null), parse("move gym to friday"))
        assertEquals(AgentCommand.Move("call Raj", null, LocalTime.of(16, 0)), parse("push call Raj to 4pm"))
        assertEquals(AgentCommand.Move("dentist", today.plusDays(1), LocalTime.of(15, 0)), parse("Reschedule my dentist to tomorrow 3pm"))
        assertEquals(AgentCommand.Move("report", today.plusDays(1), null), parse("postpone the report"))
        assertEquals(AgentCommand.Move("report", today.plusDays(1), null), parse("Postpone The report"))
        assertEquals(AgentCommand.Move("work on report", LocalDate.of(2026, 10, 9), null), parse("move work on report to friday"))
        assertEquals(AgentCommand.Move("gym", today.plusDays(1), null), parse("move gym tomorrow"))
    }

    @Test
    fun `focus sessions`() {
        assertEquals(AgentCommand.Focus(25, null), parse("focus"))
        assertEquals(AgentCommand.Focus(25, "report"), parse("focus on report"))
        assertEquals(AgentCommand.Focus(50, null), parse("focus for 50 minutes"))
        assertEquals(AgentCommand.Focus(45, "the report"), parse("start a focus timer on the report for 45 min"))
        assertEquals(AgentCommand.Focus(30, "slides"), parse("focus 30 min on slides"))
        assertNull(parse("focus 600 min"))
    }

    @Test
    fun `everything else is left to the planner`() {
        listOf(
            "tmrw gym 6am, call Raj around 4ish",
            "buy milk",
            "move",
            "move gym",
            "put the kettle on",
            "next week dentist",
            "a".repeat(200),
        ).forEach { assertNull(parse(it), it) }
    }

    @Test
    fun `the matcher finds the one task meant, or says which ones fit`() {
        val at = Instant.parse("2026-10-06T07:00:00Z")
        val tasks = listOf(
            Task("a", "Call Raj", date = today, createdAt = at),
            Task("b", "Call mum", date = today.plusDays(1), createdAt = at),
            Task("c", "Write the Q3 report", date = today, createdAt = at),
            Task("d", "Call Raj", date = today, status = TaskStatus.DONE, createdAt = at),
            Task("e", "Call Raj back", parentId = "a", createdAt = at),
        )
        assertEquals(TaskMatch.One(tasks[0]), TaskMatcher.find("call raj", tasks))
        assertEquals(TaskMatch.One(tasks[2]), TaskMatcher.find("report", tasks))
        assertEquals(TaskMatch.One(tasks[2]), TaskMatcher.find("q3 report", tasks))
        assertIs<TaskMatch.Many>(TaskMatcher.find("call", tasks))
        assertEquals(TaskMatch.One(tasks[1]), TaskMatcher.find("call", tasks.take(2), preferDate = today.plusDays(1)))
        assertEquals(TaskMatch.None, TaskMatcher.find("gym", tasks))
    }

    @Test
    fun `roll-over moves exactly the listed unfinished tasks, with subtasks and deadlines`() = runTest {
        val g = TestGraph()
        val at = g.clock.instant()
        val yesterday = today.minusDays(1)
        g.tasks.upsert(
            listOf(
                Task("y1", "Finish slides", date = yesterday, deadline = yesterday.atTime(17, 0), createdAt = at),
                Task("y1s", "Charts", date = yesterday, parentId = "y1", createdAt = at),
                Task("y2", "Gym", date = yesterday, routineId = "r1", createdAt = at),
                Task("y3", "Done already", date = yesterday, status = TaskStatus.DONE, createdAt = at),
            ),
        )
        val movable = g.tasks.all().filter { RollOverTasksTool.movable(it, today) }
        assertEquals(listOf("y1"), movable.map { it.id })

        val action = ProposedAction("tasks.rollOver", RollOverTasksTool.args(today, movable))
        val ask = g.orchestrator.execute(action, Requester.AGENT)
        assertIs<ExecutionResult.NeedsConfirmation>(ask, "the agent's suggestion needs the owner's tap")
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(action, Requester.AGENT, UserConfirmation(ask.actionFingerprint)))

        assertEquals(today, g.tasks.get("y1")?.date)
        assertEquals(today.atTime(17, 0), g.tasks.get("y1")?.deadline)
        assertEquals(today, g.tasks.get("y1s")?.date)
        assertEquals(yesterday, g.tasks.get("y2")?.date, "routine tasks stay")
        assertTrue(g.audit.entries.none { "Finish slides" in it.detail.orEmpty() })

        // A done or routine task can't be slipped into the list.
        val sneaky = ProposedAction("tasks.rollOver", JsonObject(mapOf("to" to JsonPrimitive(today.toString()), "taskIds" to JsonPrimitive("y2"))))
        assertIs<ExecutionResult.Failed>(g.orchestrator.execute(sneaky, Requester.USER))
        val junk = ProposedAction("tasks.rollOver", JsonObject(mapOf("to" to JsonPrimitive("soon"), "taskIds" to JsonPrimitive("y1"))))
        assertTrue(g.orchestrator.execute(junk, Requester.USER) !is ExecutionResult.Done)
        // Only to today or the next two weeks: never into the past or far ahead.
        g.tasks.upsert(listOf(Task("y4", "Old", date = yesterday.minusDays(1), createdAt = at)))
        for (to in listOf(yesterday, today.plusDays(RollOverTasksTool.MAX_DAYS_AHEAD + 1))) {
            val far = ProposedAction("tasks.rollOver", JsonObject(mapOf("to" to JsonPrimitive(to.toString()), "taskIds" to JsonPrimitive("y4"))))
            assertTrue(g.orchestrator.execute(far, Requester.USER) !is ExecutionResult.Done, "to=$to")
        }
        assertEquals(yesterday.minusDays(1), g.tasks.get("y4")?.date)
    }
}

class AgentRepliesTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val at = Instant.parse("2026-10-06T07:00:00Z")

    @Test
    fun `answers say what is now, next and left`() {
        val tasks = listOf(
            Task("a", "Write report", date = today, createdAt = at),
            Task("b", "Call Raj", date = today, createdAt = at),
            Task("c", "Stretch", date = today, status = TaskStatus.DONE, createdAt = at),
            Task("d", "Gym", date = today, createdAt = at),
            Task("e", "Read", date = today, createdAt = at),
        )
        val plan = com.equisteg.mydaycompanion.domain.model.DayPlan(
            today,
            listOf(
                com.equisteg.mydaycompanion.domain.model.PlanBlock("a", "Write report", com.equisteg.mydaycompanion.domain.model.BlockKind.TASK, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                com.equisteg.mydaycompanion.domain.model.PlanBlock("b", "Call Raj", com.equisteg.mydaycompanion.domain.model.BlockKind.TASK, LocalTime.of(11, 0), LocalTime.of(11, 15)),
            ),
            emptyList(),
            emptyList(),
            "test",
        )
        assertEquals("Now: Write report, until 10:00. Next: Call Raj at 11:00.", AgentReplies.whatsNext(plan, tasks, LocalTime.of(9, 30)))
        assertEquals("Nothing else is planned today. Tell me what you'd like to add.", AgentReplies.whatsNext(plan, tasks, LocalTime.of(12, 0)))
        assertEquals("1 of 5 done. Still to do: Write report, Call Raj, Gym and 1 more.", AgentReplies.daySummary(tasks, today))
        assertEquals("Nothing is planned today yet.", AgentReplies.daySummary(tasks, today.plusDays(1)))
        assertEquals("tomorrow at 16:00", AgentReplies.whenText(today.plusDays(1), LocalTime.of(16, 0), today))
        assertEquals("Friday 9 October", AgentReplies.whenText(LocalDate.of(2026, 10, 9), null, today))
        assertEquals("2 unfinished tasks from earlier days: Write report, Call Raj.", AgentReplies.rollOver(tasks.take(2)))
    }
}
