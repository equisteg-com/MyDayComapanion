package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.ai.text.NumericDateOrder
import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.repository.PreferencesStore
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

private fun strictBoolean(s: String?): Boolean? = when (s) { "true" -> true; "false" -> false; else -> null }

private suspend fun TaskRepository.children(parentId: String) = all().filter { it.parentId == parentId }

/**
 * tasks.update — replaces the editable fields of one task. Every field is sent, so the
 * confirmation covers exactly what the owner saw. A parent's subtasks move with it to a new date.
 * Args: {"taskId","title","priority","durationMinutes","personal","date"?,"preferredStart"?,"deadline"?,"notes"?}
 * deadline and preferredStart are HH:mm on the task's date; a deadline needs a date.
 */
class UpdateTaskTool(private val tasks: TaskRepository, private val clock: Clock) : AgentTool {
    override val spec = ToolSpec(
        id = "tasks.update",
        description = "Change a task's title, day, time, duration, priority, notes or personal mark.",
        riskLevel = RiskLevel.L2,
        agentProposable = true,
        confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT,
        verification = "Re-read the task and compare every field.",
    )

    private data class Edit(
        val title: String,
        val priority: Priority,
        val duration: Int,
        val personal: Boolean,
        val date: LocalDate?,
        val preferredStart: LocalTime?,
        val deadline: LocalTime?,
        val notes: String?,
    )

    private fun parse(args: JsonObject): Edit? {
        if (!args.onlyKeys(*KEYS) || args.string("taskId").isNullOrBlank()) return null
        val title = args.string("title")?.takeIf { validTitle(it) }?.trim() ?: return null
        val priority = Priority.entries.firstOrNull { it.name == args.string("priority") } ?: return null
        val duration = args.int("durationMinutes")?.takeIf { validDuration(it) } ?: return null
        val personal = strictBoolean(args.string("personal")) ?: return null
        val dateRaw = args.optionalString("date").getOrElse { return null }
        val date = dateRaw?.let { parseDate(it) ?: return null }
        val startRaw = args.optionalString("preferredStart").getOrElse { return null }
        val start = startRaw?.let { parseTime(it) ?: return null }
        val deadlineRaw = args.optionalString("deadline").getOrElse { return null }
        val deadline = deadlineRaw?.let { parseTime(it) ?: return null }
        if (deadline != null && date == null) return null
        val notes = args.optionalString("notes").getOrElse { return null }?.trim()?.takeIf { it.isNotEmpty() }
        if (notes != null && notes.length > Task.MAX_NOTES_LENGTH) return null
        return Edit(title, priority, duration, personal, date, start, deadline, notes)
    }

    override fun validate(args: JsonObject): ArgsValidation = if (parse(args) != null) ArgsValidation.Ok else ArgsValidation.Rejected("task")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val e = parse(args) ?: return ToolOutcome.Failure("INVALID")
        val task = tasks.get(args.string("taskId")!!) ?: return ToolOutcome.Failure("NOT_FOUND")
        val now = clock.instant()
        val updated = task.copy(
            title = e.title,
            priority = e.priority,
            durationMinutes = e.duration,
            personal = e.personal,
            date = e.date,
            preferredStart = e.preferredStart,
            deadline = e.deadline?.let { e.date!!.atTime(it) },
            notes = e.notes,
            updatedAt = now,
        )
        val moved = if (task.parentId == null && task.date != e.date) {
            tasks.children(task.id).map { it.copy(date = e.date, updatedAt = now) }
        } else {
            emptyList()
        }
        tasks.upsert(listOf(updated) + moved)
        return ToolOutcome.Success("updated subtasksMoved=${moved.size}")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val e = parse(args) ?: return false
        val t = tasks.get(args.string("taskId")!!) ?: return false
        return t.title == e.title && t.priority == e.priority && t.durationMinutes == e.duration && t.personal == e.personal &&
            t.date == e.date && t.preferredStart == e.preferredStart && t.deadline?.toLocalTime() == e.deadline && t.notes == e.notes
    }

    /** Taking the personal mark off a task is itself a change to a personal item. */
    override suspend fun touchesPersonal(args: JsonObject): Boolean = tasks.get(args.string("taskId")!!)?.personal == true

    companion object {
        private val KEYS = arrayOf("taskId", "title", "priority", "durationMinutes", "personal", "date", "preferredStart", "deadline", "notes")

        fun args(task: Task) = JsonObject(
            buildMap {
                put("taskId", JsonPrimitive(task.id))
                put("title", JsonPrimitive(task.title))
                put("priority", JsonPrimitive(task.priority.name))
                put("durationMinutes", JsonPrimitive(task.durationMinutes))
                put("personal", JsonPrimitive(task.personal.toString()))
                task.date?.let { put("date", JsonPrimitive(it.toString())) }
                task.preferredStart?.let { put("preferredStart", JsonPrimitive(it.toString())) }
                task.deadline?.let { put("deadline", JsonPrimitive(it.toLocalTime().toString())) }
                task.notes?.takeIf { it.isNotBlank() }?.let { put("notes", JsonPrimitive(it)) }
            },
        )
    }
}

/** tasks.delete — deletes one task and its subtasks. Always needs a tap. Args: {"taskId"} */
class DeleteTaskTool(private val tasks: TaskRepository) : AgentTool {
    override val spec = ToolSpec(
        id = "tasks.delete",
        description = "Delete one task and its subtasks.",
        riskLevel = RiskLevel.L2,
        agentProposable = true,
        confirmation = Confirmation.ALWAYS,
        verification = "The task and its subtasks can no longer be read.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.onlyKeys("taskId") && !args.string("taskId").isNullOrBlank()) ArgsValidation.Ok else ArgsValidation.Rejected("taskId")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val task = tasks.get(args.string("taskId")!!) ?: return ToolOutcome.Failure("NOT_FOUND")
        val children = tasks.children(task.id)
        children.forEach { tasks.delete(it.id) }
        if (task.routineId != null) {
            // A routine's task for a day is kept as CANCELLED, so the routine does not add it again that day.
            tasks.upsert(listOf(task.copy(status = TaskStatus.CANCELLED, updatedAt = task.updatedAt)))
            return ToolOutcome.Success("skipped subtasks=${children.size}", mapOf("skipped" to "true"))
        }
        tasks.delete(task.id)
        return ToolOutcome.Success("deleted subtasks=${children.size}")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean {
        val id = args.string("taskId")!!
        val gone = if (outcome.data["skipped"] == "true") tasks.get(id)?.status == TaskStatus.CANCELLED else tasks.get(id) == null
        return gone && tasks.children(id).isEmpty()
    }

    override suspend fun touchesPersonal(args: JsonObject): Boolean = tasks.get(args.string("taskId")!!)?.personal == true
}

/**
 * tasks.addSubtask — adds a step under a task. Subtasks take the parent's day, priority and
 * personal mark; one level only. Once a task has subtasks the planner schedules the subtasks.
 * Args: {"parentId","title","durationMinutes"}
 */
class AddSubtaskTool(private val tasks: TaskRepository, private val ids: IdGenerator, private val clock: Clock) : AgentTool {
    override val spec = ToolSpec(
        id = "tasks.addSubtask",
        description = "Break a task into a smaller step.",
        riskLevel = RiskLevel.L2,
        agentProposable = true,
        confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT,
        verification = "Re-read the new subtask and check its parent.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        if (!args.onlyKeys("parentId", "title", "durationMinutes") || args.string("parentId").isNullOrBlank()) return ArgsValidation.Rejected("parentId")
        if (!validTitle(args.string("title"))) return ArgsValidation.Rejected("title")
        if (!validDuration(args.int("durationMinutes"))) return ArgsValidation.Rejected("duration")
        return ArgsValidation.Ok
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val parent = tasks.get(args.string("parentId")!!) ?: return ToolOutcome.Failure("NOT_FOUND")
        if (parent.parentId != null) return ToolOutcome.Failure("NESTED_SUBTASK")
        if (tasks.children(parent.id).size >= Task.MAX_SUBTASKS) return ToolOutcome.Failure("TOO_MANY_SUBTASKS")
        val now = clock.instant()
        val sub = Task(
            id = ids.newId(),
            title = args.string("title")!!.trim(),
            priority = parent.priority,
            durationMinutes = args.int("durationMinutes")!!,
            date = parent.date,
            parentId = parent.id,
            createdAt = now,
            personal = parent.personal,
        )
        tasks.upsert(listOf(sub))
        return ToolOutcome.Success("subtask added", mapOf("taskId" to sub.id))
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean =
        tasks.get(outcome.data["taskId"].orEmpty())?.parentId == args.string("parentId")

    override suspend fun touchesPersonal(args: JsonObject): Boolean = tasks.get(args.string("parentId")!!)?.personal == true
}

/** tasks.reopen — marks a done task as open again. Args: {"taskId"} */
class ReopenTaskTool(private val tasks: TaskRepository, private val clock: Clock) : AgentTool {
    override val spec = ToolSpec(
        id = "tasks.reopen",
        description = "Mark a done task as not done.",
        riskLevel = RiskLevel.L1,
        agentProposable = true,
        confirmation = Confirmation.WHEN_PROPOSED_BY_AGENT,
        verification = "Re-read the task and check its status is OPEN.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.onlyKeys("taskId") && !args.string("taskId").isNullOrBlank()) ArgsValidation.Ok else ArgsValidation.Rejected("taskId")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val task = tasks.get(args.string("taskId")!!) ?: return ToolOutcome.Failure("NOT_FOUND")
        tasks.upsert(listOf(task.copy(status = TaskStatus.OPEN, updatedAt = clock.instant())))
        return ToolOutcome.Success("reopened")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean =
        tasks.get(args.string("taskId")!!)?.status == TaskStatus.OPEN

    override suspend fun touchesPersonal(args: JsonObject): Boolean = tasks.get(args.string("taskId")!!)?.personal == true
}

/**
 * settings.update — day window, buffer, date order, reminder lead time, voice output and the
 * first-run flag. Owner only; the model cannot change how the planner reads the owner's day.
 * Args (all strings): {"dayStart","dayEnd","bufferMinutes","dateOrder","reminderLeadMinutes","speechOutputEnabled","onboardingDone"}
 */
class UpdateSettingsTool(private val preferences: PreferencesStore) : AgentTool {
    override val spec = ToolSpec(
        id = "settings.update",
        description = "Change the day window, buffer, date format, reminder lead time and voice output.",
        riskLevel = RiskLevel.L1,
        agentProposable = false,
        confirmation = Confirmation.NEVER,
        verification = "Stored preferences equal the requested preferences.",
    )

    override fun validate(args: JsonObject): ArgsValidation = if (parse(args) != null) ArgsValidation.Ok else ArgsValidation.Rejected("settings")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        preferences.set(parse(args) ?: return ToolOutcome.Failure("INVALID"))
        return ToolOutcome.Success("settings updated")
    }

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = preferences.get() == parse(args)

    companion object {
        private val KEYS = setOf("dayStart", "dayEnd", "bufferMinutes", "dateOrder", "reminderLeadMinutes", "speechOutputEnabled", "onboardingDone")

        fun args(p: UserPreferences) = JsonObject(
            mapOf(
                "dayStart" to JsonPrimitive(p.dayStart.toString()),
                "dayEnd" to JsonPrimitive(p.dayEnd.toString()),
                "bufferMinutes" to JsonPrimitive(p.bufferMinutes.toString()),
                "dateOrder" to JsonPrimitive(p.dateOrder.name),
                "reminderLeadMinutes" to JsonPrimitive(p.reminderLeadMinutes.toString()),
                "speechOutputEnabled" to JsonPrimitive(p.speechOutputEnabled.toString()),
                "onboardingDone" to JsonPrimitive(p.onboardingDone.toString()),
            ),
        )

        private fun parse(args: JsonObject): UserPreferences? {
            if (args.keys != KEYS) return null
            val p = UserPreferences(
                dayStart = parseTime(args.string("dayStart")) ?: return null,
                dayEnd = parseTime(args.string("dayEnd")) ?: return null,
                bufferMinutes = args.string("bufferMinutes")?.toIntOrNull() ?: return null,
                speechOutputEnabled = strictBoolean(args.string("speechOutputEnabled")) ?: return null,
                dateOrder = NumericDateOrder.entries.firstOrNull { it.name == args.string("dateOrder") } ?: return null,
                reminderLeadMinutes = args.string("reminderLeadMinutes")?.toIntOrNull() ?: return null,
                onboardingDone = strictBoolean(args.string("onboardingDone")) ?: return null,
            )
            return p.takeIf { it.isValid }
        }
    }
}
