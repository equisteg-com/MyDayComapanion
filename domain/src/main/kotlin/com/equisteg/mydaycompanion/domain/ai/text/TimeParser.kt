package com.equisteg.mydaycompanion.domain.ai.text

import com.equisteg.mydaycompanion.domain.ai.text.TextSpans.rx
import java.time.LocalTime

/** How a time relates to the item it belongs to. */
enum class TimeRole {
    /** A clock time the item happens at ("at 5", "5pm", "3-4pm", "half past 7"). */
    AT,

    /** The item must be done before this time ("by 5", "before noon", "by end of day"). */
    DEADLINE,

    /** The item should start after this time ("after 6", "after lunch"). */
    AFTER,

    /** A loose part of the day ("in the morning", "tonight"). */
    SOFT,
}

/**
 * One time phrase found in a clause. [end] is set for ranges. [assumedHalf] is true when no
 * am/pm or part of day was given and the half of the day was chosen by the bare-hour rule.
 * [alwaysCut] marks phrases that are never part of a title ("tonight", "this evening").
 */
data class TimeMatch(
    val range: IntRange,
    val role: TimeRole,
    val start: LocalTime,
    val end: LocalTime? = null,
    val assumedHalf: Boolean = false,
    val alwaysCut: Boolean = true,
)

/**
 * Reads times written the way people type them, in any position in the sentence:
 * 5pm, 5 p.m., 5p, 17:30, 5.30pm, at 5, @5, around 5ish, 5 o'clock, at 1730, half past 7,
 * quarter to 8, 10 to 8, 3-4pm, from 3 till 4:30, between 3 and 4, noon, midnight,
 * end of day, before lunch, after work, in the morning, tonight, in 2 hours.
 *
 * Bare-hour rule (no am/pm and no part of day): 1–6 → pm, 7–11 → am, 12 → noon, unless the
 * clause mentions the evening (dinner, party, tonight …), which moves 1–11 to pm.
 */
object TimeParser {

    fun findAll(text: String, now: LocalTime? = null): List<TimeMatch> {
        val evening = eveningWords.containsMatchIn(text)
        val morning = morningWords.containsMatchIn(text)
        val eventLike = eventWords.containsMatchIn(text)
        val found = mutableListOf<TimeMatch>()
        fun add(m: TimeMatch?) {
            if (m != null && found.none { TextSpans.overlaps(it.range, m.range) }) found += m
        }

        if (now != null) relative.findAll(text).forEach { add(relativeMatch(it, now)) }
        range.findAll(text).forEach { add(rangeMatch(it, evening, morning, eventLike)) }
        phrase.findAll(text).forEach { add(phraseMatch(it, evening, morning)) }
        named.findAll(text).forEach { add(namedMatch(it)) }
        meal.findAll(text).forEach { add(mealMatch(it)) }
        fourDigit.findAll(text).forEach { add(fourDigitMatch(it, evening, morning)) }
        clock.findAll(text).forEach { add(clockMatch(it, text, evening, morning)) }
        eventHour.findAll(text).forEach { m ->
            hourOf(m.groupValues[1].toInt(), null, null, evening, morning)?.let { (h, assumed) ->
                add(TimeMatch(m.range, TimeRole.AT, LocalTime.of(h, 0), assumedHalf = assumed))
            }
        }
        dayPart.findAll(text).forEach { add(dayPartMatch(it)) }
        return found.sortedBy { it.range.first }
    }

    private enum class Part { MORNING, AFTERNOON, EVENING }

    private fun part(s: String?): Part? = when (s?.lowercase()) {
        null, "" -> null
        "morning" -> Part.MORNING
        "afternoon" -> Part.AFTERNOON
        else -> Part.EVENING // evening, night, tonight
    }

    private fun meridiem(m: MatchResult, n: String): Char? {
        val word = m.groups["am$n"]?.value ?: m.groups["ap$n"]?.value ?: return null
        return word.first().lowercaseChar()
    }

    /** Applies am/pm, part of day or the bare-hour rule. Returns null for impossible times. */
    private fun hourOf(h: Int, mer: Char?, part: Part?, evening: Boolean, morning: Boolean): Pair<Int, Boolean>? {
        if (mer != null) {
            if (h !in 1..12) return null
            return (if (mer == 'a') h % 12 else h % 12 + 12) to false
        }
        if (h > 23) return null
        if (h == 0 || h > 12) return h to false
        return when (part) {
            Part.MORNING -> (h % 12) to false
            Part.AFTERNOON, Part.EVENING -> (if (h == 12) 12 else h + 12) to false
            null -> when {
                h == 12 -> 12 to true
                evening && !morning -> (h + 12) to false
                morning -> h to false
                h in 1..6 -> (h + 12) to true
                else -> h to true
            }
        }
    }

    private fun roleOf(prefix: String?): TimeRole = when (prefix?.trim()?.lowercase()) {
        "by", "before", "till", "until" -> TimeRole.DEADLINE
        "after" -> TimeRole.AFTER
        else -> TimeRole.AT
    }

    private fun relativeMatch(m: MatchResult, now: LocalTime): TimeMatch? {
        val n = m.groupValues[1].lowercase()
        val amount = when {
            n.startsWith("half") -> 0.5
            n == "a" || n == "an" || n == "one" -> 1.0
            n == "two" -> 2.0
            n == "three" -> 3.0
            else -> n.toDoubleOrNull() ?: return null
        }
        val minutes = if (m.groupValues[2].lowercase().startsWith("h")) amount * 60 else amount
        val total = now.hour * 60 + now.minute + minutes.toInt()
        val rounded = (total + 4) / 5 * 5
        if (minutes <= 0 || rounded >= 24 * 60) return null
        return TimeMatch(m.range, TimeRole.AT, LocalTime.of(rounded / 60, rounded % 60))
    }

    private fun rangeMatch(m: MatchResult, evening: Boolean, morning: Boolean, eventLike: Boolean): TimeMatch? {
        val pre = m.groups["pre"]?.value?.trim()?.lowercase()
        val conn = m.groups["conn"]!!.value.lowercase()
        val mer1 = meridiem(m, "1")
        val mer2 = meridiem(m, "2")
        val colon = m.groups["s1"]?.value == ":" || m.groups["s2"]?.value == ":"
        if (conn == "and" && pre != "between") return null
        // "lunch 1 to 2" is a time range; "read pages 3-4" is not.
        if (pre == null && m.groups["sym"] == null && mer1 == null && mer2 == null && !colon && !eventLike) return null
        val h1 = m.groups["h1"]!!.value.toInt()
        val h2 = m.groups["h2"]!!.value.toInt()
        val m1 = m.groups["m1"]?.value?.toInt() ?: 0
        val m2 = m.groups["m2"]?.value?.toInt() ?: 0
        if (m1 > 59 || m2 > 59) return null
        val p = part(m.groups["dp"]?.value)
        val (endHour, endAssumed) = hourOf(h2, mer2, p, evening, morning) ?: return null
        var end = LocalTime.of(endHour, m2)
        val startPair = when {
            mer1 != null -> hourOf(h1, mer1, null, evening, morning)
            mer2 != null || p != null -> {
                // "3-4pm": the start shares the end's half of the day, unless that puts it after the end ("11-1pm").
                val same = hourOf(h1, mer2, p, evening, morning) ?: return null
                if (LocalTime.of(same.first, m1) < end) same else hourOf(h1, 'a', null, evening, morning)
            }
            else -> hourOf(h1, null, null, evening, morning)
        } ?: return null
        val start = LocalTime.of(startPair.first, m1)
        if (end <= start && mer2 == null && p == null && endHour + 12 <= 23) end = LocalTime.of(endHour + 12, m2)
        return TimeMatch(m.range, TimeRole.AT, start, end.takeIf { it > start }, startPair.second && endAssumed)
    }

    private fun phraseMatch(m: MatchResult, evening: Boolean, morning: Boolean): TimeMatch? {
        val (offset, before) = when {
            m.groups["half"] != null -> 30 to false
            m.groups["qp"] != null -> 15 to false
            m.groups["qt"] != null -> 15 to true
            else -> (m.groups["mm"]!!.value.toInt()) to (m.groups["dir"]!!.value.equals("to", ignoreCase = true))
        }
        if (offset !in 1..59) return null
        // "10 to 8" is a time; "1 to 2" is a range, not "1 minute to 2".
        if (m.groups["mm"] != null && m.groups["unit"] == null && offset % 5 != 0) return null
        val (hour, assumed) = hourOf(m.groups["h"]!!.value.toInt(), meridiem(m, ""), part(m.groups["dp"]?.value), evening, morning) ?: return null
        val base = LocalTime.of(hour, 0)
        val time = if (before) base.minusMinutes(offset.toLong()) else base.plusMinutes(offset.toLong())
        return TimeMatch(m.range, roleOf(m.groups["pre"]?.value), time, assumedHalf = assumed)
    }

    private fun namedMatch(m: MatchResult): TimeMatch? {
        val pre = m.groups["pre"]?.value?.trim()?.lowercase()
        val name = m.groups["n"]!!.value.lowercase()
        return when {
            name == "midnight" -> if (pre == "after") null else TimeMatch(m.range, TimeRole.DEADLINE, LocalTime.of(23, 59))
            name.startsWith("end") || name.startsWith("close") || name == "cob" ->
                TimeMatch(m.range, if (pre == "after") TimeRole.AFTER else TimeRole.DEADLINE, LocalTime.of(18, 0))
            else -> TimeMatch(m.range, roleOf(pre), LocalTime.NOON)
        }
    }

    private fun mealMatch(m: MatchResult): TimeMatch {
        val before = m.groupValues[1].equals("before", ignoreCase = true)
        val time = when (m.groupValues[2].lowercase()) {
            "breakfast" -> if (before) LocalTime.of(8, 0) else LocalTime.of(9, 0)
            "lunch" -> if (before) LocalTime.NOON else LocalTime.of(13, 30)
            "dinner", "supper" -> if (before) LocalTime.of(19, 0) else LocalTime.of(21, 0)
            "school" -> if (before) LocalTime.of(8, 0) else LocalTime.of(16, 0)
            else -> if (before) LocalTime.of(9, 0) else LocalTime.of(18, 0) // work, office
        }
        return TimeMatch(m.range, if (before) TimeRole.DEADLINE else TimeRole.AFTER, time)
    }

    private fun fourDigitMatch(m: MatchResult, evening: Boolean, morning: Boolean): TimeMatch? {
        val pre = m.groups["pre"]?.value
        val mer = meridiem(m, "")
        if (pre == null && m.groups["sym"] == null && mer == null) return null
        val digits = m.groups["hm"]!!.value
        val h = digits.dropLast(2).toInt()
        val min = digits.takeLast(2).toInt()
        if (min > 59) return null
        val (hour, assumed) = hourOf(h, mer, part(m.groups["dp"]?.value), evening, morning) ?: return null
        return TimeMatch(m.range, roleOf(pre), LocalTime.of(hour, min), assumedHalf = assumed)
    }

    private fun clockMatch(m: MatchResult, text: String, evening: Boolean, morning: Boolean): TimeMatch? {
        val pre = m.groups["pre"]?.value
        val mer = meridiem(m, "")
        val sep = m.groups["sep"]?.value
        val dp = m.groups["dp"]?.value
        val marked = m.groups["oc"] != null || m.groups["ish"] != null
        val prefixed = pre != null || m.groups["sym"] != null
        val accepted = mer != null || sep != null || prefixed || marked || dp != null
        if (!accepted) return null
        // "11.30 client call" is a time, but "rs 12.50" or "2.75" is not.
        if (sep == "." && !prefixed && mer == null && dp == null) {
            val minutes = m.groups["m"]!!.value.toInt()
            val before = currencyBefore.containsMatchIn(text.substring(0, m.range.first))
            if (minutes % 5 != 0 || before) return null
        }
        val h = m.groups["h"]!!.value.toInt()
        val min = m.groups["m"]?.value?.toInt() ?: 0
        if (min > 59) return null
        val (hour, assumed) = hourOf(h, mer, part(dp), evening, morning) ?: return null
        return TimeMatch(m.range, roleOf(pre), LocalTime.of(hour, min), assumedHalf = assumed)
    }

    private fun dayPartMatch(m: MatchResult): TimeMatch {
        val p = m.groups["p"]!!.value.lowercase()
        val mod = m.groups["mod"]?.value?.lowercase()
        val hour = when (p) {
            "morning" -> when (mod) { "early" -> 7; "late" -> 11; else -> 9 }
            "afternoon" -> when (mod) { "early" -> 13; "late" -> 16; else -> 14 }
            "evening" -> when (mod) { "early" -> 17; "late" -> 20; else -> 18 }
            else -> if (mod == "late") 22 else 20 // night, tonight
        }
        val role = if (m.groups["pre"] != null) TimeRole.DEADLINE else TimeRole.SOFT
        // A bare "night" or "morning" can be part of a name ("movie night"); qualified phrases never are.
        val qualified = p == "tonight" || mod != null || m.groups["q"] != null || role == TimeRole.DEADLINE
        return TimeMatch(m.range, role, LocalTime.of(hour, 0), alwaysCut = qualified)
    }

    private fun mer(n: String) = "(?:\\s*(?<am$n>a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)(?![a-z])|(?<ap$n>[ap])(?![a-z]))?"

    private const val DP = "(?:\\s*(?:in\\s+the\\s+|at\\s+|this\\s+|in\\s+)?(?<dp>morning|afternoon|evening|night|tonight)\\b)?"
    private const val NUM = "(?<![\\w:.])"

    private val eventWords = rx("\\b(meeting|appointment|interview|class|lecture|lunch|dinner|breakfast|session|shift|office|work|school|call|party|standup|gym|yoga)\\b")
    private val currencyBefore = rx("(?:rs\\.?|inr|usd|eur|[₹$£€])\\s*$")
    private val eveningWords = rx("\\b(dinner|supper|party|movie|film|drinks|pub|bar|concert|tonight|night|evening|date)\\b")
    private val morningWords = rx("\\b(breakfast|morning|sunrise|wake ?up)\\b")

    private val relative = rx("\\b(?:in|after)\\s+(\\d{1,3}(?:\\.\\d+)?|an?|one|two|three|half\\s+an?)\\s*(hours?|hrs?|hr|h|minutes?|mins?|min|m)\\b")
    private val range = rx(
        "(?:(?<pre>\\b(?:from|between|at|around)\\b)\\s*|(?<sym>@)\\s*)?$NUM(?<h1>\\d{1,2})(?:(?<s1>[:.])(?<m1>\\d{2}))?${mer("1")}" +
            "\\s*(?<conn>-|\\bto\\b|\\btill\\b|\\buntil\\b|\\band\\b)\\s*" +
            "(?<h2>\\d{1,2})(?:(?<s2>[:.])(?<m2>\\d{2}))?${mer("2")}(?![\\d:])$DP",
    )
    private val phrase = rx(
        "(?:(?<pre>\\b(?:at|around|by|before|after|till|until|about))\\s+)?" +
            "\\b(?:(?<half>half)\\s+past|(?<qp>quarter)\\s+past|(?<qt>quarter)\\s+to|(?<mm>\\d{1,2})\\s*(?<unit>mins?|minutes?)?\\s+(?<dir>past|to))" +
            "\\s+(?<h>\\d{1,2})(?:\\s*o'?\\s?clock)?${mer("")}$DP",
    )
    private val named = rx(
        "(?:(?<pre>\\b(?:at|around|by|before|after|till|until))\\s+)?" +
            "\\b(?<n>noon|midday|midnight|lunch\\s*time|end\\s+of\\s+(?:the\\s+)?(?:work\\s*)?day|close\\s+of\\s+business|cob)\\b",
    )
    private val meal = rx("\\b(before|after)\\s+(breakfast|lunch|dinner|supper|work|office|school)\\b")
    private val fourDigit = rx("(?:(?<pre>\\b(?:at|by|before|after|from|around))\\s*|(?<sym>@)\\s*)?$NUM(?<hm>\\d{3,4})(?![\\d:.])${mer("")}$DP")
    private val clock = rx(
        "(?:(?<pre>\\b(?:at|around|by|before|after|till|until|from|about))\\s*|(?<sym>[@~])\\s*)?$NUM(?<h>\\d{1,2})(?:(?<sep>[:.])(?<m>\\d{2}))?(?![\\d])" +
            "${mer("")}(?<oc>\\s*o'?\\s?clock)?(?<ish>\\s*-?ish)?$DP",
    )
    /** "dentist 4", "appointment 11": a bare hour right after an event word, at the end of the phrase. */
    private val eventHour = rx(
        "(?<=\\b(?:meeting|appointment|interview|class|lecture|dentist|doctor|lunch|dinner|breakfast|standup|stand-up|session|call|gym|yoga|party)\\s)" +
            "(1[0-2]|[1-9])(?=\\s*$|\\s*[,;.!?])",
    )
    private val dayPart = rx(
        "(?:(?<pre>\\bby)\\s+)?(?:(?<mod>early|late)\\s+)?(?<q>(?:in|during)\\s+the\\s+|this\\s+)?\\b(?<p>morning|afternoon|evening|tonight|night)\\b",
    )
}
