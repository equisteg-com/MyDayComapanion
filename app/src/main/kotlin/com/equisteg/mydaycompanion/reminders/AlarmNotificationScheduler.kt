package com.equisteg.mydaycompanion.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.equisteg.mydaycompanion.domain.future.NotificationScheduler
import com.equisteg.mydaycompanion.domain.reminders.Reminder
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Registers reminders with AlarmManager. Alarms are inexact but allowed in Doze, so they need no
 * exact-alarm permission and may arrive a few minutes late when the phone is idle
 * (docs/prebuild/06-permission-model.md). Android forgets alarms on restart; [ReminderReceiver]
 * re-registers them after boot, an app update or a clock change.
 *
 * Only reminder ids (task ids and dates, never titles) are kept in plain preferences, so a later
 * sync can cancel what it registered before.
 */
class AlarmNotificationScheduler(
    private val context: Context,
    private val clock: Clock,
) : NotificationScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun replaceAll(reminders: List<Reminder>): Int {
        cancelReminders()
        var registered = 0
        for (r in reminders) {
            val pi = PendingIntent.getBroadcast(context, 0, showIntent(r), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at.toEpochMilli(), pi)
            registered++
        }
        prefs.edit { putStringSet(KEY_IDS, reminders.map { it.id }.toSet()) }
        scheduleMaintenance()
        return registered
    }

    override suspend fun cancelAll() = cancelEverything()

    /** Also used by the panic wipe, which cannot suspend. Clears shown notifications too. */
    fun cancelEverything() {
        cancelReminders()
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
        alarms.cancel(maintenanceIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return)
    }

    private fun cancelReminders() {
        for (id in prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()) {
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SHOW).setData(uri(id)),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                alarms.cancel(pi)
                pi.cancel()
            }
        }
        prefs.edit { remove(KEY_IDS) }
    }

    /** Wakes the app a few minutes after midnight to add the new day's routine tasks and reminders. */
    private fun scheduleMaintenance() {
        val next = LocalDate.now(clock).plusDays(1).atTime(LocalTime.of(0, 5)).atZone(clock.zone).toInstant()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), maintenanceIntent(PendingIntent.FLAG_UPDATE_CURRENT)!!)
    }

    private fun maintenanceIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, 1, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_MAINTENANCE), flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showIntent(r: Reminder) = Intent(context, ReminderReceiver::class.java)
        .setAction(ReminderReceiver.ACTION_SHOW)
        .setData(uri(r.id))
        .putExtra(ReminderReceiver.EXTRA_KIND, r.kind.name)
        .putExtra(ReminderReceiver.EXTRA_TITLE, r.title)
        .putExtra(ReminderReceiver.EXTRA_BODY, r.body)
        .putExtra(ReminderReceiver.EXTRA_TASK_ID, r.taskId)

    private fun uri(id: String): Uri = Uri.Builder().scheme("mdc-reminder").opaquePart(id).build()

    private companion object {
        const val PREFS = "reminder_alarms"
        const val KEY_IDS = "ids"
    }
}
