package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.backup.BackupCodec
import com.equisteg.mydaycompanion.domain.backup.BackupContents
import com.equisteg.mydaycompanion.domain.backup.BackupService
import com.equisteg.mydaycompanion.domain.backup.Decrypted
import com.equisteg.mydaycompanion.domain.memory.MemoryStore
import com.equisteg.mydaycompanion.domain.repository.FixedEventRepository
import com.equisteg.mydaycompanion.domain.repository.PlanRepository
import com.equisteg.mydaycompanion.domain.repository.PreferencesStore
import com.equisteg.mydaycompanion.domain.repository.RoutineRepository
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.security.SecurityStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds, for a few minutes, what the backup tools must not receive as arguments: the owner's
 * passphrase (for a new backup) and a decrypted, validated backup (for a restore). Arguments, the
 * approval fingerprint and History only ever see a random token. Each item is taken once.
 */
class BackupStaging(private val clock: Clock, private val ttl: Duration = Duration.ofMinutes(10)) {
    private class Item(val value: Any, val at: Instant)

    private val items = ConcurrentHashMap<String, Item>()

    /** Keeps a copy; the caller should clear its own array. */
    fun stagePassphrase(passphrase: CharArray): String = put(passphrase.copyOf())

    fun stageRestore(contents: BackupContents): String = put(contents)

    fun takePassphrase(token: String): CharArray? = take(token) as? CharArray

    fun peekRestore(token: String): BackupContents? = fresh(token)?.value as? BackupContents

    fun takeRestore(token: String): BackupContents? = take(token) as? BackupContents

    fun has(token: String): Boolean = fresh(token) != null

    /** The owner cancelled: forget the item now rather than when it expires. */
    fun discard(token: String) {
        (items.remove(token)?.value as? CharArray)?.fill(' ')
    }

    private fun put(value: Any): String {
        purge()
        return UUID.randomUUID().toString().also { items[it] = Item(value, clock.instant()) }
    }

    private fun fresh(token: String): Item? = items[token]?.takeIf { Duration.between(it.at, clock.instant()) <= ttl }

    private fun take(token: String): Any? = fresh(token)?.value.also { items.remove(token) }

    private fun purge() {
        val now = clock.instant()
        items.entries.removeIf { (_, item) ->
            (Duration.between(item.at, now) > ttl).also { expired -> if (expired) (item.value as? CharArray)?.fill(' ') }
        }
    }
}

/**
 * backup.create — writes an encrypted backup of everything to a file the owner picked (build doc
 * §7.2). L3, owner only: Confirm plus the device credential. The file is decrypted once before it
 * is written, so a backup that couldn't be restored is never saved.
 * Args: {"target": export target token, "key": staged passphrase token}
 */
class CreateBackupTool(
    private val backups: BackupService,
    private val targets: ExportTargets,
    private val staging: BackupStaging,
    private val codec: BackupCodec = BackupCodec(),
) : AgentTool {
    override val spec = ToolSpec(
        id = "backup.create",
        description = "Write an encrypted backup of all data to a file the owner chose.",
        riskLevel = RiskLevel.L3,
        agentProposable = false,
        confirmation = Confirmation.ALWAYS,
        verification = "The file was written in full, and it opens with the passphrase.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.keys == setOf("target", "key") && targets.exists(args.string("target").orEmpty()) && staging.has(args.string("key").orEmpty())) {
            ArgsValidation.Ok
        } else {
            ArgsValidation.Rejected("target")
        }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val passphrase = staging.takePassphrase(args.string("key")!!) ?: return ToolOutcome.Failure("EXPIRED")
        try {
            val plain = backups.buildJson()
            val file = codec.encrypt(plain, passphrase)
            val readable = (codec.decrypt(file, passphrase) as? Decrypted.Ok)?.plaintext?.contentEquals(plain) == true
            plain.fill(0)
            if (!readable) return ToolOutcome.Failure("SELF_CHECK_FAILED")
            val written = targets.write(args.string("target")!!, file) ?: return ToolOutcome.Failure("TARGET_UNAVAILABLE")
            return ToolOutcome.Success("backup bytes=$written", mapOf("expected" to file.size.toString(), "written" to written.toString()))
        } finally {
            passphrase.fill(' ')
        }
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = outcome.data["expected"] == outcome.data["written"]

    companion object {
        fun args(target: String, key: String) = JsonObject(mapOf("target" to JsonPrimitive(target), "key" to JsonPrimitive(key)))
    }
}

/**
 * backup.restore — replaces everything on this phone with a decrypted, validated backup (build doc
 * §7.3). L4, owner only: device credential plus a challenge answer when questions are set up. The
 * approval covers exactly that backup: its SHA-256 is part of the arguments. The phone's own
 * database key is kept; nothing of the old phone's key is in a backup.
 * Args: {"staged": token, "sha256": digest of the decrypted backup}
 */
class RestoreBackupTool(
    private val staging: BackupStaging,
    private val tasks: TaskRepository,
    private val events: FixedEventRepository,
    private val plans: PlanRepository,
    private val routines: RoutineRepository,
    private val memories: MemoryStore,
    private val audit: AuditLog,
    private val preferences: PreferencesStore,
    private val security: SecurityStore,
) : AgentTool {
    override val spec = ToolSpec(
        id = "backup.restore",
        description = "Replace all data on this phone with the contents of a backup.",
        riskLevel = RiskLevel.L4,
        agentProposable = false,
        confirmation = Confirmation.ALWAYS,
        verification = "Every store holds exactly what the backup holds.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        if (args.keys != setOf("staged", "sha256")) return ArgsValidation.Rejected("keys")
        val staged = staging.peekRestore(args.string("staged").orEmpty()) ?: return ArgsValidation.Rejected("staged")
        return if (staged.digest == args.string("sha256")) ArgsValidation.Ok else ArgsValidation.Rejected("sha256")
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val c = staging.takeRestore(args.string("staged")!!) ?: return ToolOutcome.Failure("EXPIRED")
        if (c.digest != args.string("sha256")) return ToolOutcome.Failure("CHANGED")
        listOf(tasks, events, plans, routines, memories, audit, preferences, security).forEach { it.deleteAll() }
        tasks.upsert(c.tasks)
        events.upsert(c.events)
        c.plans.forEach { plans.save(it) }
        c.routines.forEach { routines.upsert(it) }
        c.memories.forEach { memories.upsert(it) }
        c.audit.forEach { audit.record(it) }
        preferences.set(c.preferences)
        security.saveSettings(c.security)
        security.replaceChallenges(c.challenges)
        restored = c
        return ToolOutcome.Success("restored tasks=${c.tasks.size} routines=${c.routines.size} memories=${c.memories.size}")
    }

    // Set by execute and read once by verify; restores never run concurrently (one approval at a time).
    private var restored: BackupContents? = null

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val c = restored ?: return false
        restored = null
        // The orchestrator records the restore itself in History right after this, so audit is checked before that.
        return tasks.count() == c.tasks.size && events.count() == c.events.size && plans.all().size == c.plans.size &&
            routines.count() == c.routines.size && memories.count() == c.memories.size && audit.count() == c.audit.size &&
            preferences.get() == c.preferences && security.settings() == c.security &&
            security.challenges().sortedBy { it.id } == c.challenges.sortedBy { it.id }
    }

    companion object {
        fun args(staged: String, digest: String) = JsonObject(mapOf("staged" to JsonPrimitive(staged), "sha256" to JsonPrimitive(digest)))
    }
}
