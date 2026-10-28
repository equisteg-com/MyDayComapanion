package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.audit.AuditActor
import com.equisteg.mydaycompanion.domain.audit.AuditDecision
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.audit.AuditOutcome
import com.equisteg.mydaycompanion.domain.audit.HistoryFilter
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** OQ-22: privacy switches are recorded in History, and History filters search the whole log. */
class PrivacySettingsToolTest {
    private fun action(setting: String, value: String) = ProposedAction("settings.privacy", PrivacySettingsTool.args(setting, value))

    @Test
    fun `the owner's switch changes are recorded without content`() = runTest {
        val g = TestGraph()
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(action(PrivacySettingsTool.LOCK_SCREEN_TEXT, "on"), Requester.USER))
        assertTrue(g.privacy.lockScreenText)
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(action(PrivacySettingsTool.WIDGET_TITLES, "on"), Requester.USER))
        assertTrue(g.privacy.widgetTitles)
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(action(PrivacySettingsTool.HISTORY_KEEP_DAYS, "365"), Requester.USER))
        assertEquals(365, g.privacy.historyKeepDays)

        val recorded = g.audit.entries.filter { it.action == "settings.privacy" }
        assertEquals(
            listOf("setting=lockScreenText value=on", "setting=widgetTitles value=on", "setting=historyKeepDays value=365"),
            recorded.map { it.detail },
        )
        assertTrue(recorded.all { it.actor == AuditActor.USER && it.outcome == AuditOutcome.SUCCESS })
    }

    @Test
    fun `the agent can't change a switch and odd values are refused`() = runTest {
        val g = TestGraph()
        assertFalse(g.orchestrator.execute(action(PrivacySettingsTool.LOCK_SCREEN_TEXT, "on"), Requester.AGENT) is ExecutionResult.Done)
        assertFalse(g.privacy.lockScreenText)
        // Nor can a background job.
        assertFalse(g.orchestrator.execute(action(PrivacySettingsTool.LOCK_SCREEN_TEXT, "on"), Requester.SYSTEM) is ExecutionResult.Done)
        assertFalse(g.privacy.lockScreenText)
        for ((setting, value) in listOf("lockScreenText" to "yes", "historyKeepDays" to "7", "theme" to "on")) {
            assertFalse(g.orchestrator.execute(action(setting, value), Requester.USER) is ExecutionResult.Done, "$setting=$value")
        }
        assertEquals(com.equisteg.mydaycompanion.domain.audit.HistoryRetention.DEFAULT_DAYS, g.privacy.historyKeepDays)
    }

    @Test
    fun `filters find their newest matches however old`() = runTest {
        val g = TestGraph()
        val now = g.clock.instant()
        g.audit.record(AuditEntry("old", now.minusSeconds(3_600), AuditActor.AGENT, "data.export", RiskLevel.L3, AuditDecision.DENIED, AuditOutcome.NOT_EXECUTED))
        repeat(10) { g.audit.record(AuditEntry("n$it", now.plusSeconds(it.toLong()), AuditActor.USER, "tasks.complete", RiskLevel.L1, AuditDecision.ALLOWED, AuditOutcome.SUCCESS)) }
        assertEquals(listOf("old"), g.audit.recentMatching(HistoryFilter.ATTENTION, 3).map { it.id })
        assertEquals(listOf("n9", "n8", "n7"), g.audit.recentMatching(HistoryFilter.ALL, 3).map { it.id })
        assertEquals(null, g.audit.latestSuccess("backup.restore"))
    }

    @Test
    fun `a shorter History period can't skip the confirmed prune`() = runTest {
        val g = TestGraph()
        val now = g.clock.instant()
        g.audit.record(AuditEntry("old", now.minus(java.time.Duration.ofDays(60)), AuditActor.USER, "tasks.complete", RiskLevel.L1, AuditDecision.ALLOWED, AuditOutcome.SUCCESS))
        val r = g.orchestrator.execute(action(PrivacySettingsTool.HISTORY_KEEP_DAYS, "30"), Requester.USER)
        assertFalse(r is ExecutionResult.Done)
        assertEquals(com.equisteg.mydaycompanion.domain.audit.HistoryRetention.DEFAULT_DAYS, g.privacy.historyKeepDays)
        // Longer is always fine; shorter is fine once nothing older remains.
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(action(PrivacySettingsTool.HISTORY_KEEP_DAYS, "365"), Requester.USER))
        g.audit.entries.removeAll { it.id == "old" }
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(action(PrivacySettingsTool.HISTORY_KEEP_DAYS, "30"), Requester.USER))
        assertEquals(30, g.privacy.historyKeepDays)
    }
}
