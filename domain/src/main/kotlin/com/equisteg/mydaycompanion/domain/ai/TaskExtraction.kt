package com.equisteg.mydaycompanion.domain.ai

import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Wire schema task-extraction/v2. Every model (including the rule-based one) must emit this.
 * v2 adds per-item dates (one message can cover several days), a preferred start for soft times
 * ("in the morning"), repeats and reminder times.
 */
@Serializable
data class TaskExtractionDto(
    val version: Int,
    val targetDate: String,
    val tasks: List<ExtractedTaskDto> = emptyList(),
    val fixedEvents: List<ExtractedEventDto> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class ExtractedTaskDto(
    val title: String,
    val priority: String = "MEDIUM",
    val durationMinutes: Int = Task.DEFAULT_DURATION_MINUTES,
    /** Null means the extraction's targetDate. */
    val date: String? = null,
    val deadline: String? = null,
    val preferredStart: String? = null,
    val repeat: String? = null,
    val remindAt: String? = null,
)

@Serializable
data class ExtractedEventDto(val title: String, val date: String? = null, val start: String, val durationMinutes: Int = 60)

/** Validated, typed extraction. Only this type may flow into the planner and tools. */
data class TaskExtraction(
    val targetDate: LocalDate,
    val tasks: List<ExtractedTask>,
    val fixedEvents: List<ExtractedEvent>,
    val warnings: List<String>,
) {
    /** Every day this extraction touches, in date order. */
    val dates: List<LocalDate> get() = (tasks.map { it.date } + fixedEvents.map { it.date }).distinct().sorted()
}

data class ExtractedTask(
    val title: String,
    val priority: Priority,
    val durationMinutes: Int,
    val deadline: LocalTime?,
    val date: LocalDate,
    val preferredStart: LocalTime? = null,
    val repeat: RoutineFrequency? = null,
    val remindAt: LocalTime? = null,
)

data class ExtractedEvent(val title: String, val start: LocalTime, val durationMinutes: Int, val date: LocalDate)

sealed interface ValidationOutcome<out T> {
    data class Valid<T>(val value: T) : ValidationOutcome<T>
    data class Invalid(val errors: List<String>) : ValidationOutcome<Nothing>
}

/**
 * Schema validation (parse into the DTO) followed by domain validation (dates, bounds, sizes,
 * safety). Anything that fails is rejected as a whole; partially valid model output is not used.
 */
object TaskExtractionValidator {
    const val SCHEMA_VERSION = 2
    const val MAX_TASKS = 25
    const val MAX_EVENTS = 15
    const val MAX_RAW_CHARS = 16_000
    private const val MAX_DAYS_AHEAD = 366L

    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    fun validate(raw: String, today: LocalDate): ValidationOutcome<TaskExtraction> {
        if (raw.length > MAX_RAW_CHARS) return ValidationOutcome.Invalid(listOf("output too large"))
        val dto = try {
            json.decodeFromString(TaskExtractionDto.serializer(), raw.trim())
        } catch (e: SerializationException) {
            return ValidationOutcome.Invalid(listOf("schema: ${e.message?.take(200)}"))
        } catch (e: IllegalArgumentException) {
            return ValidationOutcome.Invalid(listOf("schema: ${e.message?.take(200)}"))
        }

        val errors = mutableListOf<String>()
        val warnings = dto.warnings.take(10).map { it.take(200) }.toMutableList()
        if (dto.version != SCHEMA_VERSION) errors += "unsupported schema version ${dto.version}"
        val date = parseDate(dto.targetDate)
        if (date == null) {
            errors += "targetDate is not a date"
        } else if (!inRange(date, today)) {
            errors += "targetDate out of range"
        }
        if (dto.tasks.size > MAX_TASKS) errors += "too many tasks"
        if (dto.fixedEvents.size > MAX_EVENTS) errors += "too many events"

        val tasks = mutableListOf<ExtractedTask>()
        dto.tasks.forEachIndexed { i, t ->
            val title = t.title.trim()
            val priority = Priority.entries.firstOrNull { it.name == t.priority }
            val deadline = t.deadline?.let { parseTime(it) }
            val preferred = t.preferredStart?.let { parseTime(it) }
            val remindAt = t.remindAt?.let { parseTime(it) }
            val repeat = t.repeat?.let { r -> RoutineFrequency.entries.firstOrNull { it.name == r } }
            val itemDate = t.date?.let { parseDate(it) }
            when {
                title.isEmpty() || title.length > Task.MAX_TITLE_LENGTH -> errors += "task[$i] title length"
                priority == null -> errors += "task[$i] priority"
                t.durationMinutes !in Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES -> errors += "task[$i] duration"
                t.deadline != null && deadline == null -> errors += "task[$i] deadline"
                t.preferredStart != null && preferred == null -> errors += "task[$i] preferredStart"
                t.remindAt != null && remindAt == null -> errors += "task[$i] remindAt"
                t.repeat != null && repeat == null -> errors += "task[$i] repeat"
                t.date != null && !inRange(itemDate, today) -> errors += "task[$i] date"
                ContentSafety.looksLikeInstructionToAssistant(title) -> warnings += "Ignored text that looked like an instruction to the assistant."
                date != null -> tasks += ExtractedTask(title, priority, t.durationMinutes, deadline, itemDate ?: date, preferred, repeat, remindAt)
            }
        }
        val events = mutableListOf<ExtractedEvent>()
        dto.fixedEvents.forEachIndexed { i, e ->
            val start = parseTime(e.start)
            val title = e.title.trim()
            val itemDate = e.date?.let { parseDate(it) }
            when {
                title.isEmpty() || title.length > Task.MAX_TITLE_LENGTH -> errors += "event[$i] title length"
                start == null -> errors += "event[$i] start"
                e.durationMinutes !in Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES -> errors += "event[$i] duration"
                start.plusMinutes(e.durationMinutes.toLong()).isBefore(start) -> errors += "event[$i] crosses midnight"
                e.date != null && !inRange(itemDate, today) -> errors += "event[$i] date"
                ContentSafety.looksLikeInstructionToAssistant(title) -> warnings += "Ignored text that looked like an instruction to the assistant."
                date != null -> events += ExtractedEvent(title, start, e.durationMinutes, itemDate ?: date)
            }
        }
        if (errors.isNotEmpty()) return ValidationOutcome.Invalid(errors)
        return ValidationOutcome.Valid(TaskExtraction(date!!, tasks, events, warnings.distinct()))
    }

    private fun inRange(d: LocalDate?, today: LocalDate) = d != null && !d.isBefore(today) && !d.isAfter(today.plusDays(MAX_DAYS_AHEAD))

    private fun parseDate(s: String): LocalDate? = try { LocalDate.parse(s) } catch (e: DateTimeParseException) { null }

    private fun parseTime(s: String): LocalTime? = try { LocalTime.parse(s) } catch (e: DateTimeParseException) { null }
}
