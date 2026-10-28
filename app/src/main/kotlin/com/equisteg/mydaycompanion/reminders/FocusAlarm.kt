package com.equisteg.mydaycompanion.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * The end of a focus session, shown even when the app is closed (OQ-27). One alarm at a time;
 * it carries no task name, so the notification only says that focus time is up. Like reminders it
 * is inexact but allowed in Doze, so it needs no exact-alarm permission and may be a minute or two late.
 */
object FocusAlarm {
    private const val REQUEST_CODE = 2

    /** Sets the alarm for [atMillis], or cancels it when null. */
    fun schedule(context: Context, atMillis: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        intent(context, PendingIntent.FLAG_NO_CREATE)?.let { pi ->
            alarms.cancel(pi)
            pi.cancel()
        }
        // Cancelled from the app: an earlier "Focus time is up" is out of date too.
        if (atMillis == null) androidx.core.app.NotificationManagerCompat.from(context).cancel(ReminderReceiver.FOCUS_NOTIFICATION_ID)
        if (atMillis != null) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent(context, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        }
    }

    private fun intent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FOCUS_END),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )
}
