package com.equisteg.mydaycompanion.domain.ai.text

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeParserTest {
    private fun first(text: String, now: LocalTime? = null) = TimeParser.findAll(text, now).first()
    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun `clock times in every common format and position`() {
        mapOf(
            "gym 6am" to "06:00", "5pm call mom" to "17:00", "call mom 5 pm" to "17:00", "call 5p" to "17:00",
            "meet at 5 p.m." to "17:00", "standup 17:30" to "17:30", "client call 11.30" to "11:30", "sync 2.30pm" to "14:30",
            "at 1730 team sync" to "17:30", "around 4ish" to "16:00", "@5 call" to "17:00", "at 7 o'clock" to "07:00",
            "half past 7 dinner" to "19:30", "quarter past 9" to "09:15", "quarter to 8" to "07:45", "10 to 8 bus" to "07:50",
            "at 9 tonight" to "21:00", "at 7 in the evening" to "19:00", "at 8 in the morning" to "08:00", "lunch at noon" to "12:00",
        ).forEach { (text, want) -> assertEquals(t(want), first(text).start, text) }
    }

    @Test
    fun `bare hours follow the day plan rule and evening context`() {
        assertEquals(t("15:00"), first("meeting at 3").start)
        assertEquals(t("11:00"), first("meeting at 11").start)
        assertEquals(t("20:00"), first("dinner at 8").start)
        assertEquals(t("20:00"), first("movie night at 8").start)
        assertEquals(t("08:00"), first("breakfast at 8").start)
        assertTrue(first("call at 7").assumedHalf)
    }

    @Test
    fun `ranges give a start and an end`() {
        first("dentist 3-4pm").let { assertEquals(t("15:00"), it.start); assertEquals(t("16:00"), it.end) }
        first("lunch 1pm-2:30pm").let { assertEquals(t("13:00"), it.start); assertEquals(t("14:30"), it.end) }
        first("meeting between 3 and 4").let { assertEquals(t("15:00"), it.start); assertEquals(t("16:00"), it.end) }
        first("office from 9 to 5").let { assertEquals(t("09:00"), it.start); assertEquals(t("17:00"), it.end) }
        first("workshop 11-1pm").let { assertEquals(t("11:00"), it.start); assertEquals(t("13:00"), it.end) }
        first("lunch 1 to 2").let { assertEquals(t("13:00"), it.start); assertEquals(t("14:00"), it.end) }
    }

    @Test
    fun `deadlines, after and soft times have their own roles`() {
        assertEquals(TimeRole.DEADLINE to t("17:00"), first("send it by 5pm").let { it.role to it.start })
        assertEquals(TimeRole.DEADLINE to t("12:00"), first("before lunch").let { it.role to it.start })
        assertEquals(TimeRole.DEADLINE to t("18:00"), first("by end of day").let { it.role to it.start })
        assertEquals(TimeRole.AFTER to t("18:00"), first("after work").let { it.role to it.start })
        assertEquals(TimeRole.AFTER to t("18:00"), first("after 6").let { it.role to it.start })
        assertEquals(TimeRole.SOFT to t("09:00"), first("in the morning").let { it.role to it.start })
        assertEquals(TimeRole.SOFT to t("07:00"), first("early morning").let { it.role to it.start })
    }

    @Test
    fun `relative times need the current time`() {
        assertEquals(t("09:05"), first("call in 2 hours", LocalTime.of(7, 2)).start)
        assertEquals(t("07:30"), first("in 30 min", LocalTime.of(7, 0)).start)
        assertTrue(TimeParser.findAll("call in 2 hours").none { it.role == TimeRole.AT })
    }

    @Test
    fun `numbers that are not times are left alone`() {
        listOf("read 3 chapters", "read pages 3-4", "pay rs 12.50", "buy 2 apples", "version 2.75", "task number 5").forEach {
            assertTrue(TimeParser.findAll(it).isEmpty(), it)
        }
    }
}
