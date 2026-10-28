package com.equisteg.mydaycompanion

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.equisteg.mydaycompanion.data.privacy.ContentResolverExportTargets
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.security.ChallengeVerifier
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.ui.AuthOutcome
import com.equisteg.mydaycompanion.ui.AuthPurpose
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TestServices(val graph: TestGraph, appLock: Boolean = false, onboarded: Boolean = true) : AppServices {
    override val clock = graph.clock
    override val orchestrator = graph.orchestrator
    override val tasks = graph.tasks
    override val plans = graph.plans
    override val audit = graph.audit
    override val flags = com.equisteg.mydaycompanion.domain.config.FeatureFlags()
    override val exportTargets = ContentResolverExportTargets(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver)
    override val security = graph.security
    override val challenges = ChallengeVerifier(graph.security, graph.clock::instant)
    override val preferences = graph.preferences
    override val routines = graph.routines
    override val maintenance = com.equisteg.mydaycompanion.domain.agent.DailyMaintenance(graph.orchestrator, graph.tasks, graph.plans, graph.routines, graph.clock, remindersReady = { false })
    var wipedStorage = false

    init {
        runBlocking {
            graph.security.saveSettings(SecuritySettings(appLockEnabled = appLock))
            graph.preferences.set(UserPreferences(onboardingDone = onboarded))
        }
    }

    override fun wipeLocalStorage(): Boolean {
        wipedStorage = true
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
// A phone-sized screen so the saved plan below the input is laid out by the LazyColumn.
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class MagicMomentUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val graph = TestGraph()
    private val services = TestServices(graph)
    private val viewModel = MainViewModel(services, Dispatchers.Unconfined)

    @Test
    fun `type a day, review the proposal, add it, see the saved plan`() {
        compose.setContent { AppNavigation(viewModel) }

        compose.onNodeWithTag("input").performTextInput(
            "Tomorrow I need to finish the proposal, call Raj, work out, buy groceries, and I have a meeting at 11.",
        )
        compose.onNodeWithTag("submit").performClick()
        waitFor("proposal") { compose.onAllNodes(hasTestTag("proposal")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, runBlocking { graph.tasks.count() }, "nothing saved before confirmation")

        compose.onNodeWithTag("accept").assertIsEnabled().performClick()
        waitFor("saved plan for 2026-10-07") { compose.onAllNodes(hasTestTag("plan-2026-10-07")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Finish the proposal") and hasAnyAncestor(hasTestTag("plan-2026-10-07"))).assertIsDisplayed()
        assertEquals(4, runBlocking { graph.tasks.count() })
    }

    @Test
    fun `messy text over several days is understood and saved to each day`() {
        compose.setContent { AppNavigation(viewModel) }

        compose.onNodeWithTag("input").performTextInput("today finish report, tmrw call Raj 10am, fri dentist 4:30pm")
        compose.onNodeWithTag("submit").performClick()
        waitFor("proposal") { compose.onAllNodes(hasTestTag("proposal")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("preview-2026-10-07").assertExists()
        compose.onNodeWithTag("preview-2026-10-09").assertExists()

        compose.onNodeWithTag("accept").performClick()
        waitFor("saved message") { viewModel.state.value.message == "Added to 3 days." }
        assertEquals(3, runBlocking { graph.plans.count() })
    }

    /** Like waitUntil, but a timeout reports what the screen and view model actually held. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(5_000, condition)
        } catch (e: ComposeTimeoutException) {
            val s = viewModel.state.value
            throw AssertionError(
                "Timed out waiting for $what. message=${s.message} busy=${s.busy} selectedDate=${s.selectedDate} " +
                    "proposal=${s.proposal != null} plan=${viewModel.plan.value?.let { "${it.date} blocks=${it.blocks.size}" }} " +
                    "tasks=${runBlocking { graph.tasks.count() }} plans=${runBlocking { graph.plans.count() }}",
                e,
            )
        }
    }

    @Test
    fun `delete everything asks first and then wipes local data`() {
        runBlocking {
            graph.tasks.upsert(listOf(com.equisteg.mydaycompanion.domain.model.Task("t", "x", createdAt = graph.clock.instant())))
        }
        compose.setContent { AppNavigation(viewModel) }
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("delete-all").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, runBlocking { graph.tasks.count() }, "nothing deleted before confirmation")

        compose.onNodeWithTag("confirm").performClick()
        waitFor("screen-lock prompt") { viewModel.state.value.authRequest?.purpose == AuthPurpose.APPROVE }
        assertEquals(1, runBlocking { graph.tasks.count() }, "nothing deleted before the screen lock confirms")

        viewModel.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        waitFor("wipe") { viewModel.state.value.wiped }
        assertEquals(0, runBlocking { graph.tasks.count() })
        assertTrue(services.wipedStorage, "database files and key destroyed after the verified delete")
    }

    @Test
    fun `a cancelled screen-lock prompt deletes nothing`() {
        runBlocking {
            graph.tasks.upsert(listOf(com.equisteg.mydaycompanion.domain.model.Task("t", "x", createdAt = graph.clock.instant())))
        }
        compose.setContent { AppNavigation(viewModel) }
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("delete-all").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("confirm").performClick()
        waitFor("screen-lock prompt") { viewModel.state.value.authRequest != null }
        viewModel.onDeviceAuthResult(AuthOutcome.CANCELLED)
        waitFor("cancelled") { viewModel.state.value.message == "Cancelled." }
        assertEquals(1, runBlocking { graph.tasks.count() })
        assertFalse(services.wipedStorage)
    }
}
