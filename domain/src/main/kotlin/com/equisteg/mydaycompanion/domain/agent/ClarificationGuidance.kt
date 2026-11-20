package com.equisteg.mydaycompanion.domain.agent

import java.util.Locale

/**
 * Plain-language recovery hints for unclear input. Deterministic and entirely offline:
 * the user's raw text is never sent to a service or written to the audit log.
 */
object ClarificationGuidance {
    fun forInput(input: String): String {
        val text = input.trim().lowercase(Locale.ROOT)
        return when {
            text.startsWith("move ") || text.startsWith("postpone ") ||
                text.startsWith("reschedule ") || text.startsWith("shift ") ->
                "Tell me the exact saved task and its new date or time. Example: Move the report to Friday at 4pm."
            text.contains("meeting") || text.contains("appointment") ->
                "For an appointment, include a start time and duration. Example: Dentist Friday at 3pm for 45 minutes."
            text.any(Char::isDigit) ->
                "If I misread the time, spell it out clearly. Example: Call Sam tomorrow at 4pm for 15 minutes."
            else ->
                "Name the task, day and approximate duration. Example: Finish my report tomorrow for 45 minutes."
        }
    }
}
