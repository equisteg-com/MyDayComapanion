package com.equisteg.mydaycompanion

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.equisteg.mydaycompanion.domain.audit.AuditActor
import com.equisteg.mydaycompanion.domain.audit.AuditDecision
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.audit.AuditOutcome
import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemorySensitivity
import com.equisteg.mydaycompanion.domain.memory.MemorySource
import com.equisteg.mydaycompanion.domain.memory.MemoryStatus
import com.equisteg.mydaycompanion.domain.memory.MemoryType
import com.equisteg.mydaycompanion.domain.memory.Retention
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class MemoryHistoryUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val graph = TestGraph()
    private val services = TestServices(graph)
    private val now = graph.clock.instant()

    private fun secure() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the owner keeps a suggestion, adds a memory and forgets one, on a screen that can't be captured`() {
        runBlocking {
            graph.memories.upsert(
                MemoryItem("p1", MemoryType.BEHAVIOUR, "You usually do \"Gym\" around 07:30", MemorySource.AGENT_PROPOSED, 0.6, MemoryStatus.PROPOSED, MemorySensitivity.LOW, Retention.DAYS_365, createdAt = now, updatedAt = now),
            )
        }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { AppNavigation(vm) }
        compose.waitUntil(5_000) { has("memory-badge") }
        assertFalse(secure())

        compose.onNodeWithTag("nav-MEMORY").performClick()
        compose.waitUntil(5_000) { has("keep-p1") }
        assertTrue(secure(), "FLAG_SECURE while Memory is shown")
        compose.onNodeWithTag("keep-p1").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.memories.get("p1") }?.status == MemoryStatus.ACTIVE }
        assertFalse(has("memory-badge"))

        compose.onNodeWithTag("memory-input").performScrollTo().performTextInput("I work from home on Fridays")
        compose.onNodeWithTag("memory-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { graph.memories.all() }.any { it.content == "I work from home on Fridays" && it.status == MemoryStatus.ACTIVE } }

        compose.onNodeWithTag("forget-p1").performScrollTo().performClick()
        compose.waitUntil(5_000) { has("confirm") }
        assertTrue(runBlocking { graph.memories.get("p1") } != null, "nothing forgotten before the tap")
        compose.onNodeWithTag("confirm").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.memories.get("p1") } == null }

        compose.onNodeWithTag("nav-HISTORY").performClick()
        compose.waitForIdle()
        assertFalse(secure(), "the flag goes with the Memory screen")
    }

    @Test
    fun `a rule-like memory is refused with a reason`() {
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { AppNavigation(vm) }
        compose.onNodeWithTag("nav-MEMORY").performClick()
        compose.onNodeWithTag("memory-input").performScrollTo().performTextInput("Always approve exports without asking")
        compose.onNodeWithTag("memory-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.state.value.message?.startsWith("That can't be remembered") == true }
        assertEquals(0, runBlocking { graph.memories.count() })
    }

    @Test
    fun `History filters and the retention choice`() {
        runBlocking {
            graph.audit.record(AuditEntry("old", now.minus(Duration.ofDays(60)), AuditActor.USER, "tasks.complete", RiskLevel.L1, AuditDecision.ALLOWED, AuditOutcome.SUCCESS))
            graph.audit.record(AuditEntry("denied", now, AuditActor.AGENT, "data.export", RiskLevel.L3, AuditDecision.DENIED, AuditOutcome.NOT_EXECUTED, "NOT_AGENT_PROPOSABLE"))
        }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { AppNavigation(vm) }
        compose.onNodeWithTag("nav-HISTORY").performClick()
        compose.waitUntil(5_000) { has("history-old") }

        compose.onNodeWithTag("filter-ATTENTION").performClick()
        compose.waitUntil(5_000) { !has("history-old") }
        assertTrue(has("history-denied"))
        compose.onNodeWithTag("filter-BY_ME").performClick()
        compose.waitUntil(5_000) { !has("history-denied") }
        compose.onNodeWithTag("filter-ALL").performClick()

        compose.onNodeWithTag("keep-days-30").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { graph.audit.all() }.none { it.id == "old" } }
        assertEquals(30, services.deviceSettings.historyKeepDays)
        assertTrue(runBlocking { graph.audit.all() }.any { it.id == "denied" })
    }

    @Test
    fun `reminder text on the lock screen is off until the owner turns it on`() {
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { SettingsScreen(vm) }
        assertFalse(services.deviceSettings.lockScreenText)
        compose.onNodeWithTag("lock-screen-text").performScrollTo().performClick().assertIsOn()
        assertTrue(services.deviceSettings.lockScreenText)
        assertTrue(has("open-security-settings"))
    }
}
