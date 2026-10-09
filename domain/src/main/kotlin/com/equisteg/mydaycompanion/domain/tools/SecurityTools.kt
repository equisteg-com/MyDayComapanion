package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.security.ChallengeAnswer
import com.equisteg.mydaycompanion.domain.security.ChallengeHasher
import com.equisteg.mydaycompanion.domain.security.PersonalApproval
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import com.equisteg.mydaycompanion.domain.security.SecurityStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * security.updateSettings — app lock, lock delay, wipe-on-failure and personal approval method.
 * L4 and user-initiated only: a model can never change how the owner is verified.
 * Args (all strings): {"appLockEnabled","lockAfterMinutes","wipeOnRepeatedFailure","personalApproval"}
 */
class UpdateSecuritySettingsTool(private val store: SecurityStore) : AgentTool {
    override val spec = ToolSpec(
        id = "security.updateSettings",
        description = "Change app lock, lock delay, wipe-on-failure and how personal items are approved.",
        riskLevel = RiskLevel.L4,
        agentProposable = false,
        confirmation = Confirmation.ALWAYS,
        verification = "Stored settings equal the requested settings.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.keys == KEYS && parse(args) != null) ArgsValidation.Ok else ArgsValidation.Rejected("settings")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        store.saveSettings(parse(args) ?: return ToolOutcome.Failure("INVALID"))
        return ToolOutcome.Success("security settings updated")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = store.settings() == parse(args)

    companion object {
        private val KEYS = setOf("appLockEnabled", "lockAfterMinutes", "wipeOnRepeatedFailure", "personalApproval")

        fun args(s: SecuritySettings) = JsonObject(
            mapOf(
                "appLockEnabled" to JsonPrimitive(s.appLockEnabled.toString()),
                "lockAfterMinutes" to JsonPrimitive(s.lockAfterMinutes.toString()),
                "wipeOnRepeatedFailure" to JsonPrimitive(s.wipeOnRepeatedFailure.toString()),
                "personalApproval" to JsonPrimitive(s.personalApproval.name),
            ),
        )

        private fun strictBoolean(s: String): Boolean? = when (s) { "true" -> true; "false" -> false; else -> null }

        private fun parse(args: JsonObject): SecuritySettings? {
            val lock = args.string("appLockEnabled")?.let(::strictBoolean) ?: return null
            val after = args.string("lockAfterMinutes")?.toIntOrNull()?.takeIf { it in SecuritySettings.LOCK_AFTER_CHOICES } ?: return null
            val wipe = args.string("wipeOnRepeatedFailure")?.let(::strictBoolean) ?: return null
            val personal = PersonalApproval.entries.firstOrNull { it.name == args.string("personalApproval") } ?: return null
            return SecuritySettings(lock, after, wipe, personal)
        }
    }
}

/**
 * security.setChallenges — saves exactly three challenge questions with hashed answers, or removes
 * them all (empty list). L4 and user-initiated only. The UI hashes answers before building the
 * arguments, so clear answers never enter the orchestrator, the fingerprint or the audit log.
 * Args: {"answers":[{"id","question","salt","hash","iterations"}]}
 */
class SetChallengeQuestionsTool(private val store: SecurityStore) : AgentTool {
    override val spec = ToolSpec(
        id = "security.setChallenges",
        description = "Set up, change or remove the owner's challenge questions.",
        riskLevel = RiskLevel.L4,
        agentProposable = false,
        confirmation = Confirmation.ALWAYS,
        verification = "Stored challenge hashes equal the requested ones.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        val answers = parse(args) ?: return ArgsValidation.Rejected("answers")
        val ok = (answers.isEmpty() || answers.size == ChallengeHasher.QUESTION_COUNT) &&
            answers.all { ChallengeHasher.isWellFormed(it) } &&
            answers.map { it.id }.toSet().size == answers.size &&
            answers.map { it.question.lowercase() }.toSet().size == answers.size
        return if (ok) ArgsValidation.Ok else ArgsValidation.Rejected("answers")
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val answers = parse(args) ?: return ToolOutcome.Failure("INVALID")
        store.replaceChallenges(answers)
        store.clearFailures()
        return ToolOutcome.Success(if (answers.isEmpty()) "challenge questions removed" else "challenge questions saved")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean =
        store.challenges().sortedBy { it.id } == parse(args)?.sortedBy { it.id }

    companion object {
        private val ITEM_KEYS = setOf("id", "question", "salt", "hash", "iterations")

        fun args(answers: List<ChallengeAnswer>) = JsonObject(
            mapOf(
                "answers" to JsonArray(
                    answers.map {
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive(it.id),
                                "question" to JsonPrimitive(it.question),
                                "salt" to JsonPrimitive(it.salt),
                                "hash" to JsonPrimitive(it.hash),
                                "iterations" to JsonPrimitive(it.iterations.toString()),
                            ),
                        )
                    },
                ),
            ),
        )

        private fun parse(args: JsonObject): List<ChallengeAnswer>? {
            if (args.keys != setOf("answers")) return null
            val items = args.array("answers") ?: return null
            if (items.size > ChallengeHasher.QUESTION_COUNT) return null
            return items.map { el ->
                val o = el.asObject()?.takeIf { it.keys == ITEM_KEYS } ?: return null
                ChallengeAnswer(
                    id = o.string("id") ?: return null,
                    question = o.string("question") ?: return null,
                    salt = o.string("salt") ?: return null,
                    hash = o.string("hash") ?: return null,
                    iterations = o.string("iterations")?.toIntOrNull() ?: return null,
                )
            }
        }
    }
}
