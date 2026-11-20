package com.equisteg.mydaycompanion.domain.productivity

import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalTime

/** Pure, guarded local rescheduling used by drag gestures and keyboard/TalkBack controls. */
object TimeShift {
    const val STEP_MINUTES = 15

    /**
     * Changes a SOFT preferred start; the planner will resolve any conflicts after the write.
     * Fixed events cannot be passed here. Private/routine task changes require individual editing.
     */
    fun change(
        task: Task,
        scheduledStart: LocalTime,
        steps: Int,
        dayStart: LocalTime,
        dayEnd: LocalTime,
    ): Result<Task> = runCatching {
        require(steps != 0 && steps in -48..48) { "Choose a shift between -12 and +12 hours." }
        require(task.date != null && task.status == TaskStatus.OPEN &&
            !task.personal && task.routineId == null) {
            "This item needs individual review, or cannot be rescheduled."
        }
        val firstMinute = dayStart.toSecondOfDay() / 60
        val lastMinute = dayEnd.toSecondOfDay() / 60 - task.durationMinutes
        require(firstMinute <= lastMinute) { "This task cannot fit inside the configured day window." }
        val base = scheduledStart.toSecondOfDay() / 60
        val target = (base.toLong() + steps.toLong() * STEP_MINUTES)
            .coerceIn(firstMinute.toLong(), lastMinute.toLong()).toInt()
        require(target != base) { "No room to move further within the configured day." }
        task.copy(preferredStart = LocalTime.of(target / 60, target % 60))
    }
}
