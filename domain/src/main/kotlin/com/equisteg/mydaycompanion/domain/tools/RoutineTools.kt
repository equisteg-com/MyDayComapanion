package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.repository.RoutineRepository
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.routines.RoutineExpander
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime

private fun strictBool(s: String?): Boolean? = when (s) { "true" -> true; "false" -> false; else -> null }

/** The fields every routine call carries; [dayOfWeek] is required for WEEKLY and refused otherwise. */
private data class RoutineFields(
    val title: String,
    val frequency: RoutineFrequency,
    val duration: Int,
    val preferredStart: LocalTime?,
    val dayOfWeek: DayOfWeek?,
    val remind: Boolean,
)

private fun parseRoutineFields(args: JsonObject): RoutineFields? {
    val title = args.string("title")?.takeIf { validTitle(it) }?.trim() ?: return null
    val frequency = RoutineFrequency.entries.firstOrNull { it.name == args.string("frequency") } ?: return null
    val duration = args.int("durationMinutes")?.takeIf { validDuration(it) } ?: return null
    val startRaw = args.optionalString("preferredStart").getOrElse { return null }
    val start = startRaw?.let { parseTime(it) ?: return null }
    val dayRaw = args.optionalString("dayOfWeek").getOrElse { return null }
    val day = dayRaw?.let { d -> DayOfWeek.entries.firstOrNull { it.name == d } ?: return null }
    if ((frequency == RoutineFrequency.WEEKLY) != (day != null)) return null
    val remind = if (args.containsKey("remind")) strictBool(args.string("remind")) ?: return null else true
    return RoutineFields(title, frequency, duration, start, day, remind)
}

private fun MutableMap<String, kotlinx.serialization.json.JsonElement>.putRoutine(r: Routine) {
    put("title", JsonPrimitive(r.title))
    put("frequency", JsonPrimitive(r.frequency.name))
    put("durationMinutes", JsonPrimitive(r.durationMinutes))
    put("remind", JsonPrimitive(r.remind.toString()))
    r.preferredStart?.let { put("preferredStart", JsonPrimitive(it.toString())) }
    r.dayOfWeek?.let { put("dayOfWeek", JsonPrimitive(it.name)) }
}

/**
 * routines.create — saves a repeating item such as "gym every weekday at 6am".
 * Args: {"title","frequency":"DAILY|WEEKDAYS|WEEKLY","durationMinutes","preferredStart"?,"dayOfWeek"? (WEEKLY only),"remind"?}
 */
class CreateRoutineTool(
    private val routines: RoutineRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) : AgentTool {
    override val spec = ToolSpec(
        id = "routines.create",
        description = "Save a routine that adds a task on every day it is due.",
        riskLevel = RiskLevel.L2,
        featureFlag = Flag.ROUTINES.key,
        agentProposable = true,
        confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT,
        verification = "Re-read the routine by id.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.onlyKeys(*KEYS) && parseRoutineFields(args) != null) ArgsValidation.Ok else ArgsValidation.Rejected("routine")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val f = parseRoutineFields(args) ?: return ToolOutcome.Failure("INVALID")
        if (routines.count() >= Routine.MAX_ROUTINES) return ToolOutcome.Failure("TOO_MANY_ROUTINES")
        val existing = routines.all().firstOrNull { it.title.equals(f.title, ignoreCase = true) && it.frequency == f.frequency && it.dayOfWeek == f.dayOfWeek }
        if (existing != null) return ToolOutcome.Success("routine exists", mapOf("routineId" to existing.id))
        val routine = Routine(ids.newId(), f.title, f.frequency, f.preferredStart, f.duration, true, clock.instant(), f.dayOfWeek, f.remind)
        routines.upsert(routine)
        return ToolOutcome.Success("routine created", mapOf("routineId" to routine.id))
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean =
        routines.get(outcome.data["routineId"].orEmpty()) != null

    companion object {
        private val KEYS = arrayOf("title", "frequency", "durationMinutes", "preferredStart", "dayOfWeek", "remind")

        fun args(r: Routine) = JsonObject(buildMap { putRoutine(r) })
    }
}

/** routines.update — edits a routine or switches it on or off. Args: routine fields + {"routineId","enabled"} */
class UpdateRoutineTool(private val routines: RoutineRepository) : AgentTool {
    override val spec = ToolSpec(
        id = "routines.update",
        description = "Change a routine, or switch it on or off.",
        riskLevel = RiskLevel.L2,
        featureFlag = Flag.ROUTINES.key,
        agentProposable = true,
        confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT,
        verification = "Re-read the routine and compare every field.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        if (!args.onlyKeys(*KEYS) || args.string("routineId").isNullOrBlank()) return ArgsValidation.Rejected("routineId")
        if (strictBool(args.string("enabled")) == null) return ArgsValidation.Rejected("enabled")
        return if (parseRoutineFields(args) != null) ArgsValidation.Ok else ArgsValidation.Rejected("routine")
    }

    private fun merged(old: Routine, args: JsonObject): Routine? {
        val f = parseRoutineFields(args) ?: return null
        return old.copy(
            title = f.title, frequency = f.frequency, durationMinutes = f.duration, preferredStart = f.preferredStart,
            dayOfWeek = f.dayOfWeek, remind = f.remind, enabled = strictBool(args.string("enabled")) ?: return null,
        )
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val old = routines.get(args.string("routineId")!!) ?: return ToolOutcome.Failure("NOT_FOUND")
        routines.upsert(merged(old, args) ?: return ToolOutcome.Failure("INVALID"))
        return ToolOutcome.Success("routine updated")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val now = routines.get(args.string("routineId")!!) ?: return false
        return now == merged(now, args)
    }

    companion object {
        private val KEYS = arrayOf("routineId", "enabled", "title", "frequency", "durationMinutes", "preferredStart", "dayOfWeek", "remind")

        fun args(r: Routine) = JsonObject(
            buildMap {
                put("routineId", JsonPrimitive(r.id))
                put("enabled", JsonPrimitive(r.enabled.toString()))
                putRoutine(r)
            },
        )
    }
}

/** routines.delete — removes a routine. Tasks it already added stay. Always needs a tap. Args: {"routineId"} */
class DeleteRoutineTool(private val routines: RoutineRepository) : AgentTool {
    override val spec = ToolSpec(
        id = "routines.delete",
        description = "Delete a routine. Tasks it already added stay.",
        riskLevel = RiskLevel.L2,
        featureFlag = Flag.ROUTINES.key,
        agentProposable = true,
        confirmation = Confirmation.ALWAYS,
        verification = "The routine can no longer be read.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.onlyKeys("routineId") && !args.string("routineId").isNullOrBlank()) ArgsValidation.Ok else ArgsValidation.Rejected("routineId")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        routines.get(args.string("routineId")!!) ?: return ToolOutcome.Failure("NOT_FOUND")
        routines.delete(args.string("routineId")!!)
        return ToolOutcome.Success("routine deleted")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = routines.get(args.string("routineId")!!) == null
}

/**
 * routines.materialize — adds today's (or another day's) task for every routine due that day, once.
 * Run by the app itself (start, daily alarm, after a restart), never proposed by the assistant. Args: {"date"}
 */
class MaterializeRoutinesTool(
    private val routines: RoutineRepository,
    private val tasks: TaskRepository,
    private val clock: Clock,
) : AgentTool {
    override val spec = ToolSpec(
        id = "routines.materialize",
        description = "Add the tasks that routines are due to create on a day.",
        riskLevel = RiskLevel.L1,
        featureFlag = Flag.ROUTINES.key,
        agentProposable = false,
        confirmation = Confirmation.NEVER,
        verification = "Every due routine is covered: by its own task that day, a task with the same title that day, or its task moved to another day.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.onlyKeys("date") && parseDate(args.string("date")) != null) ArgsValidation.Ok else ArgsValidation.Rejected("date")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val date = parseDate(args.string("date"))!!
        val created = RoutineExpander.tasksFor(date, routines.all(), tasks.all(), clock.instant())
        if (created.isNotEmpty()) tasks.upsert(created)
        return ToolOutcome.Success("routine tasks=${created.size}")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val date = parseDate(args.string("date"))!!
        val onDay = tasks.all().filter { it.date == date }.groupingBy { it.routineId }.eachCount()
        // Same rule as RoutineExpander: covered by its own task that day, by any task with its title that day,
        // or by its task for that day having been moved elsewhere by the owner.
        val all = tasks.all()
        val titles = all.filter { it.date == date }.map { it.title.lowercase() }.toSet()
        val ids = all.map { it.id }.toSet()
        return routines.all().filter { it.isDueOn(date) }.all {
            (onDay[it.id] ?: 0) >= 1 || it.title.lowercase() in titles || RoutineExpander.taskId(it.id, date) in ids
        }
    }
}
