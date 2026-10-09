package com.equisteg.mydaycompanion.domain.ai.text

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DateParserTest {
    private val today = LocalDate.of(2026, 10, 6) // a Tuesday
    private val parser = DateParser()
    private fun date(text: String) = parser.findAll(TextSpans.expandShorthand(text), today).first().date

    @Test
    fun `relative words and common misspellings`() {
        mapOf(
            "today" to "2026-10-06", "tmrw" to "2026-10-07", "tomorow" to "2026-10-07", "2moro" to "2026-10-07",
            "the day after tomorrow" to "2026-10-08", "in 3 days" to "2026-10-09", "in a week" to "2026-10-13",
            "next week" to "2026-10-12", "this weekend" to "2026-10-10",
        ).forEach { (text, want) -> assertEquals(LocalDate.parse(want), date(text), text) }
    }

    @Test
    fun `weekdays resolve forward`() {
        assertEquals(LocalDate.of(2026, 10, 9), date("on friday"))
        assertEquals(LocalDate.of(2026, 10, 9), date("next friday"))
        assertEquals(LocalDate.of(2026, 10, 8), date("thu at 5"))
        assertEquals(LocalDate.of(2026, 10, 13), date("on tuesday"))
        assertEquals(today, date("this tuesday"))
        assertEquals(LocalDate.of(2026, 10, 11), date("on sun"))
        assertTrue(parser.findAll("I sat down in the sun", today).isEmpty())
    }

    @Test
    fun `calendar dates never land in the past`() {
        assertEquals(LocalDate.of(2026, 10, 15), date("oct 15"))
        assertEquals(LocalDate.of(2026, 10, 15), date("15th October"))
        assertEquals(LocalDate.of(2026, 10, 12), date("on the 12th"))
        assertEquals(LocalDate.of(2026, 11, 2), date("on the 2nd"))
        assertEquals(LocalDate.of(2027, 1, 5), date("jan 5"))
        assertEquals(LocalDate.of(2026, 10, 12), date("12/10"))
        assertEquals(LocalDate.of(2026, 12, 10), DateParser(NumericDateOrder.MONTH_DAY).findAll("12/10", today).first().date)
        assertEquals(LocalDate.of(2026, 11, 3), date("2026-11-03"))
    }
}
