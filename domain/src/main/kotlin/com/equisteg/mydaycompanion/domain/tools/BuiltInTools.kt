package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.domain.future.NotificationScheduler
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.planner.PlanRequest
import com.equisteg.mydaycompanion.domain.planner.PlanValidator
import com.equisteg.mydaycompanion.domain.planner.PlannerEngine
import com.equisteg.mydaycompanion.domain.privacy.DataDeletionService
import com.equisteg.mydaycompanion.domain.privacy.DataExportService
import com.equisteg.mydaycompanion.domain.repository.FixedEventRepository
import com.equisteg.mydaycompanion.domain.repository.PlanRepository
import com.equisteg.mydaycompanion.domain.repository.PreferencesStore
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import kotlinx.serialization.json.JsonObject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val RANDOM = IdGenerator { UUID.randomUUID().toString() }
    }
}

private fun parseDate(s: String?): LocalDate? = s?.let { try { LocalDate.parse(it) } catch (e: DateTimeParseException) { null } }

private fun parseTime(s: String?): LocalTime? = s?.let { try { LocalTime.parse(it) } catch (e: DateTimeParseException) { null } }

private fun validTitle(t: String?): Boolean =
    t != null && t.isNotBlank() && t.length <= Task.MAX_TITLE_LENGTH && !ContentSafety.looksLikeInstructionToAssistant(t)

private fun validDuration(d: Int?): Boolean = d != null && d in Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES

/**
 * tasks.create — stores tasks and fixed events for one day.
 * Args: {"date":"YYYY-MM-DD","tasks":[{"title","priority","durationMinutes","deadline"?}],"fixedEvents":[{"title","start","durationMinutes"}]}
 */
class CreateTasksTool(
    private val tasks: TaskRepository,
    private val events: FixedEventRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) : AgentTool {
    override val spec = ToolSpec(
        id = "tasks.create",
        description = "Save tasks and fixed events the user described for a day.",
        riskLevel = RiskLevel.L2,
        agentProposable = true,
        confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT,
        verification = "Re-read every created task and event by id.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        if (!args.onlyKeys("date", "tasks", "fixedEvents")) return ArgsValidation.Rejected("unexpected keys")
        parseDate(args.string("date")) ?: return ArgsValidation.Rejected("date")
        val ts = args.array("tasks") ?: return ArgsValidation.Rejected("tasks")
        val es = args.array("fixedEvents") ?: return ArgsValidation.Rejected("fixedEvents")
        if (ts.size + es.size == 0 || ts.size > 25 || es.size > 15) return ArgsValidation.Rejected("size")
        for (e in ts) {
            val o = e.asObject() ?: return ArgsValidation.Rejected("task")
            if (!o.onlyKeys("title", "priority", "durationMinutes", "deadline")) return ArgsValidation.Rejected("task keys")
            if (!validTitle(o.string("title"))) return ArgsValidation.Rejected("task title")
            if (Priority.entries.none { it.name == o.string("priority") }) return ArgsValidation.Rejected("task priority")
            if (!validDuration(o.int("durationMinutes"))) return ArgsValidation.Rejected("task duration")
            val dl = o.optionalString("deadline").getOrElse { return ArgsValidation.Rejected("task deadline") }
            if (dl != null && parseTime(dl) == null) return ArgsValidation.Rejected("task deadline")
        }
        for (e in es) {
            val o = e.asObject() ?: return ArgsValidation.Rejected("event")
            if (!o.onlyKeys("title", "start", "durationMinutes")) return ArgsValidation.Rejected("event keys")
            if (!validTitle(o.string("title"))) return ArgsValidation.Rejected("event title")
            if (parseTime(o.string("start")) == null) return ArgsValidation.Rejected("event start")
            if (!validDuration(o.int("durationMinutes"))) return ArgsValidation.Rejected("event duration")
        }
        return ArgsValidation.Ok
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val date = parseDate(args.string("date"))!!
        val now: Instant = clock.instant()
        val newTasks = args.array("tasks")!!.map { it.asObject()!! }.map { o ->
            Task(
                id = ids.newId(),
                title = o.string("title")!!.trim(),
                priority = Priority.valueOf(o.string("priority")!!),
                durationMinutes = o.int("durationMinutes")!!,
                date = date,
                deadline = parseTime(o.optionalString("deadline").getOrNull())?.let { date.atTime(it) },
                createdAt = now,
            )
        }
        val newEvents = args.array("fixedEvents")!!.map { it.asObject()!! }.map { o ->
            FixedEvent(
                id = ids.newId(),
                title = o.string("title")!!.trim(),
                date = date,
                start = parseTime(o.string("start"))!!,
                durationMinutes = o.int("durationMinutes")!!,
            )
        }
        tasks.upsert(newTasks)
        events.upsert(newEvents)
        return ToolOutcome.Success(
            summary = "tasks=${newTasks.size} events=${newEvents.size}",
            data = mapOf(
                "taskIds" to newTasks.joinToString(",") { it.id },
                "eventIds" to newEvents.joinToString(",") { it.id },
            ),
        )
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val taskIds = outcome.data["taskIds"].orEmpty().split(",").filter { it.isNotEmpty() }
        val eventIds = outcome.data["eventIds"].orEmpty().split(",").filter { it.isNotEmpty() }.toSet()
        val date = parseDate(args.string("date"))!!
        return taskIds.all { tasks.get(it) != null } && events.forDate(date).map { it.id }.containsAll(eventIds)
    }
}

/** plan.generate — deterministically (re)plans a day from stored tasks and events, validates and saves it. Args: {"date","notBefore"?} */
class GeneratePlanTool(
    private val tasks: TaskRepository,
    private val events: FixedEventRepository,
    private val plans: PlanRepository,
    private val preferences: PreferencesStore,
    private val planner: PlannerEngine,
) : AgentTool {
    override val spec = ToolSpec(
        id = "plan.generate",
        description = "Build and save a realistic plan for a day from the user's tasks and fixed events.",
        riskLevel = RiskLevel.L1,
        agentProposable = true,
        confirmation = Confirmation.NEVER,
        verification = "Re-read the saved plan and re-run PlanValidator.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        if (!args.onlyKeys("date", "notBefore")) return ArgsValidation.Rejected("unexpected keys")
        if (parseDate(args.string("date")) == null) return ArgsValidation.Rejected("date")
        val nb = args.optionalString("notBefore").getOrElse { return ArgsValidation.Rejected("notBefore") }
        if (nb != null && parseTime(nb) == null) return ArgsValidation.Rejected("notBefore")
        return ArgsValidation.Ok
    }

    private suspend fun request(args: JsonObject): PlanRequest {
        val date = parseDate(args.string("date"))!!
        val prefs = preferences.get()
        return PlanRequest(
            date = date,
            tasks = tasks.all().filter { it.date == date },
            fixedEvents = events.forDate(date),
            dayStart = prefs.dayStart,
            dayEnd = prefs.dayEnd,
            bufferMinutes = prefs.bufferMinutes,
            notBefore = parseTime(args.optionalString("notBefore").getOrNull()),
        )
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val request = request(args)
        val plan = planner.plan(request)
        val errors = PlanValidator.validate(plan, request)
        if (errors.isNotEmpty()) return ToolOutcome.Failure("PLAN_INVALID")
        plans.save(plan)
        return ToolOutcome.Success("blocks=${plan.blocks.size} unscheduled=${plan.unscheduledTaskIds.size}")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val request = request(args)
        val saved = plans.get(request.date) ?: return false
        return PlanValidator.validate(saved, request).isEmpty()
    }
}

/** tasks.complete — marks one task done. Args: {"taskId"} */
class CompleteTaskTool(private val tasks: TaskRepository, private val clock: Clock) : AgentTool {
    override val spec = ToolSpec(
        id = "tasks.complete",
        description = "Mark a task as done.",
        riskLevel = RiskLevel.L1,
        agentProposable = true,
        confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT,
        verification = "Re-read the task and check its status is DONE.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.onlyKeys("taskId") && !args.string("taskId").isNullOrBlank()) ArgsValidation.Ok else ArgsValidation.Rejected("taskId")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val task = tasks.get(args.string("taskId")!!) ?: return ToolOutcome.Failure("NOT_FOUND")
        tasks.upsert(listOf(task.copy(status = TaskStatus.DONE, updatedAt = clock.instant())))
        return ToolOutcome.Success("completed")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean =
        tasks.get(args.string("taskId")!!)?.status == TaskStatus.DONE
}

/** reminders.schedule — Phase 1 capability; registered now so the policy path is exercised. Args: {"taskId","at"} */
class ScheduleReminderTool(
    private val tasks: TaskRepository,
    private val scheduler: NotificationScheduler,
) : AgentTool {
    override val spec = ToolSpec(
        id = "reminders.schedule",
        description = "Schedule a local reminder notification for a task.",
        riskLevel = RiskLevel.L1,
        androidPermissions = setOf(POST_NOTIFICATIONS),
        featureFlag = Flag.REMINDERS.key,
        agentProposable = true,
        confirmation = Confirmation.NEVER,
        verification = "Scheduler reports the alarm as registered.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        if (!args.onlyKeys("taskId", "at") || args.string("taskId").isNullOrBlank()) return ArgsValidation.Rejected("taskId")
        return try {
            Instant.parse(args.string("at") ?: return ArgsValidation.Rejected("at"))
            ArgsValidation.Ok
        } catch (e: DateTimeParseException) {
            ArgsValidation.Rejected("at")
        }
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val task = tasks.get(args.string("taskId")!!) ?: return ToolOutcome.Failure("NOT_FOUND")
        val ok = scheduler.schedule(task.id, Instant.parse(args.string("at")!!), task.title)
        return if (ok) ToolOutcome.Success("scheduled") else ToolOutcome.Failure("SCHEDULER_UNAVAILABLE")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = true

    companion object {
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    }
}

/** Where an export goes. The UI registers a user-chosen destination and passes only its token to the tool. */
interface ExportTargets {
    /** Writes [bytes] to the destination registered under [token]; returns bytes written, or null if unknown. */
    suspend fun write(token: String, bytes: ByteArray): Long?
    fun exists(token: String): Boolean
}

/** data.export — writes a full JSON export to a user-chosen file. User-initiated only. Args: {"target"} */
class ExportDataTool(private val export: DataExportService, private val targets: ExportTargets) : AgentTool {
    override val spec = ToolSpec(
        id = "data.export",
        description = "Export all of the user's data to a file they choose.",
        riskLevel = RiskLevel.L3,
        agentProposable = false,
        confirmation = Confirmation.ALWAYS,
        verification = "Bytes written equal the size of the export.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        val t = args.string("target")
        return if (args.onlyKeys("target") && t != null && targets.exists(t)) ArgsValidation.Ok else ArgsValidation.Rejected("target")
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val bytes = export.exportJson().toByteArray(Charsets.UTF_8)
        val written = targets.write(args.string("target")!!, bytes) ?: return ToolOutcome.Failure("TARGET_UNAVAILABLE")
        return ToolOutcome.Success("exported", mapOf("expected" to bytes.size.toString(), "written" to written.toString()))
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean =
        outcome.data["expected"] == outcome.data["written"]
}

/** data.deleteAll — erases every store and verifies each is empty. User-initiated only. Args: {"scope":"everything"} */
class DeleteAllDataTool(private val deletion: DataDeletionService) : AgentTool {
    override val spec = ToolSpec(
        id = "data.deleteAll",
        description = "Permanently delete all of the user's data on this device.",
        riskLevel = RiskLevel.L4,
        agentProposable = false,
        confirmation = Confirmation.ALWAYS,
        verification = "Every store reports zero rows after deletion.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.onlyKeys("scope") && args.string("scope") == "everything") ArgsValidation.Ok else ArgsValidation.Rejected("scope")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val report = deletion.deleteEverything()
        return ToolOutcome.Success(
            "deleted",
            mapOf("verified" to report.verified.toString(), "remaining" to report.remaining.filterValues { it > 0 }.keys.joinToString(",")),
        )
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = outcome.data["verified"] == "true"
}
