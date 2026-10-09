package com.equisteg.mydaycompanion.domain.ai.text

import com.equisteg.mydaycompanion.domain.ai.text.TextSpans.rx
import com.equisteg.mydaycompanion.domain.model.Task

data class DurationMatch(val range: IntRange, val minutes: Int)

/** Reads "for 2 hours", "90 min", "1.5h", "1h30", "an hour and a half", "half an hour", "(30m)", "2-3 hours". */
object DurationParser {
    fun findAll(text: String): List<DurationMatch> {
        val found = mutableListOf<DurationMatch>()
        fun add(range: IntRange, minutes: Double?) {
            if (minutes == null || minutes <= 0 || found.any { TextSpans.overlaps(it.range, range) }) return
            found += DurationMatch(range, minutes.toInt().coerceIn(Task.MIN_DURATION_MINUTES, Task.MAX_DURATION_MINUTES))
        }
        hoursAndMinutes.findAll(text).forEach { m ->
            add(m.range, m.groupValues[1].toDouble() * 60 + m.groupValues[2].toInt())
        }
        compact.findAll(text).forEach { m -> add(m.range, m.groupValues[1].toDouble() * 60 + m.groupValues[2].toInt()) }
        hourAndHalf.findAll(text).forEach { add(it.range, 90.0) }
        halfHour.findAll(text).forEach { add(it.range, 30.0) }
        quarterHour.findAll(text).forEach { add(it.range, 15.0) }
        oneHour.findAll(text).forEach { add(it.range, 60.0) }
        numberUnit.findAll(text).forEach { m ->
            val n = m.groupValues[2].toDoubleOrNull()
            add(m.range, n?.let { if (m.groupValues[3].lowercase().startsWith("h")) it * 60 else it })
        }
        return found.sortedBy { it.range.first }
    }

    private const val LEAD = "\\b(?:for\\s+)?(?:about\\s+|around\\s+|roughly\\s+|approx\\.?\\s+|~\\s*)?"
    private const val HOURS = "(?:hours?|hrs?|hr|h)"
    private const val MINUTES = "(?:minutes?|mins?|min|m)"

    private val hoursAndMinutes = rx("$LEAD(\\d+(?:\\.\\d+)?)\\s*$HOURS\\s*(?:and\\s+)?(\\d{1,2})\\s*$MINUTES\\b")
    private val compact = rx("$LEAD(\\d{1,2})h(\\d{2})\\b")
    private val hourAndHalf = rx("$LEAD(?:an?|one)\\s+(?:hour|hr)\\s+and\\s+a\\s+half\\b")
    private val halfHour = rx("$LEAD(?:half\\s+an?\\s+hour|half\\s*-?\\s*hour)\\b")
    private val quarterHour = rx("$LEAD(?:a\\s+)?quarter\\s+of\\s+an\\s+hour\\b")
    private val oneHour = rx("$LEAD(?:an|one)\\s+(?:hour|hr)\\b")
    private val numberUnit = rx("\\(?$LEAD(?:(\\d+)\\s*-\\s*)?(\\d+(?:\\.\\d+)?)\\s*-?\\s*($HOURS|$MINUTES)\\b\\)?")
}
