package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.agent.AgentProposal
import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.ai.text.NumericDateOrder
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.planner.DeterministicPlanner
import com.equisteg.mydaycompanion.domain.planner.PlanRequest
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskEditToolsTest {
    private val day = LocalDate.parse("2026-10-06")

    private suspend fun TestGraph.seed(personal: Boolean = false): Task {
        val t = Task(id = "t1", title = "Write report", date = day, createdAt = clock.instant(), personal = personal)
        tasks.upsert(listOf(t))
        return t
    }

    private fun idArgs(key: String, id: String) = JsonObject(mapOf(key to JsonPrimitive(id)))

    @Test
    fun `owner edits every field and subtasks follow the task to its new day`() = runTest {
        val g = TestGraph()
        val t = g.seed()
        assertIs<ExecutionResult.Done>(
            g.orchestrator.execute(
                ProposedAction("tasks.addSubtask", JsonObject(mapOf("parentId" to JsonPrimitive("t1"), "title" to JsonPrimitive("Draft intro"), "durationMinutes" to JsonPrimitive(20)))),
                Requester.USER,
            ),
        )
        val sub = g.tasks.all().single { it.parentId == "t1" }
        assertEquals(day, sub.date)

        val edited = t.copy(
            title = "Write the Q3 report",
            priority = Priority.HIGH,
            durationMinutes = 90,
            date = day.plusDays(1),
            preferredStart = LocalTime.of(10, 0),
            deadline = day.plusDays(1).atTime(17, 0),
            notes = "Use last quarter's numbers",
        )
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(ProposedAction("tasks.update", UpdateTaskTool.args(edited)), Requester.USER))
        val saved = g.tasks.get("t1")!!
        assertEquals("Write the Q3 report", saved.title)
        assertEquals(Priority.HIGH, saved.priority)
        assertEquals(90, saved.durationMinutes)
        assertEquals(LocalTime.of(10, 0), saved.preferredStart)
        assertEquals(day.plusDays(1).atTime(17, 0), saved.deadline)
        assertEquals("Use last quarter's numbers", saved.notes)
        assertEquals(day.plusDays(1), g.tasks.get(sub.id)!!.date)
        assertTrue(g.audit.entries.none { it.detail.orEmpty().contains("Q3") || it.detail.orEmpty().contains("quarter") })
    }

    @Test
    fun `agent edits need a tap and deleting always does`() = runTest {
        val g = TestGraph()
        val t = g.seed()
        val update = ProposedAction("tasks.update", UpdateTaskTool.args(t.copy(title = "Renamed")))
        assertIs<ExecutionResult.NeedsConfirmation>(g.orchestrator.execute(update, Requester.AGENT))

        val delete = ProposedAction("tasks.delete", idArgs("taskId", "t1"))
        val ask = assertIs<ExecutionResult.NeedsConfirmation>(g.orchestrator.execute(delete, Requester.USER))
        assertFalse(ask.personal)
        g.tasks.upsert(listOf(Task(id = "s1", title = "Step", parentId = "t1", date = day, createdAt = g.clock.instant())))
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(delete, Requester.USER, UserConfirmation(ask.actionFingerprint)))
        assertNull(g.tasks.get("t1"))
        assertNull(g.tasks.get("s1"), "subtasks go with their task")
    }

    @Test
    fun `every action on a personal task needs a confirmation marked personal`() = runTest {
        val g = TestGraph()
        val t = g.seed(personal = true)
        val actions = listOf(
            ProposedAction("tasks.update", UpdateTaskTool.args(t.copy(personal = false))),
            ProposedAction("tasks.complete", idArgs("taskId", "t1")),
            ProposedAction("tasks.reopen", idArgs("taskId", "t1")),
            ProposedAction("tasks.delete", idArgs("taskId", "t1")),
            ProposedAction("tasks.addSubtask", JsonObject(mapOf("parentId" to JsonPrimitive("t1"), "title" to JsonPrimitive("Step"), "durationMinutes" to JsonPrimitive(10)))),
        )
        for (a in actions) {
            val r = assertIs<ExecutionResult.NeedsConfirmation>(g.orchestrator.execute(a, Requester.USER), a.toolId)
            assertTrue(r.personal, a.toolId)
        }
        val complete = actions[1]
        val fp = g.orchestrator.fingerprint(complete)
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(complete, Requester.USER, UserConfirmation(fp)))
        assertEquals(TaskStatus.DONE, g.tasks.get("t1")!!.status)
    }

    @Test
    fun `malformed task edits are rejected`() = runTest {
        val g = TestGraph()
        val t = g.seed()
        val base = UpdateTaskTool.args(t)
        val bad = listOf(
            JsonObject(base + ("title" to JsonPrimitive(" "))),
            JsonObject(base + ("durationMinutes" to JsonPrimitive(2))),
            JsonObject(base + ("personal" to JsonPrimitive("yes"))),
            JsonObject(base + ("deadline" to JsonPrimitive("25:00"))),
            JsonObject(base - "date" + ("deadline" to JsonPrimitive("17:00"))),
            JsonObject(base + ("notes" to JsonPrimitive("x".repeat(Task.MAX_NOTES_LENGTH + 1)))),
            JsonObject(base + ("parentId" to JsonPrimitive("t9"))),
        )
        bad.forEach { assertEquals(ExecutionResult.Denied("INVALID_ARGUMENTS"), g.orchestrator.execute(ProposedAction("tasks.update", it), Requester.USER), it.toString()) }
        val nested = Task(id = "s1", title = "Step", parentId = "t1", date = day, createdAt = g.clock.instant())
        g.tasks.upsert(listOf(nested))
        val r = g.orchestrator.execute(
            ProposedAction("tasks.addSubtask", JsonObject(mapOf("parentId" to JsonPrimitive("s1"), "title" to JsonPrimitive("Deeper"), "durationMinutes" to JsonPrimitive(10)))),
            Requester.USER,
        )
        assertEquals(ExecutionResult.Failed("NESTED_SUBTASK"), r)
    }

    @Test
    fun `planner schedules subtasks instead of their parent`() {
        val now = TestGraph().clock.instant()
        val parent = Task(id = "p", title = "Move house", durationMinutes = 240, date = day, createdAt = now)
        val a = Task(id = "a", title = "Pack books", durationMinutes = 30, date = day, parentId = "p", createdAt = now)
        val b = Task(id = "b", title = "Book van", durationMinutes = 15, date = day, parentId = "p", createdAt = now, status = TaskStatus.DONE)
        val plan = DeterministicPlanner().plan(PlanRequest(day, listOf(parent, a, b), emptyList(), LocalTime.of(9, 0), LocalTime.of(21, 0), 10))
        assertEquals(listOf("a"), plan.blocks.map { it.refId })
    }

    @Test
    fun `settings are validated and the date order changes how 3-4 is read`() = runTest {
        val g = TestGraph()
        val us = UserPreferences(dateOrder = NumericDateOrder.MONTH_DAY, dayStart = LocalTime.of(7, 0), onboardingDone = true)
        assertIs<ExecutionResult.Denied>(g.orchestrator.execute(ProposedAction("settings.update", UpdateSettingsTool.args(us)), Requester.AGENT))
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(ProposedAction("settings.update", UpdateSettingsTool.args(us)), Requester.USER))
        assertEquals(us, g.preferences.get())

        val ready = assertIs<AgentProposal.Ready>(g.orchestrator.propose("dentist on 11/10 at 4pm"))
        assertEquals(LocalDate.parse("2026-11-10"), ready.previewPlans.first().date, "month first: November 10")
        g.preferences.set(us.copy(dateOrder = NumericDateOrder.DAY_MONTH))
        val dayFirst = assertIs<AgentProposal.Ready>(g.orchestrator.propose("dentist on 11/10 at 4pm"))
        assertEquals(LocalDate.parse("2026-10-11"), dayFirst.previewPlans.first().date, "day first: 11 October")

        val invalid = listOf(
            UpdateSettingsTool.args(UserPreferences()) + ("bufferMinutes" to JsonPrimitive("7")),
            UpdateSettingsTool.args(UserPreferences()) + ("dayEnd" to JsonPrimitive("10:00")),
            UpdateSettingsTool.args(UserPreferences()) - "onboardingDone",
        )
        invalid.forEach {
            assertEquals(ExecutionResult.Denied("INVALID_ARGUMENTS"), g.orchestrator.execute(ProposedAction("settings.update", JsonObject(it)), Requester.USER))
        }
    }
}
