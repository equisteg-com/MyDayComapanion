package com.equisteg.mydaycompanion.domain.ai

import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

/**
 * Deterministic, on-device "model" for task extraction. It is the guaranteed fallback when no
 * local model is available or the energy policy forbids one, and it emits the same
 * task-extraction/v1 JSON as any model so it travels through the same validation path.
 */
class RuleBasedTaskExtractor : AiModelProvider {
    override val id: String = "rules"
    override val version: String = "rule-extractor/1"

    override fun capabilities() = ProviderCapabilities(
        modelClass = ModelClass.DETERMINISTIC,
        runsOnDevice = true,
        supportsStructuredOutput = true,
        supportsStreaming = false,
        maxInputChars = 2_000,
        maxOutputChars = 16_000,
    )

    override fun estimateCost(request: AiRequest) = CostEstimate(cashCostInr = 0.0, relativeEnergy = 1)

    override suspend fun availability(): Availability = Availability.Available

    override suspend fun generate(request: AiRequest): AiResult {
        if (request.task != AiTask.TASK_EXTRACTION) return AiResult.Failure("unsupported task", id)
        val today = try {
            LocalDate.parse(request.context["today"] ?: return AiResult.Failure("missing today", id))
        } catch (e: DateTimeParseException) {
            return AiResult.Failure("invalid today", id)
        }
        val dto = extract(request.untrustedInput, today)
        return AiResult.Success(json.encodeToString(TaskExtractionDto.serializer(), dto), id, version)
    }

    fun extract(input: String, today: LocalDate): TaskExtractionDto {
        val lower = input.lowercase()
        val targetDate = resolveDate(lower, today)
        val tasks = mutableListOf<ExtractedTaskDto>()
        val events = mutableListOf<ExtractedEventDto>()
        val warnings = mutableListOf<String>()

        for (rawClause in splitClauses(input)) {
            var clause = rawClause.replace(datePhrase, " ").replace(spaces, " ").trim(' ', '.', '!', '?', '-', ':')
            if (clause.isBlank()) continue
            if (ContentSafety.looksLikeInstructionToAssistant(clause)) {
                warnings += "Ignored text that looked like an instruction to the assistant."
                continue
            }
            clause = clause.replace(leadIn, "").trim()
            if (clause.isBlank() || filler.matches(clause)) continue

            val explicitDuration = parseDuration(clause)
            clause = clause.replace(durationPhrase, " ").replace(spaces, " ").trim()

            val atTime = atTimePhrase.find(clause)
            if (atTime != null) {
                val start = toTime(atTime.groupValues[1], atTime.groupValues[2], atTime.groupValues[3])
                val title = clause.removeRange(atTime.range).replace(haveLeadIn, "").replace(spaces, " ").trim()
                if (start == null || title.isBlank()) {
                    warnings += "Could not understand a time in your message."
                    continue
                }
                events += ExtractedEventDto(
                    title = title.capitalized().take(Task.MAX_TITLE_LENGTH),
                    start = start.toString(),
                    durationMinutes = explicitDuration ?: eventDuration(title),
                )
                continue
            }
            if (haveLeadIn.containsMatchIn(clause) && eventWord.containsMatchIn(clause)) {
                warnings += "\"${clause.take(60)}\" has no time, so it was not added to the plan."
                continue
            }

            var deadline: LocalTime? = null
            byTimePhrase.find(clause)?.let { m ->
                deadline = when {
                    m.groupValues[4].isNotEmpty() -> LocalTime.NOON
                    m.groupValues[5].isNotEmpty() -> LocalTime.of(18, 0)
                    else -> toTime(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                }
                clause = clause.removeRange(m.range)
            }
            val priority = when {
                highMarker.containsMatchIn(clause) || deadline != null -> "HIGH"
                lowMarker.containsMatchIn(clause) -> "LOW"
                else -> "MEDIUM"
            }
            val title = clause.replace(priorityWords, " ").replace(spaces, " ").trim(' ', '.', ',', '-', ':')
            if (title.length < 2 || title.lowercase().split(spaces).all { it in stopWords }) continue
            tasks += ExtractedTaskDto(
                title = title.capitalized().take(Task.MAX_TITLE_LENGTH),
                priority = priority,
                durationMinutes = explicitDuration ?: estimateDuration(title),
                deadline = deadline?.toString(),
            )
        }
        return TaskExtractionDto(
            version = TaskExtractionValidator.SCHEMA_VERSION,
            targetDate = targetDate.toString(),
            tasks = tasks.take(TaskExtractionValidator.MAX_TASKS),
            fixedEvents = events.take(TaskExtractionValidator.MAX_EVENTS),
            warnings = warnings.distinct(),
        )
    }

    private fun resolveDate(lower: String, today: LocalDate): LocalDate {
        if ("day after tomorrow" in lower) return today.plusDays(2)
        if (Regex("\\btomorrow\\b").containsMatchIn(lower)) return today.plusDays(1)
        if (Regex("\\b(today|tonight)\\b").containsMatchIn(lower)) return today
        val day = weekday.find(lower) ?: return today
        val dow = DayOfWeek.valueOf(day.groupValues[2].uppercase())
        return today.with(TemporalAdjusters.next(dow))
    }

    private fun splitClauses(input: String): List<String> = input.split(clauseSplit).map { it.trim() }.filter { it.isNotEmpty() }

    private fun parseDuration(clause: String): Int? {
        val m = durationPhrase.find(clause) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val minutes = if (m.groupValues[2].startsWith("h")) n * 60 else n
        return minutes.toInt().coerceIn(Task.MIN_DURATION_MINUTES, Task.MAX_DURATION_MINUTES)
    }

    private fun toTime(hourText: String, minuteText: String, meridiem: String): LocalTime? {
        var hour = hourText.toIntOrNull() ?: return null
        val minute = if (minuteText.isEmpty()) 0 else minuteText.toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        val m = meridiem.replace(".", "").lowercase()
        when {
            m == "pm" && hour in 1..11 -> hour += 12
            m == "am" && hour == 12 -> hour = 0
            m.isEmpty() && hour in 1..6 -> hour += 12 // "at 3" in a day plan almost always means 3pm
        }
        if (hour !in 0..23) return null
        return LocalTime.of(hour, minute)
    }

    private fun estimateDuration(title: String): Int =
        durationTable.firstOrNull { it.first.containsMatchIn(title) }?.second ?: Task.DEFAULT_DURATION_MINUTES

    private fun eventDuration(title: String): Int =
        if (Regex("\\b(call|stand-?up|check-?in)\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)) 15 else 60

    private fun String.capitalized() = replaceFirstChar { it.uppercase() }

    private companion object {
        val json = Json { encodeDefaults = true }
        val spaces = Regex("\\s+")
        val stopWords = setOf("at", "and", "the", "a", "an", "to", "on", "in", "of", "for", "by", "then", "also", "it", "do", "")
        val clauseSplit = Regex("[,;\\n]|\\.(?=\\s|$)|\\b(?:and then|then|and|also|plus)\\b", RegexOption.IGNORE_CASE)
        val weekday = Regex("\\b(on |this |next )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
        val datePhrase = Regex(
            "\\b(the )?day after tomorrow\\b|\\btomorrow\\b|\\btoday\\b|\\btonight\\b|" +
                "\\b(on |this |next )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            RegexOption.IGNORE_CASE,
        )
        val leadIn = Regex(
            "^(?:(?:so|ok|okay|please|and|also|then)\\s+)*(?:(?:i|we)(?=\\s|')\\s*)?" +
                "(?:need to|have to|must|should|want to|gotta|got to|will|plan to|would like to|'d like to|'ll)?\\s*" +
                "(?:remember to|remind me to)?\\s*",
            RegexOption.IGNORE_CASE,
        )
        val haveLeadIn = Regex("^(?:(?:(?:i|we)\\s*)?(?:have|'ve got|got)|there(?: is|'s))\\s+(?:a |an |the |my )?", RegexOption.IGNORE_CASE)
        val eventWord = Regex("\\b(meeting|appointment|interview|class|lecture|flight|standup|stand-up)\\b", RegexOption.IGNORE_CASE)
        val filler = Regex("^(that's it|thats it|thanks|thank you|please|that is all|nothing else)$", RegexOption.IGNORE_CASE)
        val atTimePhrase = Regex("\\b(?:at|@)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?(?=\\W|$)", RegexOption.IGNORE_CASE)
        val byTimePhrase = Regex(
            "\\bby\\s+(?:(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?|(noon|midday)|(end of (?:the )?day|eod|evening))(?=\\W|$)",
            RegexOption.IGNORE_CASE,
        )
        val durationPhrase = Regex(
            "\\(?\\b(?:for\\s+)?(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m)\\b\\)?",
            RegexOption.IGNORE_CASE,
        )
        val highMarker = Regex("\\b(urgent|urgently|asap|important|critical|high priority|top priority|deadline|due)\\b", RegexOption.IGNORE_CASE)
        val lowMarker = Regex("\\b(maybe|if (?:i have )?time|optional|low priority|someday|if possible)\\b", RegexOption.IGNORE_CASE)
        val priorityWords = Regex(
            "\\b(urgently|urgent|asap|important|critical|high priority|top priority|low priority|maybe|if (?:i have )?time|optional|someday|if possible)\\b",
            RegexOption.IGNORE_CASE,
        )
        val durationTable: List<Pair<Regex, Int>> = listOf(
            "\\b(call|phone|ring)\\b" to 15,
            "\\b(e-?mail|text|message|reply|respond)\\b" to 15,
            "\\b(pay|bill|book|renew|order)\\b" to 15,
            "\\b(proposal|report|presentation|deck|thesis|assignment|homework)\\b" to 120,
            "\\b(write|draft|prepare|study|review|plan)\\b" to 60,
            "\\b(work ?out|gym|exercise|run|jog|yoga|swim|walk)\\b" to 60,
            "\\b(groceries|grocery|shopping|shop|buy)\\b" to 45,
            "\\b(clean|laundry|cook|dishes|tidy)\\b" to 45,
            "\\b(read)\\b" to 30,
        ).map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }
    }
}
