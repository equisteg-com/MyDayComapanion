package com.equisteg.mydaycompanion.domain.tools

import kotlinx.serialization.json.JsonObject

/**
 * Risk levels from the master build document (section 17). Where the product spec's wording
 * differs, the master document wins (see docs/prebuild/06-permission-model.md).
 */
enum class RiskLevel {
    /** Internal calculations and local state reads. */
    L0,

    /** Non-sensitive local operations. */
    L1,

    /** Operations involving user data or meaningful state changes. */
    L2,

    /** External communication, consequential device actions or sensitive data operations. */
    L3,

    /** Financial, destructive, security-sensitive or highly consequential actions. */
    L4,
}

enum class Confirmation {
    NEVER,

    /** Confirm when the action was proposed by the assistant rather than started by the user. */
    WHEN_PROPOSED_BY_AGENT,

    ALWAYS,
}

/** Everything the policy needs to know about a tool. Declared in code, never by a model. */
data class ToolSpec(
    val id: String,
    val description: String,
    val riskLevel: RiskLevel,
    /** Android runtime permissions that must be granted (e.g. android.permission.POST_NOTIFICATIONS). */
    val androidPermissions: Set<String> = emptySet(),
    /** Feature flag that must be on, if any. */
    val featureFlag: String? = null,
    /** Whether the assistant may propose this tool at all. Destructive/export tools are user-initiated only. */
    val agentProposable: Boolean,
    val confirmation: Confirmation,
    /** How the tool proves it did what it claimed. */
    val verification: String,
    val auditRequired: Boolean = true,
    /** Only the owner's own tap may run it: never the agent, never a background (SYSTEM) job. */
    val userOnly: Boolean = false,
)

sealed interface ArgsValidation {
    data object Ok : ArgsValidation
    data class Rejected(val reason: String) : ArgsValidation
}

sealed interface ToolOutcome {
    /** [summary] must not contain personal content; it is written to the audit log. */
    data class Success(val summary: String, val data: Map<String, String> = emptyMap()) : ToolOutcome
    data class Failure(val reasonCode: String) : ToolOutcome
}

/** An allowlisted capability. Tools validate their own arguments independently of the policy. */
interface AgentTool {
    val spec: ToolSpec

    fun validate(args: JsonObject): ArgsValidation

    suspend fun execute(args: JsonObject): ToolOutcome

    /** Re-reads state after execution to confirm the effect actually happened. */
    suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean

    /**
     * True when [args] (already validated) act on an item the owner marked personal. Such calls
     * always need a confirmation, and the UI adds the owner's chosen verification (build doc §6.1).
     */
    suspend fun touchesPersonal(args: JsonObject): Boolean = false
}
