package com.equisteg.mydaycompanion.domain.productivity

import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PremiumOperationsTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val created = Instant.parse("2026-11-21T00:00:00Z")
    private fun task(id: String, date: LocalDate? = today, status: TaskStatus = TaskStatus.OPEN,
                     personal: Boolean = false, priority: Priority = Priority.MEDIUM) =
        Task(id, id, date = date, status = status, personal = personal,
            priority = priority, createdAt = created, durationMinutes = 30)

    @Test fun `safe batch plan updates only the specified non-personal tasks`() {
        val tasks = listOf(task("a"), task("b"), task("private", personal = true))
        val plan = BatchEditPlanner.plan(tasks, setOf("a", "b"), BatchEditAction.COMPLETE, today).getOrThrow()
        assertEquals(2, plan.changes.size)
        assertEquals(setOf(today), plan.affectedDates)
        assertTrue(plan.changes.all { (_, after) -> after.status == TaskStatus.DONE })
        assertEquals(TaskStatus.OPEN, tasks[0].status)
    }

    @Test fun `a protected or ineligible selection fails before any writes`() {
        val tasks = listOf(task("a"), task("b", personal = true), task("routine").copy(routineId = "r"))
        assertTrue(BatchEditPlanner.plan(tasks, setOf("a", "b"), BatchEditAction.COMPLETE, today).isFailure)
        assertTrue(BatchEditPlanner.plan(tasks, setOf("routine"), BatchEditAction.COMPLETE, today).isFailure)
        assertTrue(BatchEditPlanner.plan(tasks, setOf("missing"), BatchEditAction.COMPLETE, today).isFailure)
        assertTrue(BatchEditPlanner.plan(tasks, emptySet(), BatchEditAction.COMPLETE, today).isFailure)
        assertTrue(BatchEditPlanner.plan(listOf(task("a"),task("a")), setOf("a"), BatchEditAction.COMPLETE, today).isFailure)
        val twentyOne = (0..20).map { task("t$it") }
        assertTrue(BatchEditPlanner.plan(twentyOne, twentyOne.map { it.id }.toSet(), BatchEditAction.COMPLETE, today).isFailure)
    }

    @Test fun `bulk move retains deadline local times and excludes future tasks`() {
        val due = task("invoice").copy(date = today.minusDays(1),
            deadline = today.minusDays(1).atTime(LocalTime.of(17, 30)))
        val draft = BatchEditPlanner.plan(listOf(due), setOf("invoice"),
            BatchEditAction.MOVE_TOMORROW, today).getOrThrow()
        val moved = draft.changes.single().second
        assertEquals(today.plusDays(1), moved.date)
        assertEquals(today.plusDays(1).atTime(17, 30), moved.deadline)
        assertEquals(setOf(today.minusDays(1),today.plusDays(1)),draft.affectedDates)
        assertTrue(BatchEditPlanner.plan(listOf(task("future", today.plusDays(3))),
            setOf("future"), BatchEditAction.MOVE_TOMORROW,today).isFailure)
    }

    @Test fun `priority bulk edits never silently alter already-high tasks`() {
        val low = task("low", priority=Priority.LOW)
        assertEquals(Priority.HIGH, BatchEditPlanner.plan(listOf(low),setOf("low"),
            BatchEditAction.PRIORITY_HIGH,today).getOrThrow().changes.single().second.priority)
        assertTrue(BatchEditPlanner.plan(listOf(task("high",priority=Priority.HIGH)),
            setOf("high"),BatchEditAction.PRIORITY_HIGH,today).isFailure)
    }

    @Test fun `month grid respects Sunday US and Monday UK week starts`() {
        val us = MonthOverview.grid(YearMonth.of(2026, 11), emptyList(), today, Locale.US)
        val uk = MonthOverview.grid(YearMonth.of(2026, 11), emptyList(), today, Locale.UK)
        assertEquals(LocalDate.of(2026,11,1),us.first().date)
        assertEquals(LocalDate.of(2026,10,26),uk.first().date)
        assertTrue(us.size in listOf(28,35,42))
        assertTrue(uk.size in listOf(28,35,42))
    }

    @Test fun `month summary excludes cancelled and nested tasks`() {
        val date=LocalDate.of(2026,11,3)
        val items=listOf(task("open",date),task("done",date,TaskStatus.DONE),
            task("cancel",date,TaskStatus.CANCELLED),task("step",date).copy(parentId="open"))
        val day=MonthOverview.grid(YearMonth.from(date),items,today,Locale.US).first{it.date==date}
        assertEquals(2,day.planned)
        assertEquals(1,day.done)
        assertEquals(1,day.overdueOpen)
    }

    @Test fun `calendar previews upcoming routines and never doubles materialized or cancelled instances`() {
        val routine=Routine(id="walk",title="Walk",frequency=RoutineFrequency.DAILY,
            preferredStart=LocalTime.of(7,0),durationMinutes=30,createdAt=created)
        val target=today.plusDays(1)
        val empty=MonthOverview.grid(YearMonth.from(target),emptyList(),today,Locale.UK,listOf(routine))
            .first { it.date == target }
        assertEquals(1,empty.planned)
        assertEquals(1,empty.projectedRoutines)

        val generated=task("walk-instance",target).copy(routineId="walk")
        val actual=MonthOverview.grid(YearMonth.from(target),listOf(generated),today,Locale.UK,listOf(routine))
            .first { it.date == target }
        assertEquals(1,actual.planned)
        assertEquals(0,actual.projectedRoutines)

        val skipped=generated.copy(status=TaskStatus.CANCELLED)
        val cancelled=MonthOverview.grid(YearMonth.from(target),listOf(skipped),today,Locale.UK,listOf(routine))
            .first { it.date == target }
        assertEquals(0,cancelled.planned)
        assertEquals(0,cancelled.projectedRoutines)
    }

    @Test fun `coach recommends overdue work and uses fixed task counts`() {
        val items = listOf(task("overdue",today.minusDays(1)),
            task("today-open"),task("done",today,TaskStatus.DONE),task("backlog",null))
        val report=ProductivityCoach.report(items,today)
        assertEquals(2,report.scheduled)
        assertEquals(1,report.completed)
        assertEquals(1,report.overdue)
        assertEquals(1,report.backlog)
        assertEquals(30,report.remainingMinutes)
        assertTrue(report.recommendation.contains("overdue"))
    }

    @Test fun `capture templates are editable prompts and stable unique IDs`() {
        val templates=CaptureTemplates.builtIn
        assertTrue(templates.size>=7)
        assertEquals(templates.size,templates.map{it.id}.toSet().size)
        assertTrue(templates.all{it.prompt.isNotBlank() && it.label.isNotBlank()})
        assertFalse(templates.any{it.prompt.contains("http")})
    }
}
