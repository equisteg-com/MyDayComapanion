package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.audit.HistoryRetention
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * history.prune — removes History entries older than the owner's retention choice (F16). Only the
 * listed choices are accepted, so the newest 30 days are always kept: nobody holding the phone can
 * use this to erase what just happened. The run itself is recorded in History.
 * Args: {"keepDays":"30"|"90"|"180"|"365"}
 */
class PruneHistoryTool(private val audit: AuditLog, private val clock: Clock) : AgentTool {
    override val spec = ToolSpec(
        id = "history.prune",
        description = "Remove History entries older than the chosen retention period.",
        riskLevel = RiskLevel.L1,
        agentProposable = false,
        confirmation = Confirmation.NEVER,
        verification = "No entry older than the cut-off remains.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.keys == setOf("keepDays") && keepDays(args) != null) ArgsValidation.Ok else ArgsValidation.Rejected("keepDays")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val cutoff = cutoff(keepDays(args)!!)
        val removed = audit.deleteBefore(cutoff)
        return ToolOutcome.Success("history removed=$removed", mapOf("cutoff" to cutoff.toString()))
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean =
        audit.countBefore(Instant.parse(outcome.data["cutoff"])) == 0

    /** Whole seconds, so the cut-off compares cleanly with stored timestamps. */
    fun cutoff(keepDays: Int): Instant = clock.instant().minus(Duration.ofDays(keepDays.toLong())).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)

    companion object {
        private fun keepDays(args: JsonObject): Int? = args.string("keepDays")?.toIntOrNull()?.takeIf { it in HistoryRetention.CHOICES_DAYS }

        fun args(keepDays: Int) = JsonObject(mapOf("keepDays" to JsonPrimitive(keepDays.toString())))
    }
}
