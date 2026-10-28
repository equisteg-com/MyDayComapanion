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

/** The owner's privacy switches that live outside the encrypted database (they hold no personal content). */
interface PrivacySwitches {
    /** Show what a reminder is for on the secure lock screen. */
    var lockScreenText: Boolean

    /** Show task and event names on the home-screen widget. */
    var widgetTitles: Boolean

    /** How many days History keeps; one of [HistoryRetention.CHOICES_DAYS]. */
    var historyKeepDays: Int
}

/**
 * settings.privacy — changes one privacy switch and records it in History (OQ-22 b), so a change
 * made by whoever held the unlocked phone is visible later. The entry says which switch and its new
 * value only. Owner only; a shorter History period first goes through `history.prune`.
 * A shorter History period is refused while entries older than it remain (they go through the confirmed prune).
 * Args: {"setting":"lockScreenText"|"widgetTitles"|"historyKeepDays","value":"on"|"off"|"<days>"}
 */
class PrivacySettingsTool(
    private val switches: PrivacySwitches,
    private val audit: AuditLog,
    private val clock: Clock,
) : AgentTool {
    override val spec = ToolSpec(
        id = "settings.privacy",
        description = "Change a privacy switch (lock-screen text, widget names, History period).",
        riskLevel = RiskLevel.L1,
        agentProposable = false,
        confirmation = Confirmation.NEVER,
        verification = "The switch reads back the new value.",
        userOnly = true,
    )

    private fun parse(args: JsonObject): Pair<String, String>? {
        if (args.keys != setOf("setting", "value")) return null
        val setting = args.string("setting") ?: return null
        val value = args.string("value") ?: return null
        val ok = when (setting) {
            LOCK_SCREEN_TEXT, WIDGET_TITLES -> value == ON || value == OFF
            HISTORY_KEEP_DAYS -> value.toIntOrNull() in HistoryRetention.CHOICES_DAYS
            else -> false
        }
        return if (ok) setting to value else null
    }

    override fun validate(args: JsonObject): ArgsValidation = if (parse(args) != null) ArgsValidation.Ok else ArgsValidation.Rejected("privacySetting")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val (setting, value) = parse(args) ?: return ToolOutcome.Failure("INVALID")
        // A shorter period that would drop entries must go through the confirmed `history.prune` first.
        if (setting == HISTORY_KEEP_DAYS) {
            val days = value.toInt()
            val cutoff = PruneHistoryTool(audit, clock).cutoff(days)
            if (days < switches.historyKeepDays && audit.countBefore(cutoff) > 0) return ToolOutcome.Failure("PRUNE_FIRST")
        }
        when (setting) {
            LOCK_SCREEN_TEXT -> switches.lockScreenText = value == ON
            WIDGET_TITLES -> switches.widgetTitles = value == ON
            HISTORY_KEEP_DAYS -> switches.historyKeepDays = value.toInt()
        }
        return ToolOutcome.Success("setting=$setting value=$value")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val (setting, value) = parse(args) ?: return false
        return when (setting) {
            LOCK_SCREEN_TEXT -> switches.lockScreenText == (value == ON)
            WIDGET_TITLES -> switches.widgetTitles == (value == ON)
            else -> switches.historyKeepDays == value.toInt()
        }
    }

    companion object {
        const val LOCK_SCREEN_TEXT = "lockScreenText"
        const val WIDGET_TITLES = "widgetTitles"
        const val HISTORY_KEEP_DAYS = "historyKeepDays"
        private const val ON = "on"
        private const val OFF = "off"

        fun args(setting: String, value: String) = JsonObject(mapOf("setting" to JsonPrimitive(setting), "value" to JsonPrimitive(value)))
        fun args(setting: String, on: Boolean) = args(setting, if (on) ON else OFF)
    }
}
