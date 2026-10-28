package com.equisteg.mydaycompanion.domain.policy

import com.equisteg.mydaycompanion.domain.config.FeatureFlags
import com.equisteg.mydaycompanion.domain.tools.Confirmation
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import com.equisteg.mydaycompanion.domain.tools.ToolSpec
import java.security.MessageDigest

/**
 * Who started an action. AGENT means the action came from a model/assistant proposal. SYSTEM is the
 * app's own scheduled work (nightly run, after a restart); it may only run L0/L1 tools that never
 * need a confirmation, and it can never answer a confirmation.
 */
enum class Requester { USER, AGENT, SYSTEM }

/** Grants are checked against the real Android permission system by the app layer. */
fun interface PermissionChecker {
    fun isGranted(androidPermission: String): Boolean
}

/**
 * Proof that the user explicitly approved one specific action (tool + exact arguments). Only UI
 * code creates these, from a user gesture; model output can never produce one.
 */
data class UserConfirmation(val actionFingerprint: String)

enum class DenyReason {
    UNKNOWN_TOOL,
    FEATURE_DISABLED,
    INVALID_ARGUMENTS,
    PERMISSION_MISSING,
    NOT_AGENT_PROPOSABLE,
    CONFIRMATION_MISMATCH,
    NOT_SYSTEM_RUNNABLE,
}

sealed interface PolicyDecision {
    data object Allow : PolicyDecision
    data class RequireConfirmation(val actionFingerprint: String) : PolicyDecision
    data class Deny(val reason: DenyReason, val detail: String = "") : PolicyDecision
}

/**
 * Deterministic authorisation for every tool call. Nothing here consults a model. Rules are
 * evaluated in order and the first failing rule decides.
 */
class ToolAuthorizationPolicy(
    private val flags: FeatureFlags,
    private val permissions: PermissionChecker,
) {
    fun decide(
        spec: ToolSpec?,
        toolId: String,
        canonicalArgs: String,
        argsValid: Boolean,
        requester: Requester,
        confirmation: UserConfirmation?,
        personal: Boolean = false,
    ): PolicyDecision {
        if (spec == null) return PolicyDecision.Deny(DenyReason.UNKNOWN_TOOL, toolId)
        if (spec.featureFlag != null && !flags.isEnabled(spec.featureFlag)) {
            return PolicyDecision.Deny(DenyReason.FEATURE_DISABLED, spec.featureFlag)
        }
        if (requester == Requester.AGENT && (!spec.agentProposable || spec.userOnly)) return PolicyDecision.Deny(DenyReason.NOT_AGENT_PROPOSABLE)
        if (requester == Requester.SYSTEM && (spec.userOnly || spec.riskLevel > RiskLevel.L1 || spec.confirmation != Confirmation.NEVER)) {
            return PolicyDecision.Deny(DenyReason.NOT_SYSTEM_RUNNABLE)
        }
        if (!argsValid) return PolicyDecision.Deny(DenyReason.INVALID_ARGUMENTS)
        spec.androidPermissions.firstOrNull { !permissions.isGranted(it) }?.let {
            return PolicyDecision.Deny(DenyReason.PERMISSION_MISSING, it)
        }

        val fingerprint = fingerprint(spec.id, canonicalArgs)
        val needsConfirmation = spec.riskLevel >= RiskLevel.L3 ||
            spec.confirmation == Confirmation.ALWAYS ||
            personal ||
            (spec.confirmation == Confirmation.WHEN_PROPOSED_BY_AGENT && requester == Requester.AGENT)
        if (!needsConfirmation) return PolicyDecision.Allow
        if (requester == Requester.SYSTEM) return PolicyDecision.Deny(DenyReason.NOT_SYSTEM_RUNNABLE)
        if (confirmation == null) return PolicyDecision.RequireConfirmation(fingerprint)
        if (confirmation.actionFingerprint != fingerprint) return PolicyDecision.Deny(DenyReason.CONFIRMATION_MISMATCH)
        return PolicyDecision.Allow
    }

    companion object {
        fun fingerprint(toolId: String, canonicalArgs: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$toolId\u0000$canonicalArgs".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
