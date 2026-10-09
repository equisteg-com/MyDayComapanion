package com.equisteg.mydaycompanion.domain.ai

import com.equisteg.mydaycompanion.domain.ai.text.ClauseSplitter
import com.equisteg.mydaycompanion.domain.ai.text.DateParser
import com.equisteg.mydaycompanion.domain.ai.text.DurationParser
import com.equisteg.mydaycompanion.domain.ai.text.TextSpans
import com.equisteg.mydaycompanion.domain.ai.text.TimeMatch
import com.equisteg.mydaycompanion.domain.ai.text.TimeParser
import com.equisteg.mydaycompanion.domain.ai.text.TimeRole
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
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
 * task-extraction JSON as any model so it travels through the same validation path.
 *
 * Version 2 reads dates and times wherever they appear in a sentence and in the many ways
 * people write them (see [TimeParser], [DateParser], [DurationParser]), splits a message into
 * items without breaking names or lists ([ClauseSplitter]), and lets one message cover several
 * days.
 */
class RuleBasedTaskExtractor(private val dates: DateParser = DateParser()) : AiModelProvider {
    override val id: String = "rules"
    override val version: String = "rule-extractor/2"

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
        val now = request.context["now"]?.let { try { LocalTime.parse(it) } catch (e: DateTimeParseException) { null } }
        val dto = extract(request.untrustedInput, today, now)
        return AiResult.Success(json.encodeToString(TaskExtractionDto.serializer(), dto), id, version)
    }

    /** [now] enables relative times ("in 2 hours") and past-time warnings for today; null skips both. */
    fun extract(input: String, today: LocalDate, now: LocalTime? = null): TaskExtractionDto {
        val clauses = ClauseSplitter.split(TextSpans.expandShorthand(input))
        val clauseDates = clauses.map { c -> dates.findAll(c, today) }
        val firstDate = clauses.indices.firstNotNullOfOrNull { i ->
            val repeat = repeatOf(clauses[i])
            clauseDates[i].firstOrNull { d -> repeat == null || !TextSpans.overlaps(d.range, repeat.range) }?.date
                ?: repeat?.day?.let { today.with(TemporalAdjusters.nextOrSame(it)) }
        }
        val targetDate = firstDate ?: today

        val tasks = mutableListOf<ExtractedTaskDto>()
        val events = mutableListOf<ExtractedEventDto>()
        val warnings = mutableListOf<String>()
        var current: LocalDate? = null

        clauses.forEachIndexed { index, raw ->
            if (ContentSafety.looksLikeInstructionToAssistant(raw)) {
                warnings += "Ignored text that looked like an instruction to the assistant."
                return@forEachIndexed
            }
            // "every sunday" is a repeat, not a date; the weekday only picks the first occurrence.
            val repeat = repeatOf(raw)
            val dateMatches = clauseDates[index].filter { d -> repeat == null || !TextSpans.overlaps(d.range, repeat.range) }
            var clauseDate = dateMatches.firstOrNull()?.date
            if (clauseDate != null) current = clauseDate
            if (clauseDate == null && repeat?.day != null) clauseDate = today.with(TemporalAdjusters.nextOrSame(repeat.day))
            var text = TextSpans.cut(raw, dateMatches.map { it.range } + listOfNotNull(repeat?.range))
            if (clauseDate == null && todayPhrase.containsMatchIn(text)) clauseDate = today
            val date = clauseDate ?: current ?: targetDate
            val reminder = remindPhrase.containsMatchIn(text)
            text = text.replace(remindPhrase, " ")

            val durations = DurationParser.findAll(relativeGuard(text, date == today && now != null))
            val explicitDuration = durations.firstOrNull()?.minutes
            text = TextSpans.cut(text, durations.map { it.range })

            val times = TimeParser.findAll(text, now.takeIf { date == today })
            val at = times.firstOrNull { it.role == TimeRole.AT }
            val deadline = times.firstOrNull { it.role == TimeRole.DEADLINE }
            val after = times.firstOrNull { it.role == TimeRole.AFTER }
            val soft = times.firstOrNull { it.role == TimeRole.SOFT }
            val softUsed = at == null && after == null && soft != null
            text = TextSpans.cut(text, times.filter { it.role != TimeRole.SOFT || softUsed || it.alwaysCut }.map { it.range })

            val priority = when {
                highMarker.containsMatchIn(text) || deadline != null -> "HIGH"
                lowMarker.containsMatchIn(text) -> "LOW"
                else -> "MEDIUM"
            }
            val title = cleanTitle(text)
            val itemDate = date.takeIf { it != targetDate }?.toString()
            val repeatName = repeat?.frequency?.name

            // A timed reminder or a repeating item is a task placed at that time; anything else timed is an event.
            if (at != null && !reminder && repeatName == null) {
                val eventTitle = title.replace(haveLeadIn, "").let(::trimDangling)
                if (!meaningful(eventTitle)) {
                    warnings += "Could not tell what happens at ${at.start}. Add a few words, like \"call Raj at ${at.start}\"."
                    return@forEachIndexed
                }
                val (start, duration) = fitInDay(at.start, explicitDuration ?: at.durationMinutes() ?: eventDuration(eventTitle))
                pastWarning(eventTitle, start, date, today, now)?.let { warnings += it }
                assumedWarning(eventTitle, at)?.let { warnings += it }
                events += ExtractedEventDto(
                    title = eventTitle.capitalized().take(Task.MAX_TITLE_LENGTH),
                    date = itemDate,
                    start = start.toString(),
                    durationMinutes = duration,
                )
                return@forEachIndexed
            }
            if (haveLeadIn.containsMatchIn(title) && eventWord.containsMatchIn(title)) {
                val what = eventWord.find(title)!!.value.lowercase()
                warnings += "When is the $what? Add a time, like \"$what at 3pm\", and I'll plan around it."
                return@forEachIndexed
            }
            if (!meaningful(title)) return@forEachIndexed
            val preferred = (at ?: after ?: soft?.takeIf { softUsed })?.start
            tasks += ExtractedTaskDto(
                title = title.capitalized().take(Task.MAX_TITLE_LENGTH),
                priority = priority,
                durationMinutes = explicitDuration ?: estimateDuration(title),
                date = itemDate,
                deadline = deadline?.start?.toString(),
                preferredStart = preferred?.toString(),
                repeat = repeatName,
                remindAt = at?.start?.takeIf { reminder }?.toString(),
            )
        }
        return TaskExtractionDto(
            version = TaskExtractionValidator.SCHEMA_VERSION,
            targetDate = targetDate.toString(),
            tasks = tasks.take(TaskExtractionValidator.MAX_TASKS),
            fixedEvents = events.take(TaskExtractionValidator.MAX_EVENTS),
            warnings = warnings.distinct().take(10),
        )
    }

    /** "in 2 hours" is a start time today, not a duration; hide it from the duration reader then. */
    private fun relativeGuard(text: String, relativeAllowed: Boolean): String =
        if (!relativeAllowed) text else text.replace(relativePhrase) { " ".repeat(it.value.length) }

    private fun TimeMatch.durationMinutes(): Int? = end?.let { (it.toSecondOfDay() - start.toSecondOfDay()) / 60 }
        ?.coerceIn(Task.MIN_DURATION_MINUTES, Task.MAX_DURATION_MINUTES)

    /** Events may not cross midnight; shorten (or, in the last minutes, move) them to end by 23:59. */
    private fun fitInDay(start: LocalTime, minutes: Int): Pair<LocalTime, Int> {
        val startMin = start.toSecondOfDay() / 60
        val room = 23 * 60 + 59 - startMin
        return if (room >= Task.MIN_DURATION_MINUTES) start to minutes.coerceAtMost(room)
        else LocalTime.of(23, 54) to Task.MIN_DURATION_MINUTES
    }

    private fun pastWarning(title: String, start: LocalTime, date: LocalDate, today: LocalDate, now: LocalTime?): String? =
        if (date == today && now != null && start < now) "\"${title.capitalized()}\" at $start has already passed today." else null

    private fun assumedWarning(title: String, t: TimeMatch): String? =
        if (t.assumedHalf && t.end == null && t.start.minute == 0 && t.start.hour in 7..11) "I read the time for \"${title.capitalized()}\" as ${t.start} in the morning." else null

    private data class Repeat(val range: IntRange, val frequency: RoutineFrequency, val day: DayOfWeek?)

    private fun repeatOf(text: String): Repeat? {
        weekdaysRepeat.find(text)?.let { return Repeat(it.range, RoutineFrequency.WEEKDAYS, null) }
        weeklyRepeat.find(text)?.let { m ->
            val name = m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[3]?.value
            val day = name?.let { n -> DayOfWeek.entries.firstOrNull { it.name.startsWith(n.uppercase().take(3)) } }
            return Repeat(m.range, RoutineFrequency.WEEKLY, day)
        }
        dailyRepeat.find(text)?.let { return Repeat(it.range, RoutineFrequency.DAILY, null) }
        return null
    }

    private fun cleanTitle(text: String): String {
        var t = text.replace(priorityWords, " ").replace(spaces, " ").trim(*trimChars)
        t = t.replace(leadIn, "").trim(*trimChars)
        return trimDangling(t)
    }

    private fun trimDangling(text: String): String {
        var t = text
        while (true) {
            val next = t.replace(leadingFiller, "").replace(trailingFiller, "").trim(*trimChars)
            if (next == t) return t
            t = next
        }
    }

    private fun meaningful(title: String): Boolean =
        title.length >= 2 && !title.lowercase().split(spaces).all { it in stopWords } && !filler.matches(title)

    private fun estimateDuration(title: String): Int =
        durationTable.firstOrNull { it.first.containsMatchIn(title) }?.second ?: Task.DEFAULT_DURATION_MINUTES

    private fun eventDuration(title: String): Int =
        if (Regex("\\b(call|stand-?up|check-?in|catch-?up call)\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)) 15 else 60

    private fun String.capitalized() = replaceFirstChar { it.uppercase() }

    private companion object {
        val json = Json { encodeDefaults = true }
        val spaces = Regex("\\s+")
        val trimChars = charArrayOf(' ', '.', ',', '!', '?', '-', ':', ';', '~', '@', '(', ')', '\'', '"')
        val stopWords = setOf("at", "and", "the", "a", "an", "to", "on", "in", "of", "for", "by", "then", "also", "it", "do", "i", "me", "")
        val filler = Regex("^(that's it|thats it|thanks|thank you|please|that is all|nothing else|ok|okay|yes|no)$", RegexOption.IGNORE_CASE)
        val todayPhrase = Regex("\\btonight\\b|\\bthis\\s+(?:morning|afternoon|evening)\\b", RegexOption.IGNORE_CASE)
        val remindPhrase = Regex(
            "\\b(?:please\\s+)?(?:remind\\s+me|set\\s+(?:a\\s+)?reminder|don'?t\\s+let\\s+me\\s+forget)(?:\\s+(?:to|about|that))?\\b",
            RegexOption.IGNORE_CASE,
        )
        val relativePhrase = Regex(
            "\\b(?:in|after)\\s+(?:\\d{1,3}(?:\\.\\d+)?|an?|one|two|three|half\\s+an?)\\s*(?:hours?|hrs?|hr|h|minutes?|mins?|min|m)\\b",
            RegexOption.IGNORE_CASE,
        )
        val leadIn = Regex(
            "^(?:(?:so|ok|okay|please|and|also|then)\\s+)*(?:(?:i|we)(?=\\s|')\\s*)?" +
                "(?:need to|have to|has to|must|should|want to|wanna|gotta|got to|will|plan to|would like to|'d like to|'ll|am going to|'m going to|going to)?\\s*" +
                "(?:remember to)?\\s*",
            RegexOption.IGNORE_CASE,
        )
        val haveLeadIn = Regex("^(?:(?:(?:i|we)\\s*)?(?:have|'ve got|got)|there(?: is|'s))\\s+(?:a |an |the |my )?", RegexOption.IGNORE_CASE)
        val eventWord = Regex(
            "\\b(meeting|appointment|interview|class|lecture|flight|standup|stand-up|dentist|doctor|exam|session|call)\\b",
            RegexOption.IGNORE_CASE,
        )
        val leadingFiller = Regex("^(?:(?:and|then|also|plus|so|at|on|from|by|for|around|in|until|till|between|to|is)\\b\\s*)+", RegexOption.IGNORE_CASE)
        val trailingFiller = Regex(
            "(?:\\s+(?:at|on|from|by|for|around|in|between|and|to|with|till|until|the|of|this|next|every|each|is|o'?clock))+$",
            RegexOption.IGNORE_CASE,
        )
        val highMarker = Regex("\\b(urgent|urgently|asap|important|critical|high priority|top priority|deadline|due)\\b|!!", RegexOption.IGNORE_CASE)
        val lowMarker = Regex("\\b(maybe|if (?:i have )?time|optional|low priority|someday|if possible|when free)\\b", RegexOption.IGNORE_CASE)
        val priorityWords = Regex(
            "\\b(urgently|urgent|asap|important|critical|high priority|top priority|low priority|maybe|if (?:i have )?time|optional|someday|if possible|when free)\\b|!!+",
            RegexOption.IGNORE_CASE,
        )
        val dailyRepeat = Regex(
            "\\b(?:every\\s*day|everyday|daily|each\\s+day)\\b|\\b(?:every|each)\\s+(?=(?:morning|afternoon|evening|night)\\b)",
            RegexOption.IGNORE_CASE,
        )
        val weekdaysRepeat = Regex(
            "\\b(?:every\\s+weekday|each\\s+weekday|on\\s+weekdays|weekdays|mon(?:day)?\\s*(?:-|to)\\s*fri(?:day)?)\\b",
            RegexOption.IGNORE_CASE,
        )
        val weeklyRepeat = Regex(
            "\\b(?:every|each)\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b|" +
                "\\bon\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)s\\b|" +
                "\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)s\\b|\\b(?:weekly|every\\s+week)\\b",
            RegexOption.IGNORE_CASE,
        )
        val durationTable: List<Pair<Regex, Int>> = listOf(
            "\\b(call|phone|ring)\\b" to 15,
            "\\b(e-?mail|text|message|reply|respond|whatsapp)\\b" to 15,
            "\\b(pay|bill|book|renew|order|recharge)\\b" to 15,
            "\\b(proposal|report|presentation|deck|thesis|assignment|homework)\\b" to 120,
            "\\b(write|draft|prepare|study|review|plan)\\b" to 60,
            "\\b(work ?out|gym|exercise|run|jog|yoga|swim|walk)\\b" to 60,
            "\\b(groceries|grocery|shopping|shop|buy)\\b" to 45,
            "\\b(clean|laundry|cook|dishes|tidy)\\b" to 45,
            "\\b(read)\\b" to 30,
            "\\b(stretch|meditate|water)\\b" to 15,
        ).map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }
    }
}
