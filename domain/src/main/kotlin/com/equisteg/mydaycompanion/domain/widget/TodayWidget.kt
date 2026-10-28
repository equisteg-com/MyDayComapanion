package com.equisteg.mydaycompanion.domain.widget

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalTime

/** One row of the home-screen widget: a start time and what is due then. */
data class WidgetLine(val time: LocalTime, val text: String)

data class WidgetSummary(val headline: String, val lines: List<WidgetLine>)

/**
 * What the home-screen widget (F19) shows for today. Pure, so the widget and the tests agree.
 *
 * The widget is outside the app lock, so by default it shows times and kinds only ("Task",
 * "Event"); names appear only when the owner turns on [showTitles]. A personal task never shows
 * its name, whatever the setting.
 */
object TodayWidget {
    const val MAX_LINES = 3

    fun summarize(plan: DayPlan?, tasks: List<Task>, now: LocalTime, showTitles: Boolean): WidgetSummary {
        val byId = tasks.associateBy { it.id }
        val upcoming = plan?.blocks.orEmpty()
            .filter { it.end > now }
            .filter { it.kind != BlockKind.TASK || byId[it.refId]?.status != TaskStatus.DONE }
            .sortedBy { it.start }
        if (upcoming.isEmpty()) return WidgetSummary("Nothing else planned today", emptyList())
        val lines = upcoming.take(MAX_LINES).map { block ->
            val personal = block.kind == BlockKind.TASK && byId[block.refId]?.personal == true
            val text = when {
                personal -> "Personal task"
                showTitles -> block.title
                block.kind == BlockKind.FIXED_EVENT -> "Event"
                else -> "Task"
            }
            WidgetLine(block.start, text)
        }
        val next = upcoming.first()
        val headline = if (next.start <= now) {
            "${upcoming.size} left today · now until ${next.end}"
        } else {
            "${upcoming.size} left today · next at ${next.start}"
        }
        return WidgetSummary(headline, lines)
    }
}
