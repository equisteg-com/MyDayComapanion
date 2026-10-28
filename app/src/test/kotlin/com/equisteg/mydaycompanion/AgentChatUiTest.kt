package com.equisteg.mydaycompanion

import android.os.Looper
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.ui.AuthOutcome
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.screens.PlanScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UPD-011: the agent chat, its suggestions (move, roll over, mark done) and focus sessions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AgentChatUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val graph = TestGraph()
    private val services = TestServices(graph)
    private val today = LocalDate.now(graph.clock) // 2026-10-06, a Tuesday, 07:00
    private val friday = LocalDate.of(2026, 10, 9)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun seed(vararg tasks: Task) = runBlocking {
        graph.preferences.set(UserPreferences(onboardingDone = true))
        graph.tasks.upsert(tasks.toList())
    }

    private fun task(id: String, title: String, date: LocalDate? = today, personal: Boolean = false) =
        Task(id, title, date = date, personal = personal, createdAt = graph.clock.instant())

    private fun MainViewModel.say(text: String) {
        onInputChange(text)
        onSubmit()
        idle()
    }

    private fun MainViewModel.lastReply() = state.value.chat.last { !it.fromOwner }.text

    @Test
    fun `what's next and what's left are answered from the plan`() {
        seed(task("a", "Write report"), task("b", "Call Raj"))
        runBlocking {
            graph.plans.save(
                DayPlan(
                    today,
                    listOf(
                        PlanBlock("a", "Write report", BlockKind.TASK, LocalTime.of(7, 0), LocalTime.of(8, 0)),
                        PlanBlock("b", "Call Raj", BlockKind.TASK, LocalTime.of(9, 0), LocalTime.of(9, 15)),
                    ),
                    emptyList(),
                    emptyList(),
                    "test",
                ),
            )
        }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { PlanScreen(vm) }

        compose.onNodeWithTag("ask-0").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("chat-agent") and hasText("Now: Write report, until 08:00. Next: Call Raj at 09:00.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ask-1").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.state.value.chat.lastOrNull()?.text == "0 of 2 done. Still to do: Write report, Call Raj." }
        assertTrue(graph.audit.entries.none { "Write report" in it.detail.orEmpty() }, "chat is never written to History")
    }

    @Test
    fun `move a task to Friday after one tap`() {
        seed(task("a", "Call Raj"))
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { PlanScreen(vm) }
        vm.say("move call raj to friday")

        compose.onNodeWithTag("suggestion").assertTextContains("Move \"Call Raj\" to Friday 9 October?", substring = true)
        assertEquals(today, runBlocking { graph.tasks.get("a")?.date }, "nothing moves before the tap")
        compose.onNodeWithTag("suggestion-accept").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.tasks.get("a")?.date } == friday }
        assertNull(vm.state.value.suggestion)
        assertTrue(graph.audit.entries.any { it.action == "tasks.update" })
    }

    @Test
    fun `when several tasks fit, the agent asks which one`() {
        seed(task("a", "Call Raj"), task("b", "Call mum"))
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        vm.say("push call to 4pm")
        val choice = assertNotNull(vm.state.value.choice)
        assertEquals(setOf("a", "b"), choice.options.map { it.id }.toSet())
        vm.onChoose("b")
        idle()
        assertEquals("Move \"Call mum\" to today at 16:00?", vm.state.value.suggestion?.text)
        vm.onAcceptSuggestion()
        idle()
        assertEquals(LocalTime.of(16, 0), runBlocking { graph.tasks.get("b")?.preferredStart })
    }

    @Test
    fun `moving a personal task still needs the owner's verification`() {
        seed(task("p", "Pick up medicine", personal = true))
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        vm.say("move medicine to friday")
        vm.onAcceptSuggestion()
        idle()
        assertNotNull(vm.state.value.authRequest, "screen lock asked")
        assertEquals(today, runBlocking { graph.tasks.get("p")?.date })
        vm.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        idle()
        assertEquals(friday, runBlocking { graph.tasks.get("p")?.date })
    }

    @Test
    fun `unfinished tasks from yesterday roll over from the banner`() {
        seed(task("y", "Finish slides", date = today.minusDays(1)), task("t", "Today's task"))
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { PlanScreen(vm) }
        compose.onNodeWithTag("rollover").assertTextContains("1 unfinished task from an earlier day")
        compose.onNodeWithTag("rollover-accept").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.tasks.get("y")?.date } == today }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("rollover")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `a focus session counts down and offers to mark the task done`() {
        seed(task("a", "Write report"))
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { PlanScreen(vm) }
        vm.say("focus 30 min on report")
        compose.onNodeWithTag("focus-left").assertTextContains("30:00")

        vm.finishFocus(assertNotNull(vm.state.value.focus))
        idle()
        assertNull(vm.state.value.focus)
        assertEquals("Mark \"Write report\" done?", vm.state.value.suggestion?.text)
        vm.onAcceptSuggestion()
        idle()
        assertEquals(TaskStatus.DONE, runBlocking { graph.tasks.get("a")?.status })

        vm.say("focus")
        assertEquals(25, vm.state.value.focus?.minutes)
        vm.onStopFocus()
        assertNull(vm.state.value.focus)
    }

    @Test
    fun `text that only looks like a command is planned as usual`() {
        seed()
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        vm.say("move the car to the garage tomorrow")
        compose.waitUntil(5_000) { vm.state.value.proposal != null }
        assertTrue(vm.state.value.chat.isEmpty())
        assertNull(vm.state.value.suggestion)
    }
}
