package com.equisteg.mydaycompanion

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.equisteg.mydaycompanion.domain.reminders.Reminder
import com.equisteg.mydaycompanion.domain.reminders.ReminderKind
import com.equisteg.mydaycompanion.reminders.AlarmNotificationScheduler
import com.equisteg.mydaycompanion.reminders.ReminderReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** Registered alarms, including snoozes, are all cancelled by a sync or a wipe; nothing fires for deleted data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderAlarmsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val clock = Clock.fixed(Instant.parse("2026-10-06T07:00:00Z"), ZoneOffset.UTC)
    private val scheduler = AlarmNotificationScheduler(context, clock)

    private fun reminder(id: String, at: String) =
        Reminder(id, ReminderKind.TASK, Instant.parse(at), "Gym", "Planned for 10:00", taskId = id.removePrefix("task:").substringBefore(':'))

    @Test
    fun `a new sync replaces the old reminders and keeps the nightly alarm`() = runBlocking {
        assertEquals(2, scheduler.replaceAll(listOf(reminder("task:a:2026-10-06", "2026-10-06T09:50:00Z"), reminder("task:b:2026-10-06", "2026-10-06T10:50:00Z"))))
        assertEquals(3, alarms.scheduledAlarms.size, "two reminders and the nightly run")
        scheduler.replaceAll(listOf(reminder("task:b:2026-10-06", "2026-10-06T10:50:00Z")))
        assertEquals(2, alarms.scheduledAlarms.size, "the removed reminder is cancelled, not left behind")
    }

    @Test
    fun `a wipe cancels reminders, snoozes and the nightly alarm`() = runBlocking {
        scheduler.replaceAll(listOf(reminder("task:a:2026-10-06", "2026-10-06T09:50:00Z")))
        val shown = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SHOW)
            .setData(Uri.parse("mdc-reminder:task:a:2026-10-06"))
            .putExtra(ReminderReceiver.EXTRA_KIND, "TASK").putExtra(ReminderReceiver.EXTRA_TITLE, "Gym").putExtra(ReminderReceiver.EXTRA_TASK_ID, "a")
        AlarmNotificationScheduler.snooze(context, shown, Instant.parse("2026-10-06T10:00:00Z").toEpochMilli())
        assertEquals(3, alarms.scheduledAlarms.size)

        scheduler.cancelEverything()
        assertEquals(0, alarms.scheduledAlarms.size, "nothing left that could show a deleted task's title")
    }
}
