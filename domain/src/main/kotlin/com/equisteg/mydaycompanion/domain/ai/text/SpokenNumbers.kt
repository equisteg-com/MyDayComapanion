package com.equisteg.mydaycompanion.domain.ai.text

import com.equisteg.mydaycompanion.domain.ai.text.TextSpans.rx

/**
 * Speech recognisers often write times and lengths as words ("at seven thirty", "three p.m.",
 * "forty five minutes"). This turns those into digits before parsing, so spoken and typed text
 * read the same way. Only number words next to a time or length are changed; "one task" stays.
 */
internal object SpokenNumbers {
    private val units = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "ninety" to 90)

    private const val UNIT = "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen"
    private const val TENS = "twenty|thirty|forty|fifty|sixty|ninety"
    private const val NUMBER = "(?:(?:$TENS)(?:[\\s-]+(?:one|two|three|four|five|six|seven|eight|nine))?|$UNIT)"
    private const val HOUR = "(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
    private const val MINUTE = "(?:o(?:h)?[\\s-]+(?:one|two|three|four|five|six|seven|eight|nine)|ten|fifteen|twenty(?:[\\s-]+(?:one|two|three|four|five|six|seven|eight|nine))?|thirty(?:[\\s-]+(?:one|two|three|four|five|six|seven|eight|nine))?|forty(?:[\\s-]+(?:one|two|three|four|five|six|seven|eight|nine))?|fifty(?:[\\s-]+(?:one|two|three|four|five|six|seven|eight|nine))?)"
    private const val AFTER_TIME = "(?=\\s*(?:o'?\\s?clock|a\\.?\\s?m\\b\\.?|p\\.?\\s?m\\b\\.?|in\\s+the|tonight|today|tomorrow|[,.;!?]|$|\\s+(?:and|then|for|to|till|until|with|on|this|next|every)\\b))"

    /** "at seven thirty", "by five", "around ten o'clock". */
    private val prefixed = rx("\\b(at|by|around|about|from|till|until|before|after)\\s+($HOUR)(?:[\\s-]+($MINUTE))?$AFTER_TIME")

    /** "seven thirty p.m.", "five pm", "nine o'clock". */
    private val marked = rx("\\b($HOUR)(?:[\\s-]+($MINUTE))?(?=\\s*(?:o'?\\s?clock|a\\.?\\s?m\\b|p\\.?\\s?m\\b))")

    /** The end of a spoken range: "from 10 to six", "2 till four thirty". */
    private val rangeEnd = rx("(?<=\\d)(\\s*(?:-|to|till|until|and)\\s+)($HOUR)(?:[\\s-]+($MINUTE))?\\b")

    /** "forty five minutes", "two hours", "twenty mins". */
    private val length = rx("\\b($NUMBER)(?=\\s*(?:hours?|hrs?|minutes?|mins?)\\b)")

    fun normalize(text: String): String {
        var out = text
        out = out.replace(prefixed) { m -> m.groupValues[1] + " " + clock(m.groupValues[2], m.groupValues[3]) }
        out = out.replace(marked) { m -> clock(m.groupValues[1], m.groupValues[2]) }
        out = out.replace(rangeEnd) { m -> m.groupValues[1] + clock(m.groupValues[2], m.groupValues[3]) }
        out = out.replace(length) { m -> (value(m.groupValues[1]) ?: return@replace m.value).toString() }
        return out
    }

    private fun clock(hourWord: String, minuteWord: String): String {
        val h = value(hourWord) ?: return hourWord
        if (minuteWord.isBlank()) return h.toString()
        val min = value(minuteWord.replace(Regex("^o(?:h)?[\\s-]+", RegexOption.IGNORE_CASE), "")) ?: return "$hourWord $minuteWord"
        return String.format(java.util.Locale.ROOT, "%d:%02d", h, min)
    }

    private fun value(words: String): Int? {
        val parts = words.lowercase().split(Regex("[\\s-]+")).filter { it.isNotEmpty() }
        return when (parts.size) {
            1 -> units[parts[0]] ?: tens[parts[0]]
            2 -> (tens[parts[0]] ?: return null) + (units[parts[1]]?.takeIf { it < 10 } ?: return null)
            else -> null
        }
    }
}
