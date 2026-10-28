package com.equisteg.mydaycompanion.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.equisteg.mydaycompanion.data.db.MdcDatabase
import com.equisteg.mydaycompanion.data.db.Migrations
import com.equisteg.mydaycompanion.data.repository.RoomAuditLog
import com.equisteg.mydaycompanion.data.repository.RoomFixedEventRepository
import com.equisteg.mydaycompanion.data.repository.RoomMemoryStore
import com.equisteg.mydaycompanion.data.repository.RoomPlanRepository
import com.equisteg.mydaycompanion.data.repository.RoomPreferencesStore
import com.equisteg.mydaycompanion.data.repository.RoomRoutineRepository
import com.equisteg.mydaycompanion.data.repository.RoomSecurityStore
import com.equisteg.mydaycompanion.data.repository.RoomTaskRepository
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
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.planner.DeterministicPlanner
import com.equisteg.mydaycompanion.domain.planner.PlanRequest
import com.equisteg.mydaycompanion.domain.privacy.DataDeletionService
import com.equisteg.mydaycompanion.domain.privacy.DataExportService
import com.equisteg.mydaycompanion.domain.security.ChallengeHasher
import com.equisteg.mydaycompanion.domain.security.PersonalApproval
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the Room layer. Robolectric cannot load SQLCipher's native library, so
 * these run on plain in-memory Room; encryption is covered by on-device tests (see docs/testing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomStorageTest {
    private lateinit var db: MdcDatabase
    private val now = Instant.parse("2026-10-06T07:00:00Z")
    private val date = LocalDate.of(2026, 10, 7)

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MdcDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    private suspend fun seed() {
        RoomTaskRepository(db).upsert(
            listOf(
                Task("t1", "Finish the proposal", Priority.HIGH, 120, date, LocalDateTime.of(date, LocalTime.of(17, 0)), createdAt = now),
                Task("t2", "Call Raj", Priority.MEDIUM, 15, date, createdAt = now, parentId = "t1", notes = "Ask about Friday", personal = true),
            ),
        )
        RoomFixedEventRepository(db).upsert(listOf(FixedEvent("e1", "Meeting", date, LocalTime.of(11, 0), 60)))
        RoomRoutineRepository(db).upsert(Routine("r1", "Gym", RoutineFrequency.WEEKDAYS, LocalTime.of(7, 0), 60, createdAt = now))
        RoomMemoryStore(db).upsert(MemoryItem("m1", MemoryType.PREFERENCE, "Likes mornings", MemorySource.USER_EXPLICIT, 1.0, MemoryStatus.ACTIVE, MemorySensitivity.LOW, Retention.UNTIL_DELETED, createdAt = now, updatedAt = now))
        RoomAuditLog(db).record(AuditEntry("a1", now, AuditActor.USER, "tasks.create", RiskLevel.L2, AuditDecision.ALLOWED, AuditOutcome.SUCCESS))
        RoomPreferencesStore(db).set(
            UserPreferences(dayStart = LocalTime.of(8, 0), dateOrder = NumericDateOrder.MONTH_DAY, reminderLeadMinutes = 30, onboardingDone = true),
        )
        RoomSecurityStore(db).apply {
            saveSettings(SecuritySettings(lockAfterMinutes = 1, wipeOnRepeatedFailure = true, personalApproval = PersonalApproval.CHALLENGE))
            replaceChallenges(listOf(ChallengeHasher.hash("q1", "First school?", "St Mary's")))
            recordFailure(now)
        }
        val tasks = RoomTaskRepository(db).all()
        val plan = DeterministicPlanner().plan(PlanRequest(date, tasks, RoomFixedEventRepository(db).forDate(date)))
        RoomPlanRepository(db).save(plan)
    }

    @Test
    fun `domain objects round trip through Room unchanged`() = runTest {
        seed()
        val t1 = RoomTaskRepository(db).get("t1")!!
        assertEquals(LocalDateTime.of(date, LocalTime.of(17, 0)), t1.deadline)
        assertEquals("t1", RoomTaskRepository(db).get("t2")!!.parentId)
        assertEquals("Ask about Friday", RoomTaskRepository(db).get("t2")!!.notes)
        assertTrue(RoomTaskRepository(db).get("t2")!!.personal)
        assertEquals(
            UserPreferences(dayStart = LocalTime.of(8, 0), dateOrder = NumericDateOrder.MONTH_DAY, reminderLeadMinutes = 30, onboardingDone = true),
            RoomPreferencesStore(db).get(),
        )
        val plan = RoomPlanRepository(db).get(date)!!
        val again = DeterministicPlanner().plan(PlanRequest(date, RoomTaskRepository(db).all(), RoomFixedEventRepository(db).forDate(date)))
        assertEquals(again, plan)
    }

    @Test
    fun `security settings, hashed answers and failures round trip`() = runTest {
        seed()
        val store = RoomSecurityStore(db)
        assertEquals(SecuritySettings(lockAfterMinutes = 1, wipeOnRepeatedFailure = true, personalApproval = PersonalApproval.CHALLENGE), store.settings())
        assertTrue(ChallengeHasher.matches(store.challenges().single(), "st marys"))
        assertEquals(listOf(now), store.failures())
        store.clearFailures()
        assertEquals(2, store.count())
    }

    @Test
    fun `delete everything empties every table`() = runTest {
        seed()
        val stores = listOf(
            RoomTaskRepository(db), RoomFixedEventRepository(db), RoomPlanRepository(db), RoomRoutineRepository(db),
            RoomMemoryStore(db), RoomAuditLog(db), RoomPreferencesStore(db), RoomSecurityStore(db),
        )
        assertTrue(stores.all { it.count() > 0 })
        val report = DataDeletionService(stores).deleteEverything()
        assertTrue(report.verified, report.toString())
    }

    @Test
    fun `History retention removes only entries before the cut-off`() = runTest {
        val log = RoomAuditLog(db)
        fun at(i: String, t: String) = AuditEntry(i, java.time.Instant.parse(t), AuditActor.SYSTEM, "plan.generate", RiskLevel.L1, AuditDecision.ALLOWED, AuditOutcome.SUCCESS)
        log.record(at("old", "2026-01-01T10:00:00.250Z"))
        log.record(at("edge", "2026-04-09T07:00:00Z"))
        log.record(at("new", "2026-10-05T23:59:59.999Z"))
        val cutoff = java.time.Instant.parse("2026-04-09T07:00:00Z")
        assertEquals(1, log.countBefore(cutoff))
        assertEquals(1, log.deleteBefore(cutoff))
        assertEquals(listOf("edge", "new"), log.all().map { it.id })
        assertEquals(0, log.countBefore(cutoff))
    }

    @Test
    fun `export reads every table`() = runTest {
        seed()
        val export = DataExportService(
            RoomTaskRepository(db), RoomFixedEventRepository(db), RoomPlanRepository(db), RoomRoutineRepository(db),
            RoomMemoryStore(db), RoomAuditLog(db), RoomPreferencesStore(db), Clock.fixed(now, ZoneOffset.UTC),
        ).buildBundle()
        assertEquals(2, export.tasks.size)
        assertEquals(1, export.fixedEvents.size)
        assertEquals(1, export.routines.size)
        assertEquals(1, export.memories.size)
        assertEquals(1, export.auditLog.size)
        // t1 is broken into the step t2, so the plan holds t2 and the meeting, not t1 (planner v3).
        assertEquals(2, export.plans.size)
        assertEquals("08:00", export.preferences["dayStart"])
    }

    @Test
    fun `migration chain is continuous up to the current version`() {
        val covered = Migrations.ALL.map { it.startVersion to it.endVersion }.sortedBy { it.first }
        var v = 1
        for ((from, to) in covered) {
            assertEquals(v, from, "gap in migrations")
            v = to
        }
        assertEquals(MdcDatabase.VERSION, v)
    }

    @Test
    fun `a restore that fails part-way leaves every table as it was`() = runTest {
        seed()
        val tasks = RoomTaskRepository(db)
        val audit = RoomAuditLog(db)
        val result = runCatching {
            com.equisteg.mydaycompanion.data.repository.RoomStoreTransactions(db).atomically {
                tasks.deleteAll()
                audit.deleteAll()
                tasks.upsert(listOf(Task("n1", "From the backup", createdAt = now)))
                error("disk full")
            }
        }
        assertTrue(result.isFailure)
        assertEquals(setOf("t1", "t2"), tasks.all().map { it.id }.toSet())
        assertEquals(1, audit.count())
    }

    @Test
    fun `History filters run in SQL and find old matches past the newest entries`() = runTest {
        val audit = RoomAuditLog(db)
        // One old denied entry, then many newer successful ones: the filter must still find it.
        audit.record(AuditEntry("old-denied", now.minusSeconds(3_600), AuditActor.AGENT, "data.export", RiskLevel.L3, AuditDecision.DENIED, AuditOutcome.NOT_EXECUTED))
        repeat(20) { i ->
            audit.record(AuditEntry("n$i", now.plusSeconds(i.toLong()), AuditActor.USER, "tasks.complete", RiskLevel.L1, AuditDecision.ALLOWED, AuditOutcome.SUCCESS))
        }
        audit.record(AuditEntry("sys", now.plusSeconds(100), AuditActor.SYSTEM, "backup.restore", RiskLevel.L4, AuditDecision.ALLOWED, AuditOutcome.SUCCESS))

        val few = 5
        assertTrue(audit.recent(few).none { it.id == "old-denied" })
        assertEquals(listOf("old-denied"), audit.recentMatching(com.equisteg.mydaycompanion.domain.audit.HistoryFilter.ATTENTION, few).map { it.id })
        assertEquals(listOf("sys", "old-denied"), audit.recentMatching(com.equisteg.mydaycompanion.domain.audit.HistoryFilter.HIGH_RISK, few).map { it.id })
        assertEquals(listOf("old-denied"), audit.recentMatching(com.equisteg.mydaycompanion.domain.audit.HistoryFilter.BY_ASSISTANT, few).map { it.id })
        assertEquals(listOf("sys"), audit.recentMatching(com.equisteg.mydaycompanion.domain.audit.HistoryFilter.AUTOMATIC, few).map { it.id })
        assertEquals(few, audit.recentMatching(com.equisteg.mydaycompanion.domain.audit.HistoryFilter.BY_ME, few).size)
        assertEquals(few, audit.recentMatching(com.equisteg.mydaycompanion.domain.audit.HistoryFilter.ALL, few).size)
        assertEquals("sys", audit.latestSuccess("backup.restore")?.id)
        assertEquals(null, audit.latestSuccess("data.export"))
    }
}
