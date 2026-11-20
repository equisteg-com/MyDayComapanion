package com.equisteg.mydaycompanion.domain.productivity

import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeShiftTest {
    private val day = LocalDate.of(2026,11,21)
    private val open = Task("t", "Flexible", date=day, durationMinutes=30,
        createdAt=Instant.parse("2026-11-21T00:00:00Z"))
    private val nine = LocalTime.of(9,0)
    private val end = LocalTime.of(21,0)

    @Test fun `visual shift changes a soft preference in 15-minute increments`() {
        val later = TimeShift.change(open,nine,2,nine,end).getOrThrow()
        assertEquals(LocalTime.of(9,30),later.preferredStart)
        assertEquals(open.date,later.date)
        assertEquals(open.deadline,later.deadline)
    }

    @Test fun `cannot move earlier than start or later than available day window`() {
        assertTrue(TimeShift.change(open,nine,-1,nine,end).isFailure)
        assertTrue(TimeShift.change(open,LocalTime.of(20,30),1,nine,end).isFailure)
        assertEquals(LocalTime.of(20,30),
            TimeShift.change(open,LocalTime.of(20,15),2,nine,end).getOrThrow().preferredStart)
    }

    @Test fun `fixed policies exclude personal routine cancelled and unscheduled work`() {
        for (task in listOf(open.copy(personal=true),open.copy(routineId="r"),
            open.copy(status=TaskStatus.CANCELLED),open.copy(date=null))) {
            assertTrue(TimeShift.change(task,nine,1,nine,end).isFailure)
        }
        assertTrue(TimeShift.change(open,nine,0,nine,end).isFailure)
        assertTrue(TimeShift.change(open,nine,49,nine,end).isFailure)
    }

    @Test fun `reports when duration cannot fit the day`() {
        assertTrue(TimeShift.change(open.copy(durationMinutes=120),nine,1,
            nine,LocalTime.of(10,0)).isFailure)
    }
}
