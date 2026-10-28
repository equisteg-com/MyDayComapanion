package com.equisteg.mydaycompanion.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.equisteg.mydaycompanion.domain.future.CalendarProvider
import com.equisteg.mydaycompanion.domain.model.EventSource
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Reads the owner's calendar events for one day, read-only (build doc F7). Returns nothing unless
 * [enabled] says the owner switched it on and READ_CALENDAR is granted right now. All-day events,
 * events the owner declined and events from hidden calendars are left out. Event titles are data:
 * one that looks like an instruction to the assistant is shown as "Calendar event".
 */
class ContentResolverCalendarProvider(
    private val context: Context,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val enabled: () -> Boolean,
) : CalendarProvider {
    override suspend fun eventsOn(date: LocalDate): List<FixedEvent> {
        if (!enabled() || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) { runCatching { query(date) }.getOrDefault(emptyList()) }
    }

    private fun query(date: LocalDate): List<FixedEvent> {
        val z = zone()
        val dayStart = date.atStartOfDay(z).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(z).toInstant()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(dayStart.toEpochMilli().toString())
            .appendPath(dayEnd.toEpochMilli().toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.VISIBLE,
        )
        val out = mutableListOf<FixedEvent>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < MAX_EVENTS) {
                if (c.getInt(4) == 1) continue
                if (c.getInt(5) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                if (c.getInt(6) == 0) continue
                val begin = Instant.ofEpochMilli(c.getLong(2)).coerceAtLeast(dayStart)
                val end = Instant.ofEpochMilli(c.getLong(3)).coerceAtMost(dayEnd.minusSeconds(60))
                val minutes = ChronoUnit.MINUTES.between(begin, end).toInt()
                if (minutes < Task.MIN_DURATION_MINUTES) continue
                val start: LocalTime = begin.atZone(z).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                out += FixedEvent(
                    id = "cal:${c.getLong(0)}:$date",
                    title = cleanTitle(c.getString(1)),
                    date = date,
                    start = start,
                    durationMinutes = minutes.coerceAtMost(Task.MAX_DURATION_MINUTES),
                    source = EventSource.CALENDAR,
                )
            }
        }
        return out
    }

    private fun cleanTitle(raw: String?): String {
        val t = raw?.trim()?.replace(Regex("\\s+"), " ")?.take(Task.MAX_TITLE_LENGTH).orEmpty()
        return when {
            t.isEmpty() -> "Busy"
            ContentSafety.looksLikeInstructionToAssistant(t) -> "Calendar event"
            else -> t
        }
    }

    private companion object {
        const val MAX_EVENTS = 30
    }
}
