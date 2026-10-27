package com.equisteg.mydaycompanion.domain.privacy

import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.memory.MemoryStore
import com.equisteg.mydaycompanion.domain.repository.FixedEventRepository
import com.equisteg.mydaycompanion.domain.repository.PlanRepository
import com.equisteg.mydaycompanion.domain.repository.PreferencesStore
import com.equisteg.mydaycompanion.domain.repository.RoutineRepository
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock

@Serializable
data class ExportBundle(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val tasks: List<Map<String, String?>>,
    val fixedEvents: List<Map<String, String?>>,
    val plans: List<Map<String, String?>>,
    val routines: List<Map<String, String?>>,
    val memories: List<Map<String, String?>>,
    val auditLog: List<Map<String, String?>>,
    val preferences: Map<String, String?>,
) {
    companion object {
        const val FORMAT = "mydaycompanion-export"
        const val SCHEMA_VERSION = 1
    }
}

/** Builds a complete, human-readable JSON export of everything the app stores about the user. */
class DataExportService(
    private val tasks: TaskRepository,
    private val events: FixedEventRepository,
    private val plans: PlanRepository,
    private val routines: RoutineRepository,
    private val memories: MemoryStore,
    private val audit: AuditLog,
    private val preferences: PreferencesStore,
    private val clock: Clock,
) {
    suspend fun buildBundle(): ExportBundle = ExportBundle(
        exportedAt = clock.instant().toString(),
        tasks = tasks.all().map {
            mapOf(
                "id" to it.id, "title" to it.title, "priority" to it.priority.name,
                "durationMinutes" to it.durationMinutes.toString(), "date" to it.date?.toString(),
                "deadline" to it.deadline?.toString(), "status" to it.status.name, "parentId" to it.parentId,
                "createdAt" to it.createdAt.toString(), "updatedAt" to it.updatedAt.toString(),
            )
        },
        fixedEvents = events.all().map {
            mapOf(
                "id" to it.id, "title" to it.title, "date" to it.date.toString(), "start" to it.start.toString(),
                "durationMinutes" to it.durationMinutes.toString(), "source" to it.source.name,
            )
        },
        plans = plans.all().flatMap { plan ->
            plan.blocks.map {
                mapOf(
                    "date" to plan.date.toString(), "refId" to it.refId, "title" to it.title, "kind" to it.kind.name,
                    "start" to it.start.toString(), "end" to it.end.toString(), "plannerVersion" to plan.plannerVersion,
                )
            }
        },
        routines = routines.all().map {
            mapOf(
                "id" to it.id, "title" to it.title, "frequency" to it.frequency.name,
                "preferredStart" to it.preferredStart?.toString(), "durationMinutes" to it.durationMinutes.toString(),
                "enabled" to it.enabled.toString(), "createdAt" to it.createdAt.toString(),
            )
        },
        memories = memories.all().map {
            mapOf(
                "id" to it.id, "type" to it.type.name, "content" to it.content, "source" to it.source.name,
                "confidence" to it.confidence.toString(), "status" to it.status.name,
                "sensitivity" to it.sensitivity.name, "retention" to it.retention.name,
                "createdAt" to it.createdAt.toString(), "updatedAt" to it.updatedAt.toString(),
                "deletedAt" to it.deletedAt?.toString(),
            )
        },
        auditLog = audit.all().map {
            mapOf(
                "id" to it.id, "timestamp" to it.timestamp.toString(), "actor" to it.actor.name, "action" to it.action,
                "riskLevel" to it.riskLevel?.name, "decision" to it.decision.name, "outcome" to it.outcome.name,
                "reasonCode" to it.reasonCode, "detail" to it.detail,
            )
        },
        preferences = preferences.get().let {
            mapOf(
                "dayStart" to it.dayStart.toString(), "dayEnd" to it.dayEnd.toString(),
                "bufferMinutes" to it.bufferMinutes.toString(), "speechOutputEnabled" to it.speechOutputEnabled.toString(),
            )
        },
    )

    suspend fun exportJson(): String = json.encodeToString(ExportBundle.serializer(), buildBundle())

    companion object {
        val json = Json { prettyPrint = true; encodeDefaults = true }
    }
}
