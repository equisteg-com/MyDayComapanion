package com.equisteg.mydaycompanion.domain.model

import java.time.LocalDate
import java.time.LocalTime

enum class BlockKind { TASK, FIXED_EVENT }

data class PlanBlock(
    val refId: String,
    val title: String,
    val kind: BlockKind,
    val start: LocalTime,
    val end: LocalTime,
)

enum class PlanIssueType { OVERLOADED, DEADLINE_AT_RISK, FIXED_EVENT_CONFLICT, OUTSIDE_DAY_WINDOW }

data class PlanIssue(val type: PlanIssueType, val refId: String?, val message: String)

data class DayPlan(
    val date: LocalDate,
    val blocks: List<PlanBlock>,
    val unscheduledTaskIds: List<String>,
    val issues: List<PlanIssue>,
    val plannerVersion: String,
) {
    val isRealistic: Boolean get() = unscheduledTaskIds.isEmpty() && issues.none { it.type == PlanIssueType.FIXED_EVENT_CONFLICT }
}
