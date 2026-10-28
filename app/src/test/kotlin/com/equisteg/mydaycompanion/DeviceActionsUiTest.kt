package com.equisteg.mydaycompanion

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.domain.tools.DeviceAction
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.AuthOutcome
import com.equisteg.mydaycompanion.ui.AuthPurpose
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.screens.TaskEditForm
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DeviceActionsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val graph = TestGraph()
    private val services = TestServices(graph)
    private val viewModel = MainViewModel(services, Dispatchers.Unconfined)
    private val task = Task("t1", "Call Raj", date = LocalDate.parse("2026-10-06"), preferredStart = LocalTime.of(16, 0), createdAt = graph.clock.instant())

    @Test
    fun `a text message draft needs Confirm and the screen lock before the messages app opens`() {
        compose.setContent {
            TaskEditForm(task, emptyList(), busy = false, onSave = {}, onDelete = {}, onAddSubtask = { _, _ -> }, onCancel = {}, onDeviceAction = viewModel::onDeviceAction)
        }
        compose.onNodeWithTag("action-contact").performScrollTo().performTextInput("9845012345")
        compose.onNodeWithTag("action-message").performScrollTo().performClick()
        compose.waitUntil(5_000) { viewModel.state.value.pending != null }
        assertTrue(graph.phone.launched.isEmpty(), "nothing opens before Confirm")

        viewModel.onConfirmPending()
        compose.waitUntil(5_000) { viewModel.state.value.authRequest?.purpose == AuthPurpose.APPROVE }
        assertTrue(graph.phone.launched.isEmpty(), "nothing opens before the screen lock")

        viewModel.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        compose.waitUntil(5_000) { graph.phone.launched.isNotEmpty() }
        assertEquals(DeviceAction.ComposeMessage(DeviceAction.Channel.SMS, "9845012345", "Call Raj"), graph.phone.launched.single())
    }

    @Test
    fun `read my day speaks the summary`() {
        runBlocking { graph.tasks.upsert(listOf(task)) }
        compose.setContent { AppNavigation(viewModel) }
        viewModel.onReplan()
        compose.waitUntil(5_000) { runBlocking { graph.plans.get(task.date!!) } != null }
        compose.onNodeWithTag("read-day").performScrollTo().performClick()
        compose.waitUntil(5_000) { graph.phone.spoken.isNotEmpty() }
        assertTrue(graph.phone.spoken.single().startsWith("1 task today."), graph.phone.spoken.single())
        assertTrue(compose.onAllNodes(hasTestTag("voice")).fetchSemanticsNodes().isNotEmpty(), "mic button shown")
    }
}
