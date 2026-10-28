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
}
