package com.equisteg.mydaycompanion.domain.backup

import com.equisteg.mydaycompanion.domain.ai.text.NumericDateOrder
import com.equisteg.mydaycompanion.domain.audit.AuditActor
import com.equisteg.mydaycompanion.domain.audit.AuditDecision
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.audit.AuditOutcome
import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemorySensitivity
import com.equisteg.mydaycompanion.domain.memory.MemorySource
import com.equisteg.mydaycompanion.domain.memory.MemoryStatus
import com.equisteg.mydaycompanion.domain.memory.MemoryType
import com.equisteg.mydaycompanion.domain.memory.Retention
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.EventSource
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.privacy.DataExportService
import com.equisteg.mydaycompanion.domain.privacy.ExportBundle
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import com.equisteg.mydaycompanion.domain.security.ChallengeAnswer
import com.equisteg.mydaycompanion.domain.security.ChallengeHasher
import com.equisteg.mydaycompanion.domain.security.PersonalApproval
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import com.equisteg.mydaycompanion.domain.security.SecurityStore
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What is inside the encrypted file: the full export plus security settings and challenge hashes (build doc §7.2). */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAt: String,
    val data: ExportBundle,
    val security: Map<String, String?>,
    val challenges: List<Map<String, String?>>,
) {
    companion object {
        const val FORMAT = "mydaycompanion-backup"
        const val SCHEMA_VERSION = 1
    }
}

/** A backup that passed every check, as the app's own types. Nothing is written until the owner approves. */
data class BackupContents(
    val createdAt: Instant,
    val tasks: List<Task>,
    val events: List<FixedEvent>,
    val plans: List<DayPlan>,
    val routines: List<Routine>,
    val memories: List<MemoryItem>,
    val audit: List<AuditEntry>,
    val preferences: UserPreferences,
    val security: SecuritySettings,
    val challenges: List<ChallengeAnswer>,
    /** SHA-256 of the decrypted JSON; the restore approval is bound to it. */
    val digest: String,
)

sealed interface BackupRead {
    data class Valid(val contents: BackupContents) : BackupRead
    data class Invalid(val reason: String) : BackupRead
}

class BackupService(private val export: DataExportService, private val security: SecurityStore) {
    suspend fun buildJson(): ByteArray {
        val data = export.buildBundle()
        val s = security.settings()
        val file = BackupFile(
            createdAt = data.exportedAt,
            data = data,
            security = mapOf(
                "appLockEnabled" to s.appLockEnabled.toString(), "lockAfterMinutes" to s.lockAfterMinutes.toString(),
                "wipeOnRepeatedFailure" to s.wipeOnRepeatedFailure.toString(), "personalApproval" to s.personalApproval.name,
            ),
            challenges = security.challenges().map {
                mapOf("id" to it.id, "question" to it.question, "salt" to it.salt, "hash" to it.hash, "iterations" to it.iterations.toString())
            },
        )
        return BackupReader.json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8)
    }
}

/**
 * Reads a decrypted backup as untrusted input (build doc §7.3, §11): unknown fields anywhere make
 * the whole file invalid, every value is parsed and range-checked, and content the app itself would
 * never have stored (instruction-like titles, poisoned memories) rejects the file.
 */
object BackupReader {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    const val MAX_TASKS = 20_000
    const val MAX_EVENTS = 20_000
    const val MAX_PLAN_BLOCKS = 100_000
    const val MAX_MEMORIES = 2_000
    const val MAX_AUDIT = 100_000
    private const val MAX_ID = 64
    private val actionPattern = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-zA-Z0-9]*)*$")

    private class Bad(val reason: String) : Exception(reason)

    private fun bad(reason: String): Nothing = throw Bad(reason)

    fun read(plaintext: ByteArray): BackupRead = try {
        val file = json.decodeFromString(BackupFile.serializer(), plaintext.toString(Charsets.UTF_8))
        BackupRead.Valid(contents(file, sha256(plaintext)))
    } catch (e: Bad) {
        BackupRead.Invalid(e.reason)
    } catch (e: kotlinx.serialization.SerializationException) {
        BackupRead.Invalid("NOT_VALID_JSON")
    } catch (e: IllegalArgumentException) {
        BackupRead.Invalid("NOT_VALID_JSON")
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun contents(f: BackupFile, digest: String): BackupContents {
        if (f.format != BackupFile.FORMAT || f.schemaVersion != BackupFile.SCHEMA_VERSION) bad("UNSUPPORTED_FORMAT")
        val d = f.data
        if (d.format != ExportBundle.FORMAT || d.schemaVersion != ExportBundle.SCHEMA_VERSION) bad("UNSUPPORTED_FORMAT")
        if (d.tasks.size > MAX_TASKS || d.fixedEvents.size > MAX_EVENTS || d.plans.size > MAX_PLAN_BLOCKS ||
            d.routines.size > Routine.MAX_ROUTINES || d.memories.size > MAX_MEMORIES || d.auditLog.size > MAX_AUDIT
        ) {
            bad("TOO_MANY_ITEMS")
        }
        val tasks = d.tasks.map(::task).also { unique(it.map(Task::id)) }
        val events = d.fixedEvents.map(::event).also { unique(it.map(FixedEvent::id)) }
        val routines = d.routines.map(::routine).also { unique(it.map(Routine::id)) }
        val memories = d.memories.map(::memory).also { unique(it.map(MemoryItem::id)) }
        val audit = d.auditLog.map(::auditEntry).also { unique(it.map(AuditEntry::id)) }
        val plans = d.plans.map(::block).groupBy({ it.first }, { it.second to it.third })
            .map { (date, rows) -> DayPlan(date, rows.map { it.first }, emptyList(), emptyList(), rows.first().second) }
        val ids = tasks.map { it.id }.toSet()
        if (tasks.any { it.parentId != null && it.parentId !in ids }) bad("BROKEN_REFERENCE")
        return BackupContents(
            createdAt = instant(f.createdAt),
            tasks = tasks,
            events = events,
            plans = plans,
            routines = routines,
            memories = memories,
            audit = audit,
            preferences = preferences(d.preferences),
            security = security(f.security),
            challenges = challenges(f.challenges),
            digest = digest,
        )
    }

    private fun unique(ids: List<String>) {
        if (ids.size != ids.toSet().size) bad("DUPLICATE_ID")
    }

    private fun Map<String, String?>.only(vararg keys: String): Map<String, String?> {
        if (!keys.toSet().containsAll(this.keys)) bad("UNKNOWN_FIELD")
        return this
    }

    private fun Map<String, String?>.req(key: String): String = this[key] ?: bad("MISSING_FIELD")

    private fun id(v: String?): String = v?.takeIf { it.isNotBlank() && it.length <= MAX_ID } ?: bad("BAD_ID")
    private fun instant(v: String?): Instant = v?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: bad("BAD_TIME")
    private fun date(v: String?): LocalDate = v?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: bad("BAD_DATE")
    private fun time(v: String?): LocalTime = v?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: bad("BAD_TIME")
    private fun int(v: String?, range: IntRange): Int = v?.toIntOrNull()?.takeIf { it in range } ?: bad("BAD_NUMBER")
    private fun bool(v: String?): Boolean = when (v) { "true" -> true; "false" -> false; else -> bad("BAD_FLAG") }
    private inline fun <reified E : Enum<E>> enum(v: String?): E = enumValues<E>().firstOrNull { it.name == v } ?: bad("BAD_VALUE")

    private fun title(v: String?): String {
        val t = v ?: bad("MISSING_FIELD")
        if (t.isBlank() || t.length > Task.MAX_TITLE_LENGTH) bad("BAD_TITLE")
        if (ContentSafety.looksLikeInstructionToAssistant(t)) bad("CONTENT_REJECTED")
        return t
    }

    private fun task(m: Map<String, String?>): Task {
        m.only("id", "title", "priority", "durationMinutes", "date", "deadline", "status", "parentId", "createdAt", "updatedAt", "preferredStart", "notes", "personal", "routineId", "remindAt")
        val notes = m["notes"]
        if (notes != null && notes.length > Task.MAX_NOTES_LENGTH) bad("BAD_NOTES")
        return Task(
            id = id(m["id"]), title = title(m["title"]), priority = enum<Priority>(m["priority"]),
            durationMinutes = int(m["durationMinutes"], Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES),
            date = m["date"]?.let(::date),
            deadline = m["deadline"]?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() ?: bad("BAD_TIME") },
            status = enum<TaskStatus>(m["status"]), parentId = m["parentId"]?.let(::id),
            createdAt = instant(m["createdAt"]), updatedAt = instant(m["updatedAt"]),
            preferredStart = m["preferredStart"]?.let(::time), notes = notes, personal = bool(m["personal"]),
            routineId = m["routineId"]?.let(::id), remindAt = m["remindAt"]?.let(::time),
        )
    }

    private fun event(m: Map<String, String?>): FixedEvent {
        m.only("id", "title", "date", "start", "durationMinutes", "source")
        return FixedEvent(id(m["id"]), title(m["title"]), date(m["date"]), time(m["start"]), int(m["durationMinutes"], 1..24 * 60), enum<EventSource>(m["source"]))
    }

    private fun block(m: Map<String, String?>): Triple<LocalDate, PlanBlock, String> {
        m.only("date", "refId", "title", "kind", "start", "end", "plannerVersion")
        val start = time(m["start"])
        val end = time(m["end"])
        if (end < start) bad("BAD_TIME")
        val version = m.req("plannerVersion").takeIf { it.length <= 40 } ?: bad("BAD_VALUE")
        return Triple(date(m["date"]), PlanBlock(id(m["refId"]), title(m["title"]), enum<BlockKind>(m["kind"]), start, end), version)
    }

    private fun routine(m: Map<String, String?>): Routine {
        m.only("id", "title", "frequency", "preferredStart", "durationMinutes", "enabled", "createdAt", "dayOfWeek", "remind")
        val frequency = enum<RoutineFrequency>(m["frequency"])
        val day = m["dayOfWeek"]?.let { enum<DayOfWeek>(it) }
        if ((frequency == RoutineFrequency.WEEKLY) != (day != null)) bad("BAD_VALUE")
        return Routine(
            id(m["id"]), title(m["title"]), frequency, m["preferredStart"]?.let(::time),
            int(m["durationMinutes"], Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES), bool(m["enabled"]),
            instant(m["createdAt"]), day, bool(m["remind"]),
        )
    }

    private fun memory(m: Map<String, String?>): MemoryItem {
        m.only("id", "type", "content", "source", "confidence", "status", "sensitivity", "retention", "createdAt", "updatedAt", "deletedAt")
        val status = enum<MemoryStatus>(m["status"])
        val content = m.req("content")
        if (content.isBlank() || content.length > 280) bad("BAD_MEMORY")
        // A turned-down suggestion is only a hash; anything else must pass the same rules as when it was stored.
        val marker = status == MemoryStatus.REJECTED && Regex("^rejected:[0-9a-f]{64}$").matches(content)
        if (!marker && ContentSafety.unsafeForMemory(content)) bad("CONTENT_REJECTED")
        val source = enum<MemorySource>(m["source"])
        val sensitivity = enum<MemorySensitivity>(m["sensitivity"])
        val type = enum<MemoryType>(m["type"])
        if (source != MemorySource.USER_EXPLICIT && (sensitivity == MemorySensitivity.SENSITIVE || type == MemoryType.EXPLICIT_INSTRUCTION)) bad("BAD_MEMORY")
        val confidence = m.req("confidence").toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: bad("BAD_NUMBER")
        return MemoryItem(
            id(m["id"]), type, content, source, confidence, status, sensitivity, enum<Retention>(m["retention"]),
            createdAt = instant(m["createdAt"]), updatedAt = instant(m["updatedAt"]), deletedAt = m["deletedAt"]?.let(::instant),
        )
    }

    private fun auditEntry(m: Map<String, String?>): AuditEntry {
        m.only("id", "timestamp", "actor", "action", "riskLevel", "decision", "outcome", "reasonCode", "detail")
        val action = m.req("action").takeIf { it.length <= 60 && actionPattern.matches(it) } ?: bad("BAD_AUDIT")
        val reason = m["reasonCode"]?.takeIf { it.length <= 60 && Regex("^[A-Za-z0-9_]+$").matches(it) }
        if (m["reasonCode"] != null && reason == null) bad("BAD_AUDIT")
        val detail = m["detail"]?.also { if (it.length > AuditEntry.MAX_DETAIL_CHARS) bad("BAD_AUDIT") }?.let(::cleanDetail)
        return AuditEntry(
            id(m["id"]), instant(m["timestamp"]), enum<AuditActor>(m["actor"]), action, m["riskLevel"]?.let { enum<RiskLevel>(it) },
            enum<AuditDecision>(m["decision"]), enum<AuditOutcome>(m["outcome"]), reason, detail,
        )
    }

    /**
     * History never holds what the owner wrote, and an imported entry can't be trusted to keep
     * that rule: only the app's own counters ("tasks=3") come across; any other text is dropped.
     */
    fun cleanDetail(detail: String): String? =
        detail.split(' ').filter { token ->
            val (key, value) = token.split('=', limit = 2).takeIf { it.size == 2 } ?: return@filter false
            key in DETAIL_COUNTERS && value.length <= 9 && value.all(Char::isDigit) && value.isNotEmpty()
        }.joinToString(" ").ifEmpty { null }

    private val DETAIL_COUNTERS = setOf(
        "tasks", "events", "routines", "memories", "blocks", "unscheduled", "subtasks", "subtasksMoved", "removed", "bytes", "reminders",
    )

    private fun preferences(m: Map<String, String?>): UserPreferences {
        m.only("dayStart", "dayEnd", "bufferMinutes", "speechOutputEnabled", "dateOrder", "reminderLeadMinutes", "onboardingDone")
        val p = UserPreferences(
            time(m["dayStart"]), time(m["dayEnd"]), int(m["bufferMinutes"], 0..60), bool(m["speechOutputEnabled"]),
            enum<NumericDateOrder>(m["dateOrder"]), int(m["reminderLeadMinutes"], 0..120), bool(m["onboardingDone"]),
        )
        if (!p.isValid) bad("BAD_PREFERENCES")
        return p
    }

    private fun security(m: Map<String, String?>): SecuritySettings {
        m.only("appLockEnabled", "lockAfterMinutes", "wipeOnRepeatedFailure", "personalApproval")
        val after = int(m["lockAfterMinutes"], 0..60).takeIf { it in SecuritySettings.LOCK_AFTER_CHOICES } ?: bad("BAD_SECURITY")
        return SecuritySettings(bool(m["appLockEnabled"]), after, bool(m["wipeOnRepeatedFailure"]), enum<PersonalApproval>(m["personalApproval"]))
    }

    private fun challenges(list: List<Map<String, String?>>): List<ChallengeAnswer> {
        val answers = list.map {
            it.only("id", "question", "salt", "hash", "iterations")
            ChallengeAnswer(it.req("id"), it.req("question"), it.req("salt"), it.req("hash"), int(it["iterations"], 1..10_000_000))
        }
        if (!(answers.isEmpty() || answers.size == ChallengeHasher.QUESTION_COUNT) || !answers.all(ChallengeHasher::isWellFormed)) bad("BAD_SECURITY")
        unique(answers.map { it.id })
        return answers
    }
}
