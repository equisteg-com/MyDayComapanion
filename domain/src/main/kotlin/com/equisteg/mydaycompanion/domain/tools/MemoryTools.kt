package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.domain.memory.MemoryDecision
import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemoryPolicy
import com.equisteg.mydaycompanion.domain.memory.MemorySensitivity
import com.equisteg.mydaycompanion.domain.memory.MemorySource
import com.equisteg.mydaycompanion.domain.memory.MemoryStatus
import com.equisteg.mydaycompanion.domain.memory.MemoryStore
import com.equisteg.mydaycompanion.domain.memory.MemoryType
import com.equisteg.mydaycompanion.domain.memory.Retention
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock

/** Two memories say the same thing when they match ignoring case, spaces and end punctuation. */
fun memoryKey(content: String): String = content.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')

/**
 * memory.propose — the assistant suggests something to remember (build doc F9). The memory is
 * stored as PROPOSED and is not used until the owner keeps it. The assistant cannot propose
 * sensitive memories or standing instructions ([MemoryPolicy]), and never proposes the same thing
 * twice, including something the owner already said no to.
 * Args: {"type","content","confidence"} with confidence "0.0".."1.0".
 */
class ProposeMemoryTool(
    private val store: MemoryStore,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val policy: MemoryPolicy = MemoryPolicy(),
) : AgentTool {
    override val spec = ToolSpec(
        id = "memory.propose",
        description = "Suggest something about the owner's habits to remember; the owner decides.",
        riskLevel = RiskLevel.L1,
        featureFlag = Flag.MEMORY.key,
        agentProposable = true,
        confirmation = Confirmation.NEVER,
        verification = "The memory is stored as PROPOSED.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        if (args.keys != setOf("type", "content", "confidence")) return ArgsValidation.Rejected("keys")
        val type = MemoryType.entries.firstOrNull { it.name == args.string("type") } ?: return ArgsValidation.Rejected("type")
        if (type == MemoryType.EXPLICIT_INSTRUCTION) return ArgsValidation.Rejected("type")
        args.string("confidence")?.toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: return ArgsValidation.Rejected("confidence")
        val decision = policy.admit(candidate(args, "check", type))
        return if (decision is MemoryDecision.Accepted) ArgsValidation.Ok else ArgsValidation.Rejected((decision as MemoryDecision.Rejected).reason)
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val type = MemoryType.valueOf(args.string("type")!!)
        val key = memoryKey(args.string("content")!!)
        if (store.all().any { memoryKey(it.content) == key }) return ToolOutcome.Failure("ALREADY_KNOWN")
        val item = (policy.admit(candidate(args, ids.newId(), type)) as? MemoryDecision.Accepted)?.item ?: return ToolOutcome.Failure("REJECTED")
        store.upsert(item)
        return ToolOutcome.Success("memory proposed", mapOf("memoryId" to item.id))
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean =
        store.get(outcome.data["memoryId"].orEmpty())?.status == MemoryStatus.PROPOSED

    private fun candidate(args: JsonObject, id: String, type: MemoryType): MemoryItem {
        val now = clock.instant()
        return MemoryItem(
            id = id,
            type = type,
            content = args.string("content").orEmpty(),
            source = MemorySource.AGENT_PROPOSED,
            confidence = args.string("confidence")?.toDoubleOrNull() ?: -1.0,
            status = MemoryStatus.PROPOSED,
            sensitivity = MemorySensitivity.LOW,
            retention = Retention.DAYS_365,
            createdAt = now,
            updatedAt = now,
        )
    }

    companion object {
        fun args(type: MemoryType, content: String, confidence: Double) = JsonObject(
            mapOf(
                "type" to JsonPrimitive(type.name),
                "content" to JsonPrimitive(content),
                "confidence" to JsonPrimitive("%.2f".format(java.util.Locale.ROOT, confidence)),
            ),
        )
    }
}

/**
 * memory.add — something the owner typed on the Memory screen. Active at once, because the owner
 * wrote it. A private memory is kept but never given to the model, and changing it later needs the
 * owner's verification. The same poisoning checks apply as for proposals.
 * Args: {"content","private":"true"|"false"}
 */
class AddMemoryTool(
    private val store: MemoryStore,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val policy: MemoryPolicy = MemoryPolicy(),
) : AgentTool {
    override val spec = ToolSpec(
        id = "memory.add",
        description = "Remember something the owner typed.",
        riskLevel = RiskLevel.L2,
        featureFlag = Flag.MEMORY.key,
        agentProposable = false,
        confirmation = Confirmation.NEVER,
        verification = "The memory is stored as ACTIVE with the requested text.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        if (args.keys != setOf("content", "private")) return ArgsValidation.Rejected("keys")
        if (args.string("private") !in setOf("true", "false")) return ArgsValidation.Rejected("private")
        val decision = policy.admit(candidate(args, "check"))
        return if (decision is MemoryDecision.Accepted) ArgsValidation.Ok else ArgsValidation.Rejected((decision as MemoryDecision.Rejected).reason)
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val key = memoryKey(args.string("content")!!)
        val existing = store.all().firstOrNull { memoryKey(it.content) == key }
        // Saying it again replaces an old proposal or a "no" with the owner's own words.
        val item = (policy.admit(candidate(args, existing?.id ?: ids.newId())) as? MemoryDecision.Accepted)?.item ?: return ToolOutcome.Failure("REJECTED")
        store.upsert(item)
        return ToolOutcome.Success("memory added", mapOf("memoryId" to item.id))
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val stored = store.get(outcome.data["memoryId"].orEmpty()) ?: return false
        return stored.status == MemoryStatus.ACTIVE && stored.content == args.string("content")!!.trim()
    }

    private fun candidate(args: JsonObject, id: String): MemoryItem {
        val now = clock.instant()
        return MemoryItem(
            id = id,
            type = MemoryType.PREFERENCE,
            content = args.string("content").orEmpty(),
            source = MemorySource.USER_EXPLICIT,
            confidence = 1.0,
            status = MemoryStatus.ACTIVE,
            sensitivity = if (args.string("private") == "true") MemorySensitivity.SENSITIVE else MemorySensitivity.LOW,
            retention = Retention.UNTIL_DELETED,
            createdAt = now,
            updatedAt = now,
        )
    }

    companion object {
        fun args(content: String, private: Boolean) = JsonObject(
            mapOf("content" to JsonPrimitive(content.trim()), "private" to JsonPrimitive(private.toString())),
        )
    }
}

/** Shared parts of the owner-only tools that act on one stored memory. Args: {"memoryId"} */
abstract class OneMemoryTool(protected val store: MemoryStore) : AgentTool {
    override fun validate(args: JsonObject): ArgsValidation =
        if (args.keys == setOf("memoryId") && !args.string("memoryId").isNullOrBlank()) ArgsValidation.Ok else ArgsValidation.Rejected("memoryId")

    protected suspend fun target(args: JsonObject): MemoryItem? = store.get(args.string("memoryId")!!)

    /** A private memory needs the owner's verification for any change (build doc §6.1). */
    override suspend fun touchesPersonal(args: JsonObject): Boolean = target(args)?.sensitivity == MemorySensitivity.SENSITIVE

    companion object {
        fun args(memoryId: String) = JsonObject(mapOf("memoryId" to JsonPrimitive(memoryId)))
    }
}

/** memory.activate — the owner keeps a proposed memory. Owner only, one tap (build doc §9.2). */
class ActivateMemoryTool(store: MemoryStore, private val clock: Clock, private val policy: MemoryPolicy = MemoryPolicy()) : OneMemoryTool(store) {
    override val spec = ToolSpec(
        id = "memory.activate",
        description = "Keep a memory the assistant proposed.",
        riskLevel = RiskLevel.L2,
        featureFlag = Flag.MEMORY.key,
        agentProposable = false,
        confirmation = Confirmation.ALWAYS,
        verification = "The memory is ACTIVE.",
    )

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val item = target(args) ?: return ToolOutcome.Failure("NOT_FOUND")
        if (item.status != MemoryStatus.PROPOSED) return ToolOutcome.Failure("NOT_PROPOSED")
        store.upsert(policy.approveByUser(item, clock.instant()))
        return ToolOutcome.Success("memory kept")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = target(args)?.status == MemoryStatus.ACTIVE
}

/**
 * memory.reject — the owner says no to a proposal. The "no" is kept (without being used) so the
 * same thing is not proposed again; it goes with the memory's retention period or a wipe.
 */
class RejectMemoryTool(store: MemoryStore, private val clock: Clock) : OneMemoryTool(store) {
    override val spec = ToolSpec(
        id = "memory.reject",
        description = "Turn down a memory the assistant proposed.",
        riskLevel = RiskLevel.L1,
        agentProposable = false,
        confirmation = Confirmation.NEVER,
        verification = "The memory is REJECTED.",
    )

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val item = target(args) ?: return ToolOutcome.Failure("NOT_FOUND")
        if (item.status != MemoryStatus.PROPOSED) return ToolOutcome.Failure("NOT_PROPOSED")
        store.upsert(item.copy(status = MemoryStatus.REJECTED, updatedAt = clock.instant()))
        return ToolOutcome.Success("memory turned down")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = target(args)?.status == MemoryStatus.REJECTED
}

/** memory.delete — the owner removes a memory for good. Owner only, one tap. */
class DeleteMemoryTool(store: MemoryStore) : OneMemoryTool(store) {
    override val spec = ToolSpec(
        id = "memory.delete",
        description = "Forget a memory.",
        riskLevel = RiskLevel.L2,
        agentProposable = false,
        confirmation = Confirmation.ALWAYS,
        verification = "The memory can no longer be read.",
    )

    override suspend fun execute(args: JsonObject): ToolOutcome {
        target(args) ?: return ToolOutcome.Failure("NOT_FOUND")
        store.delete(args.string("memoryId")!!)
        return ToolOutcome.Success("memory deleted")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = target(args) == null
}

/** memory.purgeExpired — removes memories past their retention period. Run by the app each day. Args: {} */
class PurgeExpiredMemoriesTool(private val store: MemoryStore, private val clock: Clock, private val policy: MemoryPolicy = MemoryPolicy()) : AgentTool {
    override val spec = ToolSpec(
        id = "memory.purgeExpired",
        description = "Remove memories whose retention period has ended.",
        riskLevel = RiskLevel.L1,
        agentProposable = false,
        confirmation = Confirmation.NEVER,
        verification = "No stored memory is past its retention period.",
    )

    override fun validate(args: JsonObject): ArgsValidation = if (args.isEmpty()) ArgsValidation.Ok else ArgsValidation.Rejected("keys")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val now = clock.instant()
        val expired = store.all().filter { policy.isExpired(it, now) }
        expired.forEach { store.delete(it.id) }
        return ToolOutcome.Success("memories removed=${expired.size}", mapOf("at" to now.toString()))
    }

    // Checked against the same moment, so a memory that expires a millisecond later is not a failure.
    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val at = java.time.Instant.parse(outcome.data["at"])
        return store.all().none { policy.isExpired(it, at) }
    }
}
