package com.equisteg.mydaycompanion.domain.planner

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.PlanIssueType
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicPlannerTest {
    private val planner = DeterministicPlanner()
    private val date = LocalDate.of(2026, 10, 7)
    private val t0 = Instant.parse("2026-10-06T00:00:00Z")

    private fun task(id: String, minutes: Int, priority: Priority = Priority.MEDIUM, deadline: LocalTime? = null) =
        Task(id, id, priority, minutes, date, deadline?.let { date.atTime(it) }, createdAt = t0)

    private fun meeting(id: String, at: LocalTime, minutes: Int = 60) = FixedEvent(id, id, date, at, minutes)

    private fun request(tasks: List<Task>, events: List<FixedEvent> = emptyList(), notBefore: LocalTime? = null) =
        PlanRequest(date, tasks, events, LocalTime.of(9, 0), LocalTime.of(21, 0), 10, notBefore)

    @Test
    fun `magic moment plan keeps the meeting fixed and fits every task`() {
        val req = request(
            listOf(task("proposal", 120, Priority.HIGH), task("call", 15), task("workout", 60), task("groceries", 45)),
            listOf(meeting("meeting", LocalTime.of(11, 0))),
        )
        val plan = planner.plan(req)

        assertEquals(emptyList(), PlanValidator.validate(plan, req))
        assertTrue(plan.isRealistic)
        val byId = plan.blocks.associateBy { it.refId }
        assertEquals(LocalTime.of(9, 0), byId.getValue("proposal").start)
        assertEquals(LocalTime.of(11, 0), byId.getValue("meeting").start)
        assertEquals(BlockKind.FIXED_EVENT, byId.getValue("meeting").kind)
        assertEquals(LocalTime.of(12, 0), byId.getValue("call").start)
    }

    @Test
    fun `overloaded day reports unscheduled tasks instead of squeezing them in`() {
        val req = request(List(5) { task("t$it", 150) })
        val plan = planner.plan(req)
        assertEquals(emptyList(), PlanValidator.validate(plan, req))
        assertEquals(listOf("t4"), plan.unscheduledTaskIds)
        assertTrue(plan.issues.any { it.type == PlanIssueType.OVERLOADED && it.refId == "t4" })
    }

    @Test
    fun `deadline tasks go first and impossible deadlines are flagged`() {
        val req = request(listOf(task("later", 60, Priority.HIGH), task("due", 60, deadline = LocalTime.of(10, 0)), task("tooLate", 120, deadline = LocalTime.of(10, 30))))
        val plan = planner.plan(req)
        val byId = plan.blocks.associateBy { it.refId }
        assertEquals(LocalTime.of(9, 0), byId.getValue("due").start)
        assertTrue(plan.issues.any { it.type == PlanIssueType.DEADLINE_AT_RISK && it.refId == "tooLate" })
    }

    @Test
    fun `overlapping fixed events are reported as a conflict`() {
        val req = request(emptyList(), listOf(meeting("a", LocalTime.of(10, 0)), meeting("b", LocalTime.of(10, 30))))
        val plan = planner.plan(req)
        assertTrue(plan.issues.any { it.type == PlanIssueType.FIXED_EVENT_CONFLICT })
    }

    @Test
    fun `replanning never schedules in the past and skips finished tasks`() {
        val done = task("done", 30).copy(status = TaskStatus.DONE)
        val req = request(listOf(done, task("a", 30), task("b", 30)), notBefore = LocalTime.of(14, 7))
        val plan = planner.plan(req)
        assertEquals(emptyList(), PlanValidator.validate(plan, req))
        assertTrue(plan.blocks.none { it.refId == "done" })
        assertEquals(LocalTime.of(14, 10), plan.blocks.first().start)
    }

    @Test
    fun `planner is deterministic`() {
        val req = request(listOf(task("a", 45), task("b", 30, Priority.HIGH)), listOf(meeting("m", LocalTime.of(13, 0))))
        assertEquals(planner.plan(req), planner.plan(req))
    }

    @Test
    fun `validator rejects a plan that moves a fixed event or overlaps tasks`() {
        val req = request(listOf(task("a", 60), task("b", 60)), listOf(meeting("m", LocalTime.of(9, 30))))
        val good = planner.plan(req)
        val moved = good.copy(blocks = good.blocks.map { if (it.refId == "m") it.copy(start = LocalTime.of(15, 0), end = LocalTime.of(16, 0)) else it })
        val overlapping = good.copy(blocks = good.blocks.map { if (it.refId == "b") it.copy(start = good.blocks.first { b -> b.refId == "a" }.start) else it })
        assertTrue(PlanValidator.validate(moved, req).isNotEmpty())
        assertTrue(PlanValidator.validate(overlapping, req).isNotEmpty())
    }

    @Test
    fun `a preferred start places the task at that time when free, and earlier work still fills the morning`() {
        val evening = task("walk", 30).copy(preferredStart = LocalTime.of(18, 0))
        val busyAtSix = task("run", 60).copy(preferredStart = LocalTime.of(11, 30))
        val req = request(listOf(task("report", 60), evening, busyAtSix), listOf(meeting("meeting", LocalTime.of(11, 0))))
        val plan = planner.plan(req)

        assertEquals(emptyList(), PlanValidator.validate(plan, req))
        val byId = plan.blocks.associateBy { it.refId }
        assertEquals(LocalTime.of(18, 0), byId.getValue("walk").start)
        assertEquals(LocalTime.of(12, 0), byId.getValue("run").start, "the meeting runs to 12:00, so the run starts right after it")
        assertEquals(LocalTime.of(9, 0), byId.getValue("report").start)
    }

    @Test
    fun `a preferred start that cannot fit falls back to the first free gap`() {
        val late = task("late", 120).copy(preferredStart = LocalTime.of(20, 0))
        val req = request(listOf(late))
        val plan = planner.plan(req)
        assertEquals(emptyList(), PlanValidator.validate(plan, req))
        assertEquals(LocalTime.of(9, 0), plan.blocks.single().start)
    }
}
