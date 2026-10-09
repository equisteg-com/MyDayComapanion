package com.equisteg.mydaycompanion

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.equisteg.mydaycompanion.domain.ai.text.NumericDateOrder
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.security.ApprovalStep
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.AuthOutcome
import com.equisteg.mydaycompanion.ui.AuthPurpose
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.screens.TaskEditForm
import com.equisteg.mydaycompanion.ui.screens.parseClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Task editing, "Your day" settings and first-run setup (build doc F2, F10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TaskEditUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val graph = TestGraph()
    private val today = LocalDate.now(graph.clock)

    private fun task(id: String = "t1", personal: Boolean = false) =
        Task(id = id, title = "Write report", date = today, createdAt = graph.clock.instant(), personal = personal)

    @Test
    fun `a new owner goes through the four setup steps and their choices are saved`() {
        val viewModel = MainViewModel(TestServices(graph, onboarded = false), Dispatchers.Unconfined)
        compose.setContent { AppNavigation(viewModel) }
        waitFor(viewModel, "setup") { compose.onAllNodes(hasTestTag("onboarding")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithTag("day-start-7").performScrollTo().performClick()
        compose.onNodeWithTag("date-order-MONTH_DAY").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-next").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-questions").assertExists()
        compose.onNodeWithTag("onboarding-next").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-start").performScrollTo().performClick()

        waitFor(viewModel, "main screen") { compose.onAllNodes(hasTestTag("input")).fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { graph.preferences.get() }
        assertTrue(saved.onboardingDone)
        assertEquals(LocalTime.of(7, 0), saved.dayStart)
        assertEquals(NumericDateOrder.MONTH_DAY, saved.dateOrder)
    }

    @Test
    fun `the day's tasks are listed under the plan`() {
        runBlocking { graph.tasks.upsert(listOf(task())) }
        val viewModel = MainViewModel(TestServices(graph), Dispatchers.Unconfined)
        compose.setContent { AppNavigation(viewModel) }
        waitFor(viewModel, "task list") { compose.onAllNodes(hasTestTag("task-t1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("toggle-t1").performClick()
        waitFor(viewModel, "task done") { runBlocking { graph.tasks.get("t1")?.status } == TaskStatus.DONE }
        compose.onNodeWithTag("toggle-t1").performClick()
        waitFor(viewModel, "task open again") { runBlocking { graph.tasks.get("t1")?.status } == TaskStatus.OPEN }
    }

    @Test
    fun `the edit form returns every field and blocks unreadable times`() {
        var saved: Task? = null
        val original = task()
        compose.setContent { TaskEditForm(original, emptyList(), busy = false, onSave = { saved = it }, onDelete = {}, onAddSubtask = { _, _ -> }, onCancel = {}) }

        compose.onNodeWithTag("edit-title").performTextReplacement("Write the Q3 report")
        compose.onNodeWithTag("edit-notes").performTextReplacement("Use last quarter's numbers")
        compose.onNodeWithTag("edit-minutes").performTextReplacement("90")
        compose.onNodeWithTag("edit-start").performTextReplacement("half past nine")
        compose.onNodeWithTag("edit-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("edit-start").performTextReplacement("9:30")
        compose.onNodeWithTag("edit-deadline").performTextReplacement("5pm")
        compose.onNodeWithTag("edit-personal").performScrollTo().performClick()
        compose.onNodeWithTag("edit-save").performScrollTo().performClick()

        val s = assertNotNull(saved)
        assertEquals("Write the Q3 report", s.title)
        assertEquals("Use last quarter's numbers", s.notes)
        assertEquals(90, s.durationMinutes)
        assertEquals(LocalTime.of(9, 30), s.preferredStart)
        assertEquals(today.atTime(17, 0), s.deadline)
        assertTrue(s.personal)
    }

    @Test
    fun `clock text is read the way people type it`() {
        assertEquals(LocalTime.of(18, 0), parseClock("6pm").getOrNull())
        assertEquals(LocalTime.of(18, 15), parseClock("6.15 pm").getOrNull())
        assertEquals(LocalTime.of(9, 30), parseClock("0930").getOrNull())
        assertEquals(LocalTime.of(0, 0), parseClock("12am").getOrNull())
        assertNull(parseClock(" ").getOrThrow())
        assertTrue(parseClock("25:00").isFailure)
        assertTrue(parseClock("13pm").isFailure)
    }

    @Test
    fun `editing a personal task asks for the screen lock first`() {
        runBlocking { graph.tasks.upsert(listOf(task(personal = true))) }
        val viewModel = MainViewModel(TestServices(graph), Dispatchers.Unconfined)
        compose.setContent { AppNavigation(viewModel) }
        waitFor(viewModel, "task list") { compose.onAllNodes(hasTestTag("task-t1")).fetchSemanticsNodes().isNotEmpty() }

        viewModel.onSaveTask(task(personal = true).copy(title = "Doctor follow-up"))
        waitFor(viewModel, "approval") { viewModel.state.value.pending != null }
        assertEquals(listOf(ApprovalStep.TAP, ApprovalStep.DEVICE_CREDENTIAL), viewModel.state.value.pending?.steps)
        assertEquals("Write report", runBlocking { graph.tasks.get("t1")?.title }, "nothing changes before approval")

        compose.onNodeWithTag("confirm").performClick()
        waitFor(viewModel, "screen lock prompt") { viewModel.state.value.authRequest?.purpose == AuthPurpose.APPROVE }
        viewModel.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        waitFor(viewModel, "saved") { runBlocking { graph.tasks.get("t1")?.title } == "Doctor follow-up" }
    }

    @Test
    fun `deleting a task and its steps needs a tap`() {
        runBlocking {
            graph.tasks.upsert(listOf(task(), Task(id = "s1", title = "Outline", parentId = "t1", date = today, createdAt = graph.clock.instant())))
        }
        val viewModel = MainViewModel(TestServices(graph), Dispatchers.Unconfined)
        compose.setContent { AppNavigation(viewModel) }
        waitFor(viewModel, "task list") { compose.onAllNodes(hasTestTag("task-s1")).fetchSemanticsNodes().isNotEmpty() }

        viewModel.onDeleteTask("t1")
        waitFor(viewModel, "confirm dialog") { compose.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("confirm").performClick()
        waitFor(viewModel, "deleted") { runBlocking { graph.tasks.count() } == 0 }
    }

    private fun waitFor(viewModel: MainViewModel, what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(10_000, condition)
        } catch (e: ComposeTimeoutException) {
            val s = viewModel.state.value
            throw AssertionError(
                "Timed out waiting for $what. pending=${s.pending?.title}/${s.pending?.steps} auth=${s.authRequest} " +
                    "message=${s.message} busy=${s.busy} prefs=${s.preferences}",
                e,
            )
        }
    }
}
