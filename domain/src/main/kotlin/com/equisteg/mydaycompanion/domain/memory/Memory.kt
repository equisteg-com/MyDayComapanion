package com.equisteg.mydaycompanion.domain.memory

import com.equisteg.mydaycompanion.domain.repository.ErasableStore
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import java.time.Duration
import java.time.Instant

/** Two memories say the same thing when they match ignoring case, spaces and end punctuation. */
fun memoryKey(content: String): String = content.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')

/** For the agent's habit suggestions only: the same habit even when its "around HH:mm" time drifts. */
fun habitKey(content: String): String = memoryKey(content).replace(Regex(" around \\d{1,2}:\\d{2}$"), "")

/**
 * What is kept of a turned-down suggestion: a one-way hash of its habit key, so it is never
 * proposed again but its words (which can name a task) are not kept.
 */
fun rejectedMarker(content: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(habitKey(content).toByteArray())
    return "rejected:" + digest.joinToString("") { "%02x".format(it) }
}

/** The owner's own words: an exact match, or a suggestion they turned down. Different times are different memories. */
fun MemoryItem.sameAs(content: String): Boolean = memoryKey(this.content) == memoryKey(content) || this.content == rejectedMarker(content)

/** For suggestions: anything already stored about the same habit, at any time, kept or turned down. */
fun MemoryItem.coversHabit(content: String): Boolean = habitKey(this.content) == habitKey(content) || this.content == rejectedMarker(content)

enum class MemoryType { PREFERENCE, ROUTINE, RECURRING_TASK, BEHAVIOUR, EXPLICIT_INSTRUCTION, TEMPORARY_CONTEXT }

enum class MemorySource { USER_EXPLICIT, AGENT_PROPOSED, SYSTEM_DERIVED }

enum class MemoryStatus { PROPOSED, ACTIVE, REJECTED }

enum class MemorySensitivity { LOW, PERSONAL, SENSITIVE }

enum class Retention(val duration: Duration?) {
    SESSION(Duration.ofDays(1)),
    DAYS_30(Duration.ofDays(30)),
    DAYS_365(Duration.ofDays(365)),
    UNTIL_DELETED(null),
}

enum class Visibility { USER_VISIBLE }

data class MemoryItem(
    val id: String,
    val type: MemoryType,
    val content: String,
    val source: MemorySource,
    val confidence: Double,
    val status: MemoryStatus,
    val sensitivity: MemorySensitivity,
    val retention: Retention,
    val visibility: Visibility = Visibility.USER_VISIBLE,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

interface MemoryStore : ErasableStore {
    suspend fun all(): List<MemoryItem>
    suspend fun get(id: String): MemoryItem?
    suspend fun upsert(item: MemoryItem)
    suspend fun delete(id: String)
}

sealed interface MemoryDecision {
    data class Accepted(val item: MemoryItem) : MemoryDecision
    data class Rejected(val reason: String) : MemoryDecision
}

/**
 * Rules that keep memory explainable and resistant to poisoning:
 * - assistant-proposed memories start as PROPOSED and only the user can activate them;
 * - content that reads like a rule for the assistant, a credential or a data-exfiltration request is rejected;
 * - the assistant may not store SENSITIVE memories at all;
 * - every memory has a retention period and expired items are purged.
 */
class MemoryPolicy(private val maxContentChars: Int = 280) {

    fun admit(candidate: MemoryItem): MemoryDecision {
        val content = candidate.content.trim()
        if (content.isEmpty() || content.length > maxContentChars) return MemoryDecision.Rejected("CONTENT_LENGTH")
        if (candidate.confidence !in 0.0..1.0) return MemoryDecision.Rejected("CONFIDENCE_RANGE")
        if (ContentSafety.unsafeForMemory(content)) return MemoryDecision.Rejected("MEMORY_POISONING_SUSPECTED")
        if (candidate.source != MemorySource.USER_EXPLICIT && candidate.sensitivity == MemorySensitivity.SENSITIVE) {
            return MemoryDecision.Rejected("SENSITIVE_REQUIRES_USER")
        }
        if (candidate.source != MemorySource.USER_EXPLICIT && candidate.type == MemoryType.EXPLICIT_INSTRUCTION) {
            return MemoryDecision.Rejected("INSTRUCTION_REQUIRES_USER")
        }
        val status = if (candidate.source == MemorySource.USER_EXPLICIT) MemoryStatus.ACTIVE else MemoryStatus.PROPOSED
        return MemoryDecision.Accepted(candidate.copy(content = content, status = status))
    }

    /** Only a user gesture calls this. */
    fun approveByUser(item: MemoryItem, now: Instant): MemoryItem {
        check(item.status == MemoryStatus.PROPOSED) { "Only proposed memories can be approved" }
        return item.copy(status = MemoryStatus.ACTIVE, updatedAt = now)
    }

    fun isExpired(item: MemoryItem, now: Instant): Boolean {
        val d = item.retention.duration ?: return false
        return item.createdAt.plus(d) <= now
    }
}
