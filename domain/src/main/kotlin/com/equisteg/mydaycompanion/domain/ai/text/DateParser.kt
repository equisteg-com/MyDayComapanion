package com.equisteg.mydaycompanion.domain.ai.text

import com.equisteg.mydaycompanion.domain.ai.text.TextSpans.rx
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class DateMatch(val range: IntRange, val date: LocalDate)

/** How to read an all-number date such as 12/10. */
enum class NumericDateOrder { DAY_MONTH, MONTH_DAY }

/**
 * Finds date phrases ("tomorrow", "tmrw", "next friday", "on the 12th", "Oct 12", "12/10",
 * "in 3 days", "this weekend") and resolves them against [today]. Dates are never in the past:
 * a day-of-month or month-day that has already gone this month/year moves to the next one.
 */
class DateParser(private val numericOrder: NumericDateOrder = NumericDateOrder.DAY_MONTH) {

    fun findAll(text: String, today: LocalDate): List<DateMatch> {
        val found = mutableListOf<DateMatch>()
        fun add(range: IntRange, date: LocalDate?) {
            if (date == null || found.any { TextSpans.overlaps(it.range, range) }) return
            found += DateMatch(range, date)
        }
        iso.findAll(text).forEach { m ->
            add(m.range, safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()))
        }
        dayAfterTomorrow.findAll(text).forEach { add(it.range, today.plusDays(2)) }
        tomorrow.findAll(text).forEach { add(it.range, today.plusDays(1)) }
        todayWord.findAll(text).forEach { add(it.range, today) }
        inDays.findAll(text).forEach { m ->
            val n = number(m.groupValues[1]) ?: return@forEach
            val days = if (m.groupValues[2].startsWith("week", ignoreCase = true)) n * 7L else n.toLong()
            add(m.range, today.plusDays(days))
        }
        nextWeek.findAll(text).forEach { add(it.range, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))) }
        weekend.findAll(text).forEach { add(it.range, today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))) }
        dayMonth.findAll(text).forEach { m ->
            add(m.range, forward(today, month(m.groupValues[2]), m.groupValues[1].toInt()))
        }
        monthDay.findAll(text).forEach { m ->
            add(m.range, forward(today, month(m.groupValues[1]), m.groupValues[2].toInt()))
        }
        numeric.findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val (day, mon) = if (numericOrder == NumericDateOrder.DAY_MONTH) a to b else b to a
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            add(m.range, if (year != null) safeDate(year, mon, day) else forward(today, mon, day))
        }
        ordinal.findAll(text).forEach { m ->
            val day = m.groupValues[1].toInt()
            var d = safeDate(today.year, today.monthValue, day)
            if (d == null || d.isBefore(today)) d = today.plusMonths(1).let { safeDate(it.year, it.monthValue, day) }
            add(m.range, d)
        }
        weekdayFull.findAll(text).forEach { m -> add(m.range, weekdayDate(today, m.groupValues[1], m.groupValues[2])) }
        weekdayShort.findAll(text).forEach { m -> add(m.range, weekdayDate(today, m.groupValues[1], m.groupValues[2])) }
        weekendShort.findAll(text).forEach { m -> add(m.range, weekdayDate(today, m.groupValues[1], m.groupValues[2])) }
        return found.sortedBy { it.range.first }
    }

    private fun weekdayDate(today: LocalDate, prefix: String, name: String): LocalDate {
        val dow = weekdays.entries.first { name.lowercase().startsWith(it.key) }.value
        // "this friday" on a Friday means today; otherwise the coming one.
        return if (prefix.trim().equals("this", ignoreCase = true) && today.dayOfWeek == dow) today
        else today.with(TemporalAdjusters.next(dow))
    }

    private fun forward(today: LocalDate, month: Int?, day: Int): LocalDate? {
        if (month == null) return null
        val d = safeDate(today.year, month, day) ?: return null
        return if (d.isBefore(today)) safeDate(today.year + 1, month, day) else d
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try { LocalDate.of(y, m, d) } catch (e: DateTimeException) { null }

    private fun month(name: String): Int? = months.entries.firstOrNull { name.lowercase().startsWith(it.key) }?.value

    private fun number(s: String): Int? = s.toIntOrNull() ?: words[s.lowercase()]

    private companion object {
        val weekdays = linkedMapOf(
            "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
        )
        val months = linkedMapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        )
        val words = mapOf("a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7)
        const val MONTH = "(january|february|march|april|may|june|july|august|september|october|november|december|" +
            "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)\\.?"

        val iso = rx("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")
        val dayAfterTomorrow = rx("\\b(?:the\\s+)?day\\s+after\\s+tomorrow\\b|\\bovermorrow\\b")
        val tomorrow = rx("\\b(?:by\\s+|on\\s+)?tomorrow(?:'s)?\\b")
        val todayWord = rx("\\b(?:by\\s+|for\\s+)?today(?:'s)?\\b")
        val inDays = rx("\\b(?:in|after)\\s+(\\d{1,2}|a|an|one|two|three|four|five|six|seven)\\s+(days?|weeks?)\\b")
        val nextWeek = rx("\\b(?:next|coming)\\s+week\\b")
        val weekend = rx("\\b(?:on\\s+|this\\s+|the\\s+|next\\s+)?weekend\\b")
        val dayMonth = rx("\\b(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTH(?![a-z])")
        val monthDay = rx("\\b(?:on\\s+)?$MONTH\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
        val numeric = rx("(?<![\\d/:.])(?:on\\s+)?(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}|\\d{4}))?(?![\\d/])")
        val ordinal = rx("\\b(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)\\b")
        val weekdayFull = rx("\\b(on\\s+|this\\s+|next\\s+|coming\\s+|by\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)s?\\b")
        // Short names that are also English words (sat, sun) only count with a lead-in.
        val weekdayShort = rx("\\b(on\\s+|this\\s+|next\\s+|coming\\s+|by\\s+)?(mon|tues|tue|wed|thurs|thur|thu|fri)\\b")
        val weekendShort = rx("\\b(on\\s+|this\\s+|next\\s+|coming\\s+|by\\s+)(sat|sun)\\b")
    }
}
