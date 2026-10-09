package com.equisteg.mydaycompanion.domain.ai.text

/** Text helpers shared by the date, time and duration readers. All matching is case-insensitive. */
internal object TextSpans {
    private val spaces = Regex("\\s+")

    fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    /** Replaces every range with a space so the remaining words keep their order and spacing. */
    fun cut(text: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return text
        val sb = StringBuilder(text)
        for (r in ranges.sortedByDescending { it.first }) sb.replace(r.first, r.last + 1, " ")
        return sb.toString().replace(spaces, " ").trim()
    }

    fun overlaps(a: IntRange, b: IntRange) = a.first <= b.last && b.first <= a.last

    /** Common shorthand and misspellings, expanded before any parsing. Whole words only. */
    private val shorthand: List<Pair<Regex, String>> = listOf(
        "tmrw|tmrow|tmr|tmro|tmorrow|tomorow|tommorow|tommorrow|tomorrw|2moro|2morrow|tomoro" to "tomorrow",
        "tdy|2day|todday" to "today",
        "tonite|2nite|tonigh" to "tonight",
        "mtg|meetin" to "meeting",
        "appt|apptmt" to "appointment",
        "b4" to "before",
        "eod" to "end of day",
        "w/" to "with",
        "pls|plz" to "please",
        "wknd" to "weekend",
    ).map { (from, to) -> Regex("(?<![\\w/])(?:$from)(?![\\w/])", RegexOption.IGNORE_CASE) to to }

    fun expandShorthand(text: String): String {
        var out = text
            .replace('–', '-').replace('—', '-')
            .replace(Regex("\\s&\\s"), " and ")
            .replace(Regex("\\s\\+\\s"), " and ")
        for ((r, to) in shorthand) out = out.replace(r, to)
        return out
    }
}
