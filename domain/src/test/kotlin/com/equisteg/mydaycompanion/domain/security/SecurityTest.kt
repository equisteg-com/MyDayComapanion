package com.equisteg.mydaycompanion.domain.security

import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.testing.InMemorySecurityStore
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import com.equisteg.mydaycompanion.domain.tools.SetChallengeQuestionsTool
import com.equisteg.mydaycompanion.domain.tools.UpdateSecuritySettingsTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecurityTest {
    private val t0 = Instant.parse("2026-10-06T07:00:00Z")

    private fun three() = listOf(
        ChallengeHasher.hash("q1", "What was the name of your first school?", "St. Mary's School"),
        ChallengeHasher.hash("q2", "In which town was your mother born?", "Madurai"),
        ChallengeHasher.hash("q3", "What was your childhood nickname?", "Chinnu"),
    )

    @Test
    fun `answers are normalised and only a salted hash is stored`() {
        val a = ChallengeHasher.hash("q1", "School?", "  St. Mary's   School! ")
        assertTrue(ChallengeHasher.matches(a, "st marys school"))
        assertTrue(ChallengeHasher.matches(a, "ST. MARY'S SCHOOL"))
        assertFalse(ChallengeHasher.matches(a, "st marks school"))
        assertFalse(a.hash.contains("mary", ignoreCase = true) || a.salt.contains("mary", ignoreCase = true))
        assertEquals(ChallengeHasher.ITERATIONS, a.iterations)
        assertTrue(ChallengeHasher.isWellFormed(a))
        val b = ChallengeHasher.hash("q1", "School?", "st marys school")
        assertNotEquals(a.hash, b.hash, "a fresh salt per answer")
        assertFalse(ChallengeHasher.isAcceptableAnswer(" ? "))
    }

    @Test
    fun `five wrong answers lock for fifteen minutes and ten escalate`() {
        val five = (1..5L).map { t0.plusSeconds(it) }
        assertEquals(ChallengeGate.Open, LockoutPolicy.evaluate(five.take(4), t0.plusSeconds(10), FailureResponse.LOCK_UNTIL_CREDENTIAL))
        assertIs<ChallengeGate.LockedUntil>(LockoutPolicy.evaluate(five, t0.plusSeconds(10), FailureResponse.LOCK_UNTIL_CREDENTIAL))
        assertEquals(ChallengeGate.Open, LockoutPolicy.evaluate(five, t0.plus(Duration.ofMinutes(16)), FailureResponse.LOCK_UNTIL_CREDENTIAL))

        val ten = (1..10L).map { t0.plus(Duration.ofMinutes(it * 20)) }
        val now = t0.plus(Duration.ofHours(5))
        assertEquals(ChallengeGate.RequireCredential, LockoutPolicy.evaluate(ten, now, FailureResponse.LOCK_UNTIL_CREDENTIAL))
        assertEquals(ChallengeGate.Wipe, LockoutPolicy.evaluate(ten, now, FailureResponse.WIPE))
        assertEquals(ChallengeGate.Open, LockoutPolicy.evaluate(ten, now.plus(Duration.ofDays(2)), FailureResponse.WIPE), "older than 24 h")
    }

    @Test
    fun `approval steps follow the risk level and cannot drop below the defaults`() {
        val s = SecuritySettings()
        assertEquals(listOf(ApprovalStep.TAP), ApprovalRules.stepsFor(RiskLevel.L2, false, s, hasChallenges = true))
        assertEquals(listOf(ApprovalStep.TAP, ApprovalStep.DEVICE_CREDENTIAL), ApprovalRules.stepsFor(RiskLevel.L3, false, s, true))
        assertEquals(
            listOf(ApprovalStep.TAP, ApprovalStep.DEVICE_CREDENTIAL, ApprovalStep.CHALLENGE),
            ApprovalRules.stepsFor(RiskLevel.L4, false, s, true),
        )
        assertEquals(listOf(ApprovalStep.TAP, ApprovalStep.DEVICE_CREDENTIAL), ApprovalRules.stepsFor(RiskLevel.L4, false, s, false))
        assertEquals(listOf(ApprovalStep.TAP, ApprovalStep.DEVICE_CREDENTIAL), ApprovalRules.stepsFor(RiskLevel.L2, true, s, true))
        val byChallenge = s.copy(personalApproval = PersonalApproval.CHALLENGE)
        assertEquals(listOf(ApprovalStep.TAP, ApprovalStep.CHALLENGE), ApprovalRules.stepsFor(RiskLevel.L2, true, byChallenge, true))
        assertEquals(listOf(ApprovalStep.TAP, ApprovalStep.DEVICE_CREDENTIAL), ApprovalRules.stepsFor(RiskLevel.L2, true, byChallenge, false))
    }

    @Test
    fun `verifier counts wrong answers, blocks while locked and resets on success or credential`() = runTest {
        var now = t0
        val store = InMemorySecurityStore().apply { replaceChallenges(three()) }
        val verifier = ChallengeVerifier(store, { now })
        repeat(4) { assertIs<ChallengeResult.Wrong>(verifier.answer("q2", "chennai")) }
        val fifth = verifier.answer("q2", "chennai")
        assertIs<ChallengeGate.LockedUntil>(assertIs<ChallengeResult.Wrong>(fifth).gate)
        assertIs<ChallengeResult.Blocked>(verifier.answer("q2", "Madurai"), "even the right answer waits out the lock")
        now = now.plus(Duration.ofMinutes(16))
        assertEquals(ChallengeResult.Correct, verifier.answer("q2", "madurai"))
        assertEquals(0, store.failures().size)

        repeat(10) { now = now.plus(Duration.ofMinutes(16)); verifier.answer("q3", "wrong") }
        assertEquals(ChallengeGate.RequireCredential, verifier.gate())
        verifier.onDeviceCredentialPassed()
        assertEquals(ChallengeGate.Open, verifier.gate())
    }

    @Test
    fun `wipe response is only used when the owner opted in`() = runTest {
        val store = InMemorySecurityStore(SecuritySettings(wipeOnRepeatedFailure = true)).apply { replaceChallenges(three()) }
        var now = t0
        val verifier = ChallengeVerifier(store, { now })
        repeat(10) { now = now.plus(Duration.ofMinutes(16)); verifier.answer("q1", "nope") }
        assertEquals(ChallengeGate.Wipe, verifier.gate())
    }

    @Test
    fun `security changes are L4, user-only and need a matching confirmation`() = runTest {
        val g = TestGraph()
        val settings = ProposedAction("security.updateSettings", UpdateSecuritySettingsTool.args(SecuritySettings(appLockEnabled = false)))

        assertIs<ExecutionResult.Denied>(g.orchestrator.execute(settings, Requester.AGENT))
        val ask = assertIs<ExecutionResult.NeedsConfirmation>(g.orchestrator.execute(settings, Requester.USER))
        assertEquals(RiskLevel.L4, ask.riskLevel)
        assertIs<ExecutionResult.Denied>(g.orchestrator.execute(settings, Requester.USER, UserConfirmation("forged")))
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(settings, Requester.USER, UserConfirmation(ask.actionFingerprint)))
        assertFalse(g.security.settings().appLockEnabled)

        val questions = ProposedAction("security.setChallenges", SetChallengeQuestionsTool.args(three()))
        val ask2 = assertIs<ExecutionResult.NeedsConfirmation>(g.orchestrator.execute(questions, Requester.USER))
        assertIs<ExecutionResult.Done>(g.orchestrator.execute(questions, Requester.USER, UserConfirmation(ask2.actionFingerprint)))
        assertEquals(3, g.security.challenges().size)
        assertTrue(g.audit.entries.none { e -> listOf("Madurai", "Chinnu", "Mary").any { e.detail.orEmpty().contains(it, true) } })
    }

    @Test
    fun `malformed security arguments are rejected`() = runTest {
        val g = TestGraph()
        val bad = listOf(
            ProposedAction("security.updateSettings", JsonObject(mapOf("appLockEnabled" to JsonPrimitive("maybe")))),
            ProposedAction(
                "security.updateSettings",
                JsonObject(UpdateSecuritySettingsTool.args(SecuritySettings()) + ("lockAfterMinutes" to JsonPrimitive("7"))),
            ),
            ProposedAction("security.setChallenges", SetChallengeQuestionsTool.args(three().take(2))),
            ProposedAction("security.setChallenges", SetChallengeQuestionsTool.args(three().map { it.copy(iterations = 1_000) })),
            ProposedAction("security.setChallenges", SetChallengeQuestionsTool.args(three().map { it.copy(hash = "not base64!") })),
            ProposedAction("security.setChallenges", SetChallengeQuestionsTool.args(three().map { it.copy(question = "same?") })),
        )
        bad.forEach { assertEquals(ExecutionResult.Denied("INVALID_ARGUMENTS"), g.orchestrator.execute(it, Requester.USER), it.toString()) }
    }

    @Test
    fun `delete everything also clears security settings and challenge answers`() = runTest {
        val g = TestGraph()
        g.security.replaceChallenges(three())
        g.security.recordFailure(t0)
        val report = g.deletion.deleteEverything()
        assertTrue(report.verified)
        assertEquals(0, g.security.count())
    }
}
