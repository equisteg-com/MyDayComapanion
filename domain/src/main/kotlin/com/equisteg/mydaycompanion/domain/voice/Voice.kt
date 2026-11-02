package com.equisteg.mydaycompanion.domain.voice

import com.equisteg.mydaycompanion.domain.agent.AgentProposal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** What a short spoken answer means in a voice conversation. */
enum class VoiceIntent {
    /** "yes", "add it", "sounds good": approve the plan or suggestion that is on screen. */
    CONFIRM,

    /** "no", "cancel", "never mind": drop it. */
    CANCEL,

    /** "stop", "bye", "that's all": end the conversation. */
    STOP,

    /** Anything else is a new request. */
    OTHER,
}

/**
 * Reads the owner's spoken answer with fixed rules (never a model), so only the owner's own words
 * can approve something. The whole utterance must be the answer: "yes, and call mom" is a new
 * request, not an approval, so nothing extra slips through on a "yes".
 */
object VoiceIntents {
    fun parse(text: String): VoiceIntent {
        val t = text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(polite, "")
            .trim()
        return when {
            t.isEmpty() -> VoiceIntent.OTHER
            confirm.matches(t) -> VoiceIntent.CONFIRM
            cancel.matches(t) -> VoiceIntent.CANCEL
            stop.matches(t) -> VoiceIntent.STOP
            else -> VoiceIntent.OTHER
        }
    }

    private val polite = Regex("^(?:(?:ok|okay|alright|right|um|uh|hmm|well|so)\\s+)+|(?:\\s+(?:please|thanks|thank you|then|now|for me))+$")
    private val confirm = Regex(
        "(?:yes|yeah|yep|yup|ya|yah|sure|ok|okay|alright|correct|right|perfect|great|good|fine|confirm|confirmed|approve|do it|go ahead|go for it|" +
            "add it|add them|add that|add those|save it|save them|save that|sounds good|looks good|that's good|that's right|that's fine|" +
            "yes please|yes add it|yes do it|yes go ahead|please do|let's do it|make it so)(?: (?:yes|please|thanks))*",
    )
    private val cancel = Regex(
        "(?:no|nope|nah|cancel|cancel it|cancel that|never mind|nevermind|don't|do not|don't add it|discard|discard it|forget it|" +
            "scrap that|scrap it|not now|no thanks|no thank you|leave it|skip it|skip)(?: (?:no|thanks))*",
    )
    private val stop = Regex(
        "(?:stop|stop listening|bye|bye bye|goodbye|good bye|that's all|that's it|that is all|that's everything|i'm done|im done|done|" +
            "end|end chat|exit|quit|close|nothing else|nothing|thank you that's all|thanks that's all|see you)",
    )
}

/**
 * Turns what the agent shows on screen into a short sentence to speak. Only the items the owner
 * just said are read back (never the titles of tasks already saved, which may be personal).
 */
object SpokenReplies {
    /** At most this many items are read out; the rest are counted. */
    const val MAX_ITEMS = 6

    fun proposal(p: AgentProposal.Ready, today: LocalDate): String {
        val newTitles = (p.extraction.tasks.map { it.title } + p.extraction.fixedEvents.map { it.title }).map { it.lowercase(Locale.ROOT) }.toSet()
        val lines = mutableListOf<String>()
        var unscheduled = 0
        var shown = 0
        var total = 0
        for (plan in p.previewPlans) {
            val blocks = plan.blocks.filter { it.title.lowercase(Locale.ROOT) in newTitles }.sortedBy { it.start }
            val skipped = plan.unscheduledTaskIds.size
            unscheduled += skipped
            if (blocks.isEmpty()) continue
            total += blocks.size
            val items = blocks.take((MAX_ITEMS - shown).coerceAtLeast(0)).map { "${it.title} at ${time(it.start)}" }
            shown += items.size
            if (items.isNotEmpty()) lines += "${dayLabel(plan.date, today).replaceFirstChar { it.uppercase() }}: ${list(items)}."
        }
        val more = total - shown
        return buildString {
            append(if (lines.isEmpty()) "I couldn't fit anything new." else lines.joinToString(" "))
            if (more > 0) append(" And ${count(more, "more item")}.")
            if (unscheduled > 0) append(" ${count(unscheduled, "task")} didn't fit.")
            p.warnings.firstOrNull { it.startsWith("When is") || it.startsWith("Could not tell") }?.let { append(" ").append(it) }
            append(if (p.previewPlans.size > 1) " Shall I add these to your days?" else " Shall I add this to your day?")
        }
    }

    /** "today", "tomorrow" or the weekday. */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> "on " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }

    /** 9 AM, 6:30 PM, noon. Spoken engines read these naturally. */
    fun time(t: LocalTime): String {
        if (t == LocalTime.NOON) return "noon"
        val h = t.hour % 12
        val hour = if (h == 0) 12 else h
        val half = if (t.hour < 12) "AM" else "PM"
        return if (t.minute == 0) "$hour $half" else String.format(Locale.ROOT, "%d:%02d %s", hour, t.minute, half)
    }

    /**
     * Text made fit to be spoken: emoji and symbols dropped, whitespace tidied, and long text cut at
     * a sentence end so a reply never reads on for minutes.
     */
    fun forSpeech(text: String, maxChars: Int = 600): String {
        val clean = text
            .replace(Regex("[\\p{So}\\p{Cn}\\p{Cs}\\u200d\\ufe0f]"), " ")
            .replace(Regex("[•·*#_`~|<>\\[\\]{}]"), " ")
            .replace("ℹ", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.length <= maxChars) return clean
        val cut = clean.substring(0, maxChars)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (end > maxChars / 2) cut.substring(0, end + 1) else "$cut…"
    }

    private fun list(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private fun count(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

}
