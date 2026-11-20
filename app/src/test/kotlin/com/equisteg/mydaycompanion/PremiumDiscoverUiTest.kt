package com.equisteg.mydaycompanion

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.ui.screens.TaskDiscovery
import com.equisteg.mydaycompanion.ui.screens.WeekOverview
import com.equisteg.mydaycompanion.ui.screens.weekSummaries
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PremiumDiscoverUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 11, 20)
    private val timestamp = Instant.parse("2026-11-20T01:00:00Z")

    private fun task(id: String, name: String, status: TaskStatus = TaskStatus.OPEN,
                     priority: Priority = Priority.MEDIUM, date: LocalDate? = this.date) =
        Task(id = id, title = name, status = status, priority = priority, date = date, createdAt = timestamp)

    @Test fun `task discovery can search and filter without a network service`() {
        val tasks = listOf(task("a", "Write report", priority = Priority.HIGH),
            task("b", "Read book"), task("c", "Review report", TaskStatus.DONE))
        compose.setContent { MyDayCompanionTheme { TaskDiscovery(tasks, onToggle = {}, onEdit = {}) } }

        compose.onNodeWithTag("task-search-toggle").performClick()
        compose.onNodeWithTag("task-search-input").performTextInput("report")
        compose.onNodeWithTag("task-search-count").assertTextContains("2 matching tasks")
        compose.onNodeWithTag("task-status-OPEN").performClick()
        compose.onNodeWithTag("task-search-count").assertTextContains("1 matching tasks")
        compose.onNodeWithTag("task-priority-HIGH").performClick()
        compose.onNodeWithTag("task-search-count").assertTextContains("1 matching tasks")
    }

    @Test fun `weekly overview uses monday start and keeps cancelled entries out`() {
        val monday = date.minusDays(4)
        val tasks = listOf(task("a", "Monday", date = monday),
            task("b", "Finished", TaskStatus.DONE),
            task("c", "Skipped", TaskStatus.CANCELLED))
        val week = weekSummaries(date, tasks)
        assertEquals(7, week.size)
        assertEquals(monday, week.first().date)
        assertEquals(1, week.first().planned)
        assertEquals(1, week[4].planned)
        assertEquals(1, week[4].done)
    }

    @Test fun `week navigation selects another date without changing tasks`() {
        var selected: LocalDate? = null
        compose.setContent { MyDayCompanionTheme { WeekOverview(date, emptyList(), onSelectDate = { selected = it }) } }
        compose.onNodeWithTag("week-day-2026-11-16").performClick()
        assertEquals(LocalDate.of(2026, 11, 16), selected)
    }
}
