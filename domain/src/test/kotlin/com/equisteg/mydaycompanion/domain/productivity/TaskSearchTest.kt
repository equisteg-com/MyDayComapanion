package com.equisteg.mydaycompanion.domain.productivity

import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskSearchTest {
    private val instant = Instant.parse("2026-11-20T00:00:00Z")
    private fun task(id: String, title: String, status: TaskStatus = TaskStatus.OPEN,
                     priority: Priority = Priority.MEDIUM, notes: String? = null, order: Long = 0) =
        Task(id = id, title = title, status = status, priority = priority, notes = notes,
            createdAt = instant, updatedAt = instant.plusSeconds(order))

    @Test
    fun `search finds all words across title and notes without matching partial phrases by accident`() {
        val tasks = listOf(task("a", "Design report", notes = "Client review"),
            task("b", "Review design", notes = "Quarterly report"),
            task("c", "Design presentation"))
        val found = TaskSearch.find(tasks, "  REVIEW   design  ")
        assertEquals(2, found.totalMatches)
        assertEquals(setOf("a", "b"), found.items.map { it.id }.toSet())
    }

    @Test
    fun `filters are independent and cancelled tasks stay hidden`() {
        val tasks = listOf(task("a", "Inbox", priority = Priority.HIGH),
            task("b", "Inbox", TaskStatus.DONE, Priority.HIGH),
            task("c", "Inbox", TaskStatus.CANCELLED, Priority.HIGH),
            task("d", "Inbox", priority = Priority.LOW))
        assertEquals(listOf("a"), TaskSearch.find(tasks, "inbox", TaskStateFilter.OPEN, Priority.HIGH).items.map { it.id })
        assertEquals(listOf("b"), TaskSearch.find(tasks, "inbox", TaskStateFilter.DONE).items.map { it.id })
        assertEquals(3, TaskSearch.find(tasks, "inbox").totalMatches)
    }

    @Test
    fun `results are capped newest first and report the full match count`() {
        val tasks = (0 until 150).map { task("t$it", "Work item $it", order = it.toLong()) }
        val result = TaskSearch.find(tasks, "work")
        assertEquals(150, result.totalMatches)
        assertEquals(TaskSearch.MAX_RESULTS, result.items.size)
        assertEquals("t149", result.items.first().id)
        assertEquals("t120", result.items.last().id)
        assertTrue(result.hasMore)
        assertFalse(TaskSearch.find(tasks, "not-present").hasMore)
    }

    @Test
    fun `private task content is searched locally without displaying other tasks`() {
        val tasks = listOf(task("private", "Appointment", notes = "Confidential", order = 2),
            task("public", "Groceries"))
        assertEquals(listOf("private"), TaskSearch.find(tasks, "confidential").items.map { it.id })
    }

    @Test
    fun `hundred thousand local records remain bounded in output`() {
        val tasks = (0 until 100_000).map {
            task("id$it", "Daily task", order = it.toLong())
        }
        val result = TaskSearch.find(tasks, "daily")
        assertEquals(100_000, result.totalMatches)
        assertEquals(TaskSearch.MAX_RESULTS, result.items.size)
        assertEquals("id99999", result.items.first().id)
    }
}
