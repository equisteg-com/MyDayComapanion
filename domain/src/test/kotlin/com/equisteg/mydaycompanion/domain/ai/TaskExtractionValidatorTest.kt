package com.equisteg.mydaycompanion.domain.ai

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertIs

class TaskExtractionValidatorTest {
    private val today = LocalDate.of(2026, 10, 6)

    private fun check(raw: String) = TaskExtractionValidator.validate(raw, today)

    @Test
    fun `accepts well formed output`() {
        assertIs<ValidationOutcome.Valid<TaskExtraction>>(
            check("""{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"Call Raj","priority":"HIGH","durationMinutes":15}],"fixedEvents":[]}"""),
        )
    }

    @Test
    fun `accepts items on several days with soft times, repeats and reminders`() {
        val v = check(
            """{"version":2,"targetDate":"2026-10-06","tasks":[{"title":"Run","priority":"MEDIUM","durationMinutes":30,"date":"2026-10-07",""" +
                """"preferredStart":"07:00","repeat":"WEEKDAYS","remindAt":"06:50"}],""" +
                """"fixedEvents":[{"title":"Dentist","date":"2026-10-09","start":"16:00","durationMinutes":60}]}""",
        )
        assertIs<ValidationOutcome.Valid<TaskExtraction>>(v)
        kotlin.test.assertEquals(listOf(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9)), v.value.dates)
    }

    @Test
    fun `rejects malformed, truncated or non json output`() {
        listOf("", "not json", """{"version":2,"targetDate":"2026-10-07","tasks":[""", "[]", "null").forEach {
            assertIs<ValidationOutcome.Invalid>(check(it), it)
        }
    }

    @Test
    fun `rejects unknown fields so models cannot smuggle tool calls`() {
        assertIs<ValidationOutcome.Invalid>(
            check("""{"version":2,"targetDate":"2026-10-07","tasks":[],"tool_call":{"id":"data.deleteAll"}}"""),
        )
    }

    @Test
    fun `rejects out of range values`() {
        listOf(
            """{"version":1,"targetDate":"2026-10-07"}""",
            """{"version":3,"targetDate":"2026-10-07"}""",
            """{"version":2,"targetDate":"2020-01-01"}""",
            """{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"x","priority":"HIGH","durationMinutes":100000}]}""",
            """{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"x","priority":"SUPER","durationMinutes":10}]}""",
            """{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"","priority":"LOW","durationMinutes":10}]}""",
            """{"version":2,"targetDate":"2026-10-07","fixedEvents":[{"title":"m","start":"25:00","durationMinutes":10}]}""",
            """{"version":2,"targetDate":"2026-10-07","fixedEvents":[{"title":"late","start":"23:30","durationMinutes":60}]}""",
            """{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"x","priority":"LOW","durationMinutes":10,"date":"2026-10-01"}]}""",
            """{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"x","priority":"LOW","durationMinutes":10,"date":"2030-01-01"}]}""",
            """{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"x","priority":"LOW","durationMinutes":10,"preferredStart":"9am"}]}""",
            """{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"x","priority":"LOW","durationMinutes":10,"repeat":"HOURLY"}]}""",
            """{"version":2,"targetDate":"2026-10-07","tasks":[{"title":"x","priority":"LOW","durationMinutes":10,"remindAt":"soon"}]}""",
            """{"version":2,"targetDate":"2026-10-07","fixedEvents":[{"title":"m","date":"yesterday","start":"10:00","durationMinutes":10}]}""",
        ).forEach { assertIs<ValidationOutcome.Invalid>(check(it), it) }
    }

    @Test
    fun `rejects oversized output`() {
        assertIs<ValidationOutcome.Invalid>(check("x".repeat(TaskExtractionValidator.MAX_RAW_CHARS + 1)))
    }
}
