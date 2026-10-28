package com.equisteg.mydaycompanion.domain.audit

import com.equisteg.mydaycompanion.domain.repository.ErasableStore
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import java.time.Instant

enum class AuditActor { USER, AGENT, SYSTEM }

enum class AuditDecision { ALLOWED, DENIED, CONFIRMATION_REQUIRED, NOT_APPLICABLE }

enum class AuditOutcome { SUCCESS, FAILURE, NOT_EXECUTED, VERIFICATION_FAILED }

/**
 * Privacy-minimised audit record: which action, who asked, what the policy decided and what
 * happened. It never stores task titles, memory content or raw user text.
 */
data class AuditEntry(
    val id: String,
    val timestamp: Instant,
    val actor: AuditActor,
    val action: String,
    val riskLevel: RiskLevel?,
    val decision: AuditDecision,
    val outcome: AuditOutcome,
    val reasonCode: String? = null,
    val detail: String? = null,
) {
    companion object {
        const val MAX_DETAIL_CHARS = 200
    }
}

interface AuditLog : ErasableStore {
    suspend fun record(entry: AuditEntry)
    suspend fun recent(limit: Int): List<AuditEntry>
    suspend fun all(): List<AuditEntry>

    /** Entries older than [instant]; used to skip a retention run that has nothing to do. */
    suspend fun countBefore(instant: Instant): Int

    /** Removes entries older than [instant] (history retention, F16) and returns how many went. */
    suspend fun deleteBefore(instant: Instant): Int

    /** The newest [limit] entries that [filter] shows; Room filters in SQL so older matches aren't missed (OQ-22 c). */
    suspend fun recentMatching(filter: HistoryFilter, limit: Int): List<AuditEntry> =
        all().filter(filter::matches).sortedByDescending { it.timestamp }.take(limit)

    /** The newest successful run of [action], e.g. the last restore, which marks the entries imported with it. */
    suspend fun latestSuccess(action: String): AuditEntry? =
        all().filter { it.action == action && it.outcome == AuditOutcome.SUCCESS }.maxByOrNull { it.timestamp }
}

/** The History screen's filters (F16). Pure, so the screen and the tests agree on what each one shows. */
enum class HistoryFilter(val label: String) {
    ALL("All"),
    ATTENTION("Blocked or failed"),
    HIGH_RISK("High risk"),
    BY_ME("By me"),
    BY_ASSISTANT("By the assistant"),
    AUTOMATIC("Automatic"),
    ;

    fun matches(e: AuditEntry): Boolean = when (this) {
        ALL -> true
        ATTENTION -> e.decision == AuditDecision.DENIED || e.outcome == AuditOutcome.FAILURE || e.outcome == AuditOutcome.VERIFICATION_FAILED
        HIGH_RISK -> e.riskLevel != null && e.riskLevel >= RiskLevel.L3
        BY_ME -> e.actor == AuditActor.USER
        BY_ASSISTANT -> e.actor == AuditActor.AGENT
        AUTOMATIC -> e.actor == AuditActor.SYSTEM
    }
}

/** How long History keeps entries. The data map's default is 180 days; the owner can choose another. */
object HistoryRetention {
    val CHOICES_DAYS = listOf(30, 90, 180, 365)
    const val DEFAULT_DAYS = 180
}
