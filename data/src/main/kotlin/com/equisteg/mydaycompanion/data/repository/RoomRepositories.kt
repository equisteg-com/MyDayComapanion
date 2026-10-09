package com.equisteg.mydaycompanion.data.repository

import com.equisteg.mydaycompanion.data.db.AuditEntity
import com.equisteg.mydaycompanion.data.db.FixedEventEntity
import com.equisteg.mydaycompanion.data.db.MdcDatabase
import com.equisteg.mydaycompanion.data.db.MemoryEntity
import com.equisteg.mydaycompanion.data.db.PlanBlockEntity
import com.equisteg.mydaycompanion.data.db.PlanEntity
import com.equisteg.mydaycompanion.data.db.PreferencesEntity
import com.equisteg.mydaycompanion.data.db.RoutineEntity
import com.equisteg.mydaycompanion.data.db.TaskEntity
import com.equisteg.mydaycompanion.domain.audit.AuditActor
import com.equisteg.mydaycompanion.domain.audit.AuditDecision
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.audit.AuditOutcome
import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemorySensitivity
import com.equisteg.mydaycompanion.domain.memory.MemorySource
import com.equisteg.mydaycompanion.domain.memory.MemoryStatus
import com.equisteg.mydaycompanion.domain.memory.MemoryStore
import com.equisteg.mydaycompanion.domain.memory.MemoryType
import com.equisteg.mydaycompanion.domain.memory.Retention
import com.equisteg.mydaycompanion.domain.memory.Visibility
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.EventSource
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.PlanIssue
import com.equisteg.mydaycompanion.domain.model.PlanIssueType
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.repository.FixedEventRepository
import com.equisteg.mydaycompanion.domain.repository.PlanRepository
import com.equisteg.mydaycompanion.domain.repository.PreferencesStore
import com.equisteg.mydaycompanion.domain.repository.RoutineRepository
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RoomTaskRepository(db: MdcDatabase) : TaskRepository {
    private val dao = db.tasks()
    override val storeName = "tasks"
    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun all() = dao.all().map { it.toDomain() }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun upsert(tasks: List<Task>) = dao.upsert(tasks.map { it.toEntity() })
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count() = dao.count()
}

class RoomFixedEventRepository(db: MdcDatabase) : FixedEventRepository {
    private val dao = db.fixedEvents()
    override val storeName = "fixed_events"
    override fun observeForDate(date: LocalDate) = dao.observeForDate(date.toString()).map { l -> l.map { it.toDomain() } }
    override suspend fun forDate(date: LocalDate) = dao.forDate(date.toString()).map { it.toDomain() }
    override suspend fun all() = dao.all().map { it.toDomain() }
    override suspend fun upsert(events: List<FixedEvent>) = dao.upsert(events.map { it.toEntity() })
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count() = dao.count()
}

class RoomPlanRepository(db: MdcDatabase) : PlanRepository {
    private val dao = db.plans()
    override val storeName = "plans"

    override fun observe(date: LocalDate): Flow<DayPlan?> = dao.observePlan(date.toString()).map { it?.let { p -> load(p) } }

    override suspend fun get(date: LocalDate) = dao.plan(date.toString())?.let { load(it) }

    override suspend fun all() = dao.allPlans().map { load(it) }

    override suspend fun save(plan: DayPlan) {
        val entity = PlanEntity(
            date = plan.date.toString(),
            plannerVersion = plan.plannerVersion,
            unscheduledTaskIds = plan.unscheduledTaskIds.joinToString(","),
            issues = plan.issues.joinToString("\n") { "${it.type.name}|${it.refId.orEmpty()}|${it.message.replace('\n', ' ')}" },
        )
        val blocks = plan.blocks.mapIndexed { i, b ->
            PlanBlockEntity(plan.date.toString(), i, b.refId, b.title, b.kind.name, b.start.toString(), b.end.toString())
        }
        dao.replace(entity, blocks)
    }

    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count() = dao.count()

    private suspend fun load(p: PlanEntity) = DayPlan(
        date = LocalDate.parse(p.date),
        blocks = dao.blocks(p.date).map {
            PlanBlock(it.refId, it.title, BlockKind.valueOf(it.kind), LocalTime.parse(it.start), LocalTime.parse(it.end))
        },
        unscheduledTaskIds = p.unscheduledTaskIds.split(",").filter { it.isNotEmpty() },
        issues = p.issues.lines().filter { it.isNotBlank() }.map { line ->
            val parts = line.split("|", limit = 3)
            PlanIssue(PlanIssueType.valueOf(parts[0]), parts.getOrNull(1)?.ifEmpty { null }, parts.getOrElse(2) { "" })
        },
        plannerVersion = p.plannerVersion,
    )
}

class RoomRoutineRepository(db: MdcDatabase) : RoutineRepository {
    private val dao = db.routines()
    override val storeName = "routines"
    override suspend fun all() = dao.all().map {
        Routine(it.id, it.title, RoutineFrequency.valueOf(it.frequency), it.preferredStart?.let(LocalTime::parse), it.durationMinutes, it.enabled, Instant.parse(it.createdAt))
    }
    override suspend fun upsert(routine: Routine) = dao.upsert(
        RoutineEntity(routine.id, routine.title, routine.frequency.name, routine.preferredStart?.toString(), routine.durationMinutes, routine.enabled, routine.createdAt.toString()),
    )
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count() = dao.count()
}

class RoomMemoryStore(db: MdcDatabase) : MemoryStore {
    private val dao = db.memories()
    override val storeName = "memories"
    override suspend fun all() = dao.all().map { it.toDomain() }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun upsert(item: MemoryItem) = dao.upsert(item.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count() = dao.count()
}

class RoomAuditLog(db: MdcDatabase) : AuditLog {
    private val dao = db.audit()
    override val storeName = "audit_log"
    override suspend fun record(entry: AuditEntry) = dao.insert(entry.toEntity())
    override suspend fun recent(limit: Int) = dao.recent(limit).map { it.toDomain() }
    override suspend fun all() = dao.all().map { it.toDomain() }
    fun observeRecent(limit: Int): Flow<List<AuditEntry>> = dao.observeRecent(limit).map { l -> l.map { it.toDomain() } }
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count() = dao.count()
}

class RoomPreferencesStore(db: MdcDatabase) : PreferencesStore {
    private val dao = db.preferences()
    override val storeName = "preferences"
    override suspend fun get(): UserPreferences = dao.get()?.let {
        UserPreferences(LocalTime.parse(it.dayStart), LocalTime.parse(it.dayEnd), it.bufferMinutes, it.speechOutputEnabled)
    } ?: UserPreferences()
    override suspend fun set(preferences: UserPreferences) = dao.set(
        PreferencesEntity(1, preferences.dayStart.toString(), preferences.dayEnd.toString(), preferences.bufferMinutes, preferences.speechOutputEnabled),
    )
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count() = dao.count()
}

private fun TaskEntity.toDomain() = Task(
    id, title, Priority.valueOf(priority), durationMinutes, date?.let(LocalDate::parse), deadline?.let(LocalDateTime::parse),
    TaskStatus.valueOf(status), parentId, Instant.parse(createdAt), Instant.parse(updatedAt), preferredStart?.let(LocalTime::parse),
)

private fun Task.toEntity() = TaskEntity(
    id, title, priority.name, durationMinutes, date?.toString(), deadline?.toString(), status.name, parentId, createdAt.toString(), updatedAt.toString(),
    preferredStart?.toString(),
)

private fun FixedEventEntity.toDomain() = FixedEvent(id, title, LocalDate.parse(date), LocalTime.parse(start), durationMinutes, EventSource.valueOf(source))

private fun FixedEvent.toEntity() = FixedEventEntity(id, title, date.toString(), start.toString(), durationMinutes, source.name)

private fun MemoryEntity.toDomain() = MemoryItem(
    id, MemoryType.valueOf(type), content, MemorySource.valueOf(source), confidence, MemoryStatus.valueOf(status),
    MemorySensitivity.valueOf(sensitivity), Retention.valueOf(retention), Visibility.valueOf(visibility),
    Instant.parse(createdAt), Instant.parse(updatedAt), deletedAt?.let(Instant::parse),
)

private fun MemoryItem.toEntity() = MemoryEntity(
    id, type.name, content, source.name, confidence, status.name, sensitivity.name, retention.name, visibility.name,
    createdAt.toString(), updatedAt.toString(), deletedAt?.toString(),
)

private fun AuditEntity.toDomain() = AuditEntry(
    id, Instant.parse(timestamp), AuditActor.valueOf(actor), action, riskLevel?.let(RiskLevel::valueOf),
    AuditDecision.valueOf(decision), AuditOutcome.valueOf(outcome), reasonCode, detail,
)

private fun AuditEntry.toEntity() = AuditEntity(
    id, timestamp.toString(), actor.name, action, riskLevel?.name, decision.name, outcome.name, reasonCode, detail,
)
