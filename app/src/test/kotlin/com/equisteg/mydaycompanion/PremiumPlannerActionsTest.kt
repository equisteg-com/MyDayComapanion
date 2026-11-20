package com.equisteg.mydaycompanion

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.ui.screens.QuickReschedule
import com.equisteg.mydaycompanion.ui.screens.ReversibleMove
import com.equisteg.mydaycompanion.ui.screens.moveToNextDay
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PremiumPlannerActionsTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 11, 20)
    private val stamp = Instant.parse("2026-11-20T00:00:00Z")
    private fun task(id: String = "task", title: String = "Finish proposal") =
        Task(id = id, title = title, date = day, createdAt = stamp,
            deadline = day.atTime(17, 30))

    @Test fun `move preserves the clock time of an existing deadline`() {
        val original = task()
        val moved = moveToNextDay(original, day)
        assertEquals(day.plusDays(1), moved.date)
        assertEquals(day.plusDays(1).atTime(17, 30), moved.deadline)
        assertEquals(original.id, moved.id)
        assertEquals(original.preferredStart, moved.preferredStart)
    }

    @Test fun `undo is only available for the same unmodified task`() {
        val original = task()
        val move = ReversibleMove(original, moveToNextDay(original, day))
        assertFalse(move.canUndo(original))
        assertTrue(move.canUndo(move.moved.copy(updatedAt = stamp.plusSeconds(5))))
        assertFalse(move.canUndo(move.moved.copy(title = "Edited after moving")))
        assertFalse(move.canUndo(null))
    }

    @Test fun `routine, cancelled and completed tasks cannot be quick moved`() {
        val original = task()
        val routines = listOf(original.copy(routineId = "habit"),
            original.copy(status = TaskStatus.CANCELLED),
            original.copy(status = TaskStatus.DONE))
        compose.setContent { MyDayCompanionTheme {
            QuickReschedule(day, routines, false, false, {}, {})
        } }
        compose.onNodeWithTag("quick-reschedule").assertDoesNotExist()
    }

    @Test fun `quick move and undo buttons call their corresponding actions`() {
        val original = task()
        var moved: Task? = null
        var undos = 0
        compose.setContent { MyDayCompanionTheme {
            QuickReschedule(day, listOf(original), true, false,
                onMove = { moved = it }, onUndo = { undos++ })
        } }
        compose.onNodeWithTag("move-tomorrow-task").performClick()
        assertEquals(original.id, moved?.id)
        compose.onNodeWithTag("undo-quick-move").performClick()
        assertEquals(1, undos)
    }
}
