package com.equisteg.mydaycompanion.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.equisteg.mydaycompanion.MainActivity
import com.equisteg.mydaycompanion.MyDayCompanionApp
import com.equisteg.mydaycompanion.R
import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.reminders.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Not exported: only this app's own alarms and notification buttons, and the system's restart and
 * clock-change broadcasts, can reach it. It shows reminders, handles the Done button and runs the
 * daily maintenance. Every change it makes goes through the orchestrator like any other.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MyDayCompanionApp
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_SHOW -> show(context, app, intent)
                    ACTION_SNOOZE -> snooze(context, intent)
                    ACTION_DONE -> done(context, app, intent.getStringExtra(EXTRA_TASK_ID), intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                    ACTION_MAINTENANCE,
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    -> app.services.maintenance.run()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun show(context: Context, app: MyDayCompanionApp, intent: Intent) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { k -> ReminderKind.entries.firstOrNull { it.name == k } } ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        // A task finished, skipped or deleted (including by a wipe) since the alarm was set is not reminded.
        if (kind == ReminderKind.TASK && (taskId == null || app.services.tasks.get(taskId)?.status != TaskStatus.OPEN)) return
        val id = intent.dataString.hashCode()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, if (kind == ReminderKind.BRIEF) CHANNEL_BRIEF else CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // On a locked screen only "MyDayCompanion: Reminder" shows, never the title.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_REMINDERS).setSmallIcon(R.drawable.ic_notification).setContentTitle(PUBLIC_TITLE).build(),
            )
        if (kind == ReminderKind.TASK && taskId != null) {
            val done = PendingIntent.getBroadcast(
                context, id,
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE).setData(intent.data)
                    .putExtra(EXTRA_TASK_ID, taskId).putExtra(EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Done", done)
        }
        if (kind != ReminderKind.BRIEF) {
            val snooze = PendingIntent.getBroadcast(
                context, id,
                Intent(intent).setAction(ACTION_SNOOZE).putExtra(EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "In $SNOOZE_MINUTES min", snooze)
        }
        @Suppress("MissingPermission") // checked in canNotify
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /** The owner tapped "In 10 min": the same reminder shows again later, registered so a wipe cancels it. */
    private fun snooze(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        AlarmNotificationScheduler.snooze(context, intent, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
    }

    /** The owner tapped Done. A personal task still needs the owner's verification, so that one opens the app instead. */
    private suspend fun done(context: Context, app: MyDayCompanionApp, taskId: String?, notificationId: Int) {
        taskId ?: return
        val action = ProposedAction("tasks.complete", JsonObject(mapOf("taskId" to JsonPrimitive(taskId))))
        when (app.services.orchestrator.execute(action, Requester.USER)) {
            is ExecutionResult.Done -> {
                NotificationManagerCompat.from(context).cancel(notificationId)
                app.services.maintenance.syncReminders()
            }
            is ExecutionResult.NeedsConfirmation -> if (canNotify(context)) {
                @Suppress("MissingPermission")
                NotificationManagerCompat.from(context).notify(
                    notificationId,
                    NotificationCompat.Builder(context, CHANNEL_REMINDERS).setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("Open the app to finish this task")
                        .setContentText("Personal tasks need your fingerprint, face or PIN.")
                        .setContentIntent(
                            PendingIntent.getActivity(
                                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                        .setAutoCancel(true).build(),
                )
            }
            else -> Unit
        }
    }

    companion object {
        const val ACTION_SHOW = "com.equisteg.mydaycompanion.action.SHOW_REMINDER"
        const val ACTION_DONE = "com.equisteg.mydaycompanion.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.equisteg.mydaycompanion.action.REMINDER_SNOOZE"
        const val SNOOZE_MINUTES = 10
        const val ACTION_MAINTENANCE = "com.equisteg.mydaycompanion.action.DAILY_MAINTENANCE"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_BRIEF = "brief"
        const val PUBLIC_TITLE = "Reminder"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun canNotify(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_BRIEF, "Morning brief", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
