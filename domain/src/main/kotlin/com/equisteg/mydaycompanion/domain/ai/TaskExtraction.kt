package com.equisteg.mydaycompanion.domain.ai

import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** Wire schema task-extraction/v1. Every model (including the rule-based one) must emit this. */
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
    val deadline: String? = null,
)

@Serializable
data class ExtractedEventDto(val title: String, val start: String, val durationMinutes: Int = 60)

/** Validated, typed extraction. Only this type may flow into the planner and tools. */
data class TaskExtraction(
    val targetDate: LocalDate,
    val tasks: List<ExtractedTask>,
    val fixedEvents: List<ExtractedEvent>,
    val warnings: List<String>,
)

data class ExtractedTask(val title: String, val priority: Priority, val durationMinutes: Int, val deadline: LocalTime?)

data class ExtractedEvent(val title: String, val start: LocalTime, val durationMinutes: Int)

sealed interface ValidationOutcome<out T> {
    data class Valid<T>(val value: T) : ValidationOutcome<T>
    data class Invalid(val errors: List<String>) : ValidationOutcome<Nothing>
}

/**
 * Schema validation (parse into the DTO) followed by domain validation (dates, bounds, sizes,
 * safety). Anything that fails is rejected as a whole; partially valid model output is not used.
 */
object TaskExtractionValidator {
    const val SCHEMA_VERSION = 1
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
        } else if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            errors += "targetDate out of range"
        }
        if (dto.tasks.size > MAX_TASKS) errors += "too many tasks"
        if (dto.fixedEvents.size > MAX_EVENTS) errors += "too many events"

        val tasks = mutableListOf<ExtractedTask>()
        dto.tasks.forEachIndexed { i, t ->
            val title = t.title.trim()
            val priority = Priority.entries.firstOrNull { it.name == t.priority }
            val deadline = t.deadline?.let { parseTime(it) }
            when {
                title.isEmpty() || title.length > Task.MAX_TITLE_LENGTH -> errors += "task[$i] title length"
                priority == null -> errors += "task[$i] priority"
                t.durationMinutes !in Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES -> errors += "task[$i] duration"
                t.deadline != null && deadline == null -> errors += "task[$i] deadline"
                ContentSafety.looksLikeInstructionToAssistant(title) -> warnings += "Ignored text that looked like an instruction to the assistant."
                else -> tasks += ExtractedTask(title, priority, t.durationMinutes, deadline)
            }
        }
        val events = mutableListOf<ExtractedEvent>()
        dto.fixedEvents.forEachIndexed { i, e ->
            val start = parseTime(e.start)
            val title = e.title.trim()
            when {
                title.isEmpty() || title.length > Task.MAX_TITLE_LENGTH -> errors += "event[$i] title length"
                start == null -> errors += "event[$i] start"
                e.durationMinutes !in Task.MIN_DURATION_MINUTES..Task.MAX_DURATION_MINUTES -> errors += "event[$i] duration"
                start.plusMinutes(e.durationMinutes.toLong()).isBefore(start) -> errors += "event[$i] crosses midnight"
                ContentSafety.looksLikeInstructionToAssistant(title) -> warnings += "Ignored text that looked like an instruction to the assistant."
                else -> events += ExtractedEvent(title, start, e.durationMinutes)
            }
        }
        if (errors.isNotEmpty()) return ValidationOutcome.Invalid(errors)
        return ValidationOutcome.Valid(TaskExtraction(date!!, tasks, events, warnings.distinct()))
    }

    private fun parseDate(s: String): LocalDate? = try { LocalDate.parse(s) } catch (e: DateTimeParseException) { null }

    private fun parseTime(s: String): LocalTime? = try { LocalTime.parse(s) } catch (e: DateTimeParseException) { null }
}
