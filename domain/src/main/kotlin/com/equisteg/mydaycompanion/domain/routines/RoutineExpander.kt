package com.equisteg.mydaycompanion.domain.routines

import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.tools.IdGenerator
import java.time.Instant
import java.time.LocalDate

/**
 * Turns routines into tasks for one day. Pure: the caller stores the result. A routine produces at
 * most one task per day, so running it again (app start, boot, the daily alarm) adds nothing twice.
 * A routine task the owner removes is kept as CANCELLED ("skip today"), so it still counts and is
 * not added again. A plain task with the same title on that day also counts: when the owner says
 * "gym every weekday", the first one is saved as a task and the routine takes over from the next day.
 */
object RoutineExpander {
    fun tasksFor(date: LocalDate, routines: List<Routine>, existing: List<Task>, ids: IdGenerator, now: Instant): List<Task> {
        val onDay = existing.filter { it.date == date }
        val already = onDay.mapNotNull { it.routineId }.toSet()
        val titles = onDay.map { it.title.lowercase() }.toSet()
        return routines
            .filter { it.isDueOn(date) && it.id !in already && it.title.lowercase() !in titles }
            .map {
                Task(
                    id = ids.newId(),
                    title = it.title,
                    priority = Priority.MEDIUM,
                    durationMinutes = it.durationMinutes,
                    date = date,
                    createdAt = now,
                    preferredStart = it.preferredStart,
                    routineId = it.id,
                )
            }
    }
}
