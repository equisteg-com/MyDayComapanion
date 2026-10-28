package com.equisteg.mydaycompanion.domain.agent

import com.equisteg.mydaycompanion.domain.ai.text.DateParser
import com.equisteg.mydaycompanion.domain.ai.text.NumericDateOrder
import com.equisteg.mydaycompanion.domain.ai.text.TimeParser
import com.equisteg.mydaycompanion.domain.ai.text.TimeRole
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime

/**
 * Things the owner can ask the agent in the chat besides "plan these tasks" (UPD-011). They are
 * read by fixed rules on the phone, never by the model, and none of them changes anything by
 * itself: a move or a roll-over becomes a suggestion the owner approves with a tap.
 */
sealed interface AgentCommand {
    /** "move gym to friday", "push call Raj to 4pm", "reschedule dentist to tomorrow 3pm", "postpone report". */
    data class Move(val query: String, val date: LocalDate?, val time: LocalTime?) : AgentCommand

    /** "what's next", "what now", "what should I do now". */
    data object WhatsNext : AgentCommand

    /** "what's left", "how's my day", "summary". */
    data object DaySummary : AgentCommand

    /** "roll over", "move unfinished to today", "carry over yesterday". */
    data object RollOver : AgentCommand

    /** "focus 25 min on report", "focus on report", "start a focus timer". */
    data class Focus(val minutes: Int, val query: String?) : AgentCommand
}

class AgentCommandParser(dateOrder: NumericDateOrder = NumericDateOrder.DAY_MONTH) {
    private val dates = DateParser(dateOrder)

    fun parse(input: String, today: LocalDate, now: LocalTime): AgentCommand? {
        val text = input.trim().trimEnd('?', '!', '.', ' ').replace('’', '\'').replace(Regex("\\s+"), " ")
        if (text.isEmpty() || text.length > MAX_COMMAND_CHARS) return null
        if (whatsNext.matches(text)) return AgentCommand.WhatsNext
        if (summary.matches(text)) return AgentCommand.DaySummary
        if (rollOver.matches(text)) return AgentCommand.RollOver
        focus(text)?.let { return it }
        return move(text, today, now)
    }

    private fun focus(text: String): AgentCommand.Focus? {
        if (focusBare.matches(text)) return AgentCommand.Focus(DEFAULT_FOCUS_MINUTES, null)
        val m = focusFull.matchEntire(text) ?: return null
        val minutes = (m.groups["m1"]?.value ?: m.groups["m2"]?.value)?.toIntOrNull() ?: DEFAULT_FOCUS_MINUTES
        if (minutes !in MIN_FOCUS_MINUTES..MAX_FOCUS_MINUTES) return null
        val query = (m.groups["q1"]?.value ?: m.groups["q2"]?.value)?.trim()?.takeIf { it.isNotEmpty() }
        return AgentCommand.Focus(minutes, query)
    }

    private fun move(text: String, today: LocalDate, now: LocalTime): AgentCommand.Move? {
        val m = moveVerb.find(text) ?: return null
        val rest = text.substring(m.range.last + 1).trim()
        if (rest.isEmpty()) return null
        val postpone = m.value.trim().lowercase().startsWith("postpone")
        val split = destinationSplit.find(rest)
        val (query, destination) = if (split != null) {
            rest.substring(0, split.range.first).trim() to rest.substring(split.range.last + 1).trim()
        } else {
            // "move gym tomorrow": the destination is the date or time phrase at the end.
            val date = dates.findAll(rest, today).lastOrNull()
            val time = TimeParser.findAll(rest, now).lastOrNull { it.role == TimeRole.AT }
            val start = listOfNotNull(date?.range?.first, time?.range?.first).minOrNull()
            if (start == null) rest to "" else rest.substring(0, start).trim() to rest.substring(start).trim()
        }
        val cleanQuery = query.replace(leadingArticle, "").trim()
        if (cleanQuery.isEmpty()) return null
        val date = dates.findAll(destination, today).firstOrNull()?.date
        val time = TimeParser.findAll(destination, now).firstOrNull { it.role == TimeRole.AT }?.start
        return when {
            date != null || time != null -> AgentCommand.Move(cleanQuery, date, time)
            postpone && destination.isEmpty() -> AgentCommand.Move(cleanQuery, today.plusDays(1), null)
            else -> null
        }
    }

    companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val MIN_FOCUS_MINUTES = 5
        const val MAX_FOCUS_MINUTES = 180
        private const val MAX_COMMAND_CHARS = 160
        private val i = setOf(RegexOption.IGNORE_CASE)

        private val whatsNext = Regex("(what'?s|what is|what) (next|now|up next|on now|coming up)( for me)?|what (should|do|can) i do (now|next)|next|up next", i)
        private val summary = Regex("(what'?s|what is) left( (today|for today))?|how'?s my day( going)?|how is my day( going)?|(day )?summary|summari[sz]e( my day)?|what'?s my day( look like)?", i)
        private val rollOver = Regex(
            "roll ?over|carry (it |them )?over( (from )?yesterday)?|(move|bring|carry) (all |my )?(the )?(unfinished|undone|leftover|left ?over|open|missed) (tasks |items |ones )?(from yesterday )?(to|into|over to|onto) today",
            i,
        )
        private val focusBare = Regex("(start )?(a )?focus( timer| session| mode)?|focus now", i)
        private val focusFull = Regex(
            "(start )?(a )?focus( timer| session)?( for)? (?<m1>\\d{1,3}) ?(m|min|mins|minutes)?( on (?<q1>.+))?|(start )?(a )?focus( timer| session)? on (?<q2>.+?)( for (?<m2>\\d{1,3}) ?(m|min|mins|minutes)?)?",
            i,
        )
        private val moveVerb = Regex("^(move|reschedule|push( back)?|shift|postpone|put off|put)\\s", i)
        private val leadingArticle = Regex("^(my|the)\\s+", i)
        private val destinationSplit = Regex("\\s(to|till|until|for|on|onto)\\s(?!.*\\s(to|till|until|onto)\\s)", i)
    }
}

/** How a typed name ("raj", "the report") found the owner's task. */
sealed interface TaskMatch {
    data class One(val task: Task) : TaskMatch
    data class Many(val tasks: List<Task>) : TaskMatch
    data object None : TaskMatch
}

/**
 * Finds the open task the owner meant. Exact title first, then a title that contains the words
 * typed; several equally good matches are given back so the agent can ask which one.
 */
object TaskMatcher {
    private val stopWords = setOf("the", "my", "a", "an", "to", "task", "with")

    fun find(query: String, tasks: List<Task>, preferDate: LocalDate? = null): TaskMatch {
        val open = tasks.filter { it.status == TaskStatus.OPEN && it.parentId == null }
        val q = query.trim().lowercase()
        if (q.isEmpty()) return TaskMatch.None
        val words = q.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 && it !in stopWords }
        val exact = open.filter { it.title.trim().lowercase() == q }
        val contains = open.filter { it.title.lowercase().contains(q) }
        val allWords = if (words.isEmpty()) emptyList() else open.filter { t -> val title = t.title.lowercase(); words.all { it in title } }
        val best = listOf(exact, contains, allWords).firstOrNull { it.isNotEmpty() } ?: return TaskMatch.None
        if (best.size == 1) return TaskMatch.One(best.single())
        val onDay = preferDate?.let { d -> best.filter { it.date == d } }.orEmpty()
        return if (onDay.size == 1) TaskMatch.One(onDay.single()) else TaskMatch.Many(best.take(MAX_CHOICES))
    }

    private const val MAX_CHOICES = 5
}

/** The agent's answers in the chat, worked out on the phone from the plan and tasks (UPD-011). */
object AgentReplies {
    private val dayName = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ENGLISH)

    fun whatsNext(plan: com.equisteg.mydaycompanion.domain.model.DayPlan?, tasks: List<Task>, now: LocalTime): String {
        val nn = com.equisteg.mydaycompanion.domain.insights.DayInsights.nowNext(plan, tasks, now)
        val parts = listOfNotNull(
            nn.now?.let { "Now: ${it.title}, until ${it.end}." },
            nn.next?.let { "Next: ${it.title} at ${it.start}." },
        )
        return if (parts.isEmpty()) "Nothing else is planned today. Tell me what you'd like to add." else parts.joinToString(" ")
    }

    fun daySummary(tasks: List<Task>, today: LocalDate): String {
        val day = tasks.filter { it.date == today && it.parentId == null && it.status != TaskStatus.CANCELLED }
        if (day.isEmpty()) return "Nothing is planned today yet."
        val done = day.count { it.status == TaskStatus.DONE }
        val open = day.filter { it.status == TaskStatus.OPEN }
        if (open.isEmpty()) return "All ${day.size} done today. Nice work!"
        val names = open.take(3).map { it.title }
        val more = if (open.size > 3) " and ${open.size - 3} more" else ""
        return "$done of ${day.size} done. Still to do: ${names.joinToString(", ")}$more."
    }

    fun whenText(date: LocalDate?, time: LocalTime?, today: LocalDate): String = listOfNotNull(
        date?.let {
            when (it) {
                today -> "today"
                today.plusDays(1) -> "tomorrow"
                else -> it.format(dayName)
            }
        },
        time?.let { "at $it" },
    ).joinToString(" ")

    fun rollOver(tasks: List<Task>): String {
        val names = tasks.take(3).joinToString(", ") { it.title }
        val more = if (tasks.size > 3) " and ${tasks.size - 3} more" else ""
        return if (tasks.size == 1) "1 unfinished task from an earlier day: $names." else "${tasks.size} unfinished tasks from earlier days: $names$more."
    }
}
