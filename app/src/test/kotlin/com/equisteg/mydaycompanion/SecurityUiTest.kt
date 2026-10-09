package com.equisteg.mydaycompanion

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.AuthOutcome
import com.equisteg.mydaycompanion.ui.AuthPurpose
import com.equisteg.mydaycompanion.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** App lock, challenge questions and owner verification through the real UI (build doc §6, §8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SecurityUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val graph = TestGraph()

    @Test
    fun `the app opens locked and unlocks only after the screen lock says yes`() {
        val viewModel = MainViewModel(TestServices(graph, appLock = true))
        compose.setContent { AppNavigation(viewModel) }
        waitFor(viewModel, "lock screen") { compose.onAllNodes(hasTestTag("lock-screen")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasTestTag("input")).fetchSemanticsNodes().isEmpty(), "nothing of the agent is visible")

        compose.onNodeWithTag("unlock").performClick()
        waitFor(viewModel, "unlock prompt") { viewModel.state.value.authRequest?.purpose == AuthPurpose.UNLOCK }
        viewModel.onDeviceAuthResult(AuthOutcome.CANCELLED)
        waitFor(viewModel, "still locked") { viewModel.state.value.authRequest == null }
        assertTrue(viewModel.state.value.locked)

        compose.onNodeWithTag("unlock").performClick()
        waitFor(viewModel, "unlock prompt again") { viewModel.state.value.authRequest != null }
        viewModel.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        waitFor(viewModel, "main screen") { compose.onAllNodes(hasTestTag("input")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `leaving the app for longer than the lock delay locks it again`() {
        val viewModel = MainViewModel(TestServices(graph, appLock = false))
        compose.setContent { AppNavigation(viewModel) }
        waitFor(viewModel, "main screen") { compose.onAllNodes(hasTestTag("input")).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(viewModel.state.value.locked)

        // Turn the lock on with "lock at once"; this is an L4 change approved with the screen lock.
        viewModel.onSecuritySettingsChange(SecuritySettings(appLockEnabled = true, lockAfterMinutes = 0))
        waitFor(viewModel, "confirm dialog") { compose.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("confirm").performClick()
        waitFor(viewModel, "approval prompt") { viewModel.state.value.authRequest?.purpose == AuthPurpose.APPROVE }
        viewModel.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        waitFor(viewModel, "settings saved") { viewModel.state.value.security.appLockEnabled }

        viewModel.onAppBackground()
        viewModel.onAppForeground()
        waitFor(viewModel, "lock screen") { compose.onAllNodes(hasTestTag("lock-screen")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `with challenge questions, delete everything also needs a correct answer`() {
        runBlocking { graph.tasks.upsert(listOf(Task("t", "x", createdAt = graph.clock.instant()))) }
        val services = TestServices(graph, appLock = false)
        val viewModel = MainViewModel(services)
        compose.setContent { AppNavigation(viewModel) }

        viewModel.onSaveChallenges(
            listOf("First school?" to "St Mary's", "Mother's home town?" to "Madurai", "Childhood nickname?" to "Chinnu"),
        )
        waitFor(viewModel, "confirm dialog") { compose.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("confirm").performClick()
        waitFor(viewModel, "approval prompt") { viewModel.state.value.authRequest != null }
        viewModel.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        waitFor(viewModel, "questions saved") { viewModel.state.value.hasChallenges }
        assertEquals(3, runBlocking { graph.security.challenges().size })
        assertTrue(graph.audit.entries.none { it.detail.orEmpty().contains("Madurai", ignoreCase = true) })

        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("delete-all").performScrollTo().performClick()
        waitFor(viewModel, "confirm dialog") { compose.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("confirm").performClick()
        waitFor(viewModel, "approval prompt") { viewModel.state.value.authRequest != null }
        viewModel.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        waitFor(viewModel, "challenge") { compose.onAllNodes(hasTestTag("challenge-answer")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("challenge-answer").performTextInput("wrong answer")
        compose.onNodeWithTag("challenge-submit").performClick()
        waitFor(viewModel, "wrong answer shown") { viewModel.state.value.challenge?.error != null }
        assertEquals(1, runBlocking { graph.tasks.count() }, "a wrong answer deletes nothing")

        val question = viewModel.state.value.challenge!!.question
        val right = mapOf("First school?" to "st marys", "Mother's home town?" to "MADURAI", "Childhood nickname?" to "chinnu")[question]!!
        viewModel.onChallengeAnswer(right)
        waitFor(viewModel, "wiped") { viewModel.state.value.wiped }
        assertEquals(0, runBlocking { graph.tasks.count() })
        assertTrue(services.wipedStorage)
    }

    private fun waitFor(viewModel: MainViewModel, what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(10_000, condition)
        } catch (e: ComposeTimeoutException) {
            val s = viewModel.state.value
            throw AssertionError(
                "Timed out waiting for $what. locked=${s.locked} auth=${s.authRequest} challenge=${s.challenge} " +
                    "pending=${s.pending?.title}/${s.pending?.steps} message=${s.message} busy=${s.busy}",
                e,
            )
        }
    }
}
