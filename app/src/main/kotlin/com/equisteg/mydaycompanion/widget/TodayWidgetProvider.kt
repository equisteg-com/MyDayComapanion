package com.equisteg.mydaycompanion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.equisteg.mydaycompanion.AppServices
import com.equisteg.mydaycompanion.MainActivity
import com.equisteg.mydaycompanion.MyDayCompanionApp
import com.equisteg.mydaycompanion.R
import com.equisteg.mydaycompanion.domain.widget.TodayWidget
import com.equisteg.mydaycompanion.domain.widget.WidgetSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The home-screen widget (F19): what is left today, and a button that opens the app to add more.
 * It only reads; every change still happens in the app, through the orchestrator. Built with
 * RemoteViews, so it needs no extra library.
 */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val started = wipes.get()
        scope.launch {
            try {
                // A locked Keystore, a missing database or an unexpected Application must never crash the home screen.
                val loaded = runCatching {
                    if (wiping.get()) null else render(context, load((context.applicationContext as MyDayCompanionApp).services))
                }.getOrNull()
                // A read that overlapped "Delete everything" must not draw the old day back.
                // The check and the draw share one lock with the wipe's clear(), so a clear is never overwritten.
                synchronized(drawLock) {
                    val views = if (loaded == null || wiping.get() || wipes.get() != started) render(context, null) else loaded
                    ids.forEach { manager.updateAppWidget(it, views) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val wiping = AtomicBoolean(false)
        private val wipes = AtomicInteger(0)
        private val drawLock = Any()
        private val rows = listOf(
            R.id.widget_line_1 to R.id.widget_time_1,
            R.id.widget_line_2 to R.id.widget_time_2,
            R.id.widget_line_3 to R.id.widget_time_3,
        )

        suspend fun load(services: AppServices): WidgetSummary {
            val today = LocalDate.now(services.clock)
            val plan = services.plans.get(today)
            val tasks = plan?.blocks.orEmpty().mapNotNull { services.tasks.get(it.refId) }
            val now = LocalTime.now(services.clock).truncatedTo(ChronoUnit.MINUTES)
            return TodayWidget.summarize(plan, tasks, now, services.deviceSettings.widgetTitles)
        }

        /** [summary] null shows only "Open MyDayCompanion" (after a wipe, or when the data can't be read). */
        fun render(context: Context, summary: WidgetSummary?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_headline, summary?.headline ?: context.getString(R.string.widget_open))
            rows.forEachIndexed { i, (lineId, timeId) ->
                val line = summary?.lines?.getOrNull(i)
                val visibility = if (line == null) View.GONE else View.VISIBLE
                views.setViewVisibility(lineId, visibility)
                views.setViewVisibility(timeId, visibility)
                views.setTextViewText(timeId, line?.time?.toString().orEmpty())
                views.setTextViewText(lineId, line?.text.orEmpty())
            }
            // Both open the app (which asks for the app lock first); the plan's input is at the top of My day.
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_add, open)
            return views
        }

        /** Asks every placed widget to read today again; called when the plan or the widget setting changes. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TodayWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        /**
         * Runs "Delete everything" with the widget held blank: no update draws while [wipe] runs, an
         * update that started before it draws blank, and the widget is cleared afterwards whatever happens.
         */
        fun <T> whileWiping(context: Context, wipe: () -> T): T {
            wiping.set(true)
            wipes.incrementAndGet()
            try {
                return wipe()
            } finally {
                synchronized(drawLock) {
                    runCatching { clear(context) }
                    wiping.set(false)
                }
            }
        }

        /** After "Delete everything": nothing from before may stay on the home screen. */
        fun clear(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, null))
        }
    }
}
