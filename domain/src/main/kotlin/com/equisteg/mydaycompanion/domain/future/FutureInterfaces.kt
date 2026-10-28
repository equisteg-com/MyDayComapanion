package com.equisteg.mydaycompanion.domain.future

import com.equisteg.mydaycompanion.domain.model.FixedEvent
import java.time.Instant
import java.time.LocalDate

/*
 * Extension points required by the build documents (section 13 / 36). They exist so future work
 * does not force an architecture rewrite. V1 ships only the Disabled implementations below for the
 * cloud-facing ones; nothing here may become a V1 runtime dependency.
 */

/** Reads fixed events from the device calendar. Phase 1, behind the calendar_read flag + READ_CALENDAR. */
interface CalendarProvider {
    suspend fun eventsOn(date: LocalDate): List<FixedEvent>
}

/** Speech in/out. Phase 1, behind the voice flag. */
interface VoiceProvider {
    fun isRecognitionAvailable(): Boolean
    fun isSynthesisAvailable(): Boolean
    suspend fun speak(text: String)
}

/** Local reminders. Phase 1, behind the reminders flag + POST_NOTIFICATIONS. */
interface NotificationScheduler {
    suspend fun schedule(reminderId: String, at: Instant, title: String): Boolean
    suspend fun cancel(reminderId: String)
    suspend fun cancelAll()
}

/** Future cross-device sync. Locked off in V1. */
interface SyncProvider {
    val enabled: Boolean
}

/** Future optional backend. Locked off in V1. */
interface CloudBackend {
    val enabled: Boolean
}

/** Future third-party integration (email, messaging, ...). Locked off in V1. */
interface ExternalIntegration {
    val id: String
    val enabled: Boolean
}

object DisabledSyncProvider : SyncProvider {
    override val enabled = false
}

object DisabledCloudBackend : CloudBackend {
    override val enabled = false
}

object NoCalendarProvider : CalendarProvider {
    override suspend fun eventsOn(date: LocalDate): List<FixedEvent> = emptyList()
}

object NoVoiceProvider : VoiceProvider {
    override fun isRecognitionAvailable() = false
    override fun isSynthesisAvailable() = false
    override suspend fun speak(text: String) = Unit
}

object NoNotificationScheduler : NotificationScheduler {
    override suspend fun schedule(reminderId: String, at: Instant, title: String) = false
    override suspend fun cancel(reminderId: String) = Unit
    override suspend fun cancelAll() = Unit
}
