package com.equisteg.mydaycompanion.domain.policy

import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.domain.config.FeatureFlags
import com.equisteg.mydaycompanion.domain.tools.Confirmation
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import com.equisteg.mydaycompanion.domain.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ToolAuthorizationPolicyTest {
    private fun spec(
        risk: RiskLevel,
        proposable: Boolean = true,
        confirmation: Confirmation = Confirmation.NEVER,
        perms: Set<String> = emptySet(),
        flag: String? = null,
    ) = ToolSpec("test.tool", "t", risk, perms, flag, proposable, confirmation, "v")

    private val policy = ToolAuthorizationPolicy(FeatureFlags(), PermissionChecker { it == "granted" })

    private fun decide(spec: ToolSpec?, requester: Requester = Requester.USER, valid: Boolean = true, confirmation: UserConfirmation? = null) =
        policy.decide(spec, "test.tool", "{}", valid, requester, confirmation)

    @Test
    fun `unknown tool is denied`() {
        assertEquals(DenyReason.UNKNOWN_TOOL, (decide(null) as PolicyDecision.Deny).reason)
    }

    @Test
    fun `invalid arguments are denied`() {
        assertEquals(DenyReason.INVALID_ARGUMENTS, (decide(spec(RiskLevel.L0), valid = false) as PolicyDecision.Deny).reason)
    }

    @Test
    fun `agent cannot request user-only tools even with a confirmation`() {
        val s = spec(RiskLevel.L4, proposable = false, confirmation = Confirmation.ALWAYS)
        val fp = ToolAuthorizationPolicy.fingerprint("test.tool", "{}")
        assertEquals(DenyReason.NOT_AGENT_PROPOSABLE, (decide(s, Requester.AGENT, confirmation = UserConfirmation(fp)) as PolicyDecision.Deny).reason)
    }

    @Test
    fun `missing android permission is denied`() {
        assertEquals(DenyReason.PERMISSION_MISSING, (decide(spec(RiskLevel.L1, perms = setOf("missing"))) as PolicyDecision.Deny).reason)
        assertIs<PolicyDecision.Allow>(decide(spec(RiskLevel.L1, perms = setOf("granted"))))
    }

    @Test
    fun `disabled feature is denied`() {
        assertEquals(DenyReason.FEATURE_DISABLED, (decide(spec(RiskLevel.L1, flag = Flag.CLOUD_AI.key)) as PolicyDecision.Deny).reason)
    }

    @Test
    fun `L3 and L4 always need confirmation, even when the user starts them`() {
        for (risk in listOf(RiskLevel.L3, RiskLevel.L4)) {
            assertIs<PolicyDecision.RequireConfirmation>(decide(spec(risk, proposable = false)))
        }
    }

    @Test
    fun `agent proposals that change data need confirmation, user actions do not`() {
        val s = spec(RiskLevel.L2, confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT)
        assertIs<PolicyDecision.RequireConfirmation>(decide(s, Requester.AGENT))
        assertIs<PolicyDecision.Allow>(decide(s, Requester.USER))
    }

    @Test
    fun `confirmation only covers the exact arguments it was given for`() {
        val s = spec(RiskLevel.L3, proposable = false)
        val fp = (decide(s) as PolicyDecision.RequireConfirmation).actionFingerprint
        assertIs<PolicyDecision.Allow>(decide(s, confirmation = UserConfirmation(fp)))
        val other = policy.decide(s, "test.tool", """{"x":1}""", true, Requester.USER, UserConfirmation(fp))
        assertEquals(DenyReason.CONFIRMATION_MISMATCH, (other as PolicyDecision.Deny).reason)
    }

    @Test
    fun `cloud, sync, account and analytics flags cannot be enabled in V1`() {
        listOf(Flag.CLOUD_AI, Flag.CLOUD_SYNC, Flag.ACCOUNTS, Flag.ANALYTICS, Flag.EXTERNAL_INTEGRATIONS).forEach {
            assertFailsWith<IllegalArgumentException> { FeatureFlags(mapOf(it to true)) }
        }
    }

    @Test
    fun `the app's own scheduled work may only run quiet L0 and L1 tools`() {
        assertIs<PolicyDecision.Allow>(decide(spec(RiskLevel.L1, proposable = false, confirmation = Confirmation.NEVER), Requester.SYSTEM))
        assertEquals(DenyReason.NOT_SYSTEM_RUNNABLE, (decide(spec(RiskLevel.L2, confirmation = Confirmation.NEVER), Requester.SYSTEM) as PolicyDecision.Deny).reason)
        assertEquals(DenyReason.NOT_SYSTEM_RUNNABLE, (decide(spec(RiskLevel.L1, confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT), Requester.SYSTEM) as PolicyDecision.Deny).reason)
        val fp = ToolAuthorizationPolicy.fingerprint("test.tool", "{}")
        assertEquals(
            DenyReason.NOT_SYSTEM_RUNNABLE,
            (decide(spec(RiskLevel.L4, proposable = false, confirmation = Confirmation.ALWAYS), Requester.SYSTEM, confirmation = UserConfirmation(fp)) as PolicyDecision.Deny).reason,
        )
    }
}
