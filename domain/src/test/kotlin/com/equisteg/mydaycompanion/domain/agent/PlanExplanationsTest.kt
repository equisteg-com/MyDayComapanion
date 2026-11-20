package com.equisteg.mydaycompanion.domain.agent

import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.PlanIssue
import com.equisteg.mydaycompanion.domain.model.PlanIssueType
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PlanExplanationsTest {
    private val day = LocalDate.of(2026,11,21)
    private val at = LocalTime.of(10,0)
    @Test fun `explanations are recognized as read only voice or text commands`() {
        val parser=AgentCommandParser()
        assertEquals(AgentCommand.ExplainPlan, parser.parse("explain my plan",day,at))
        assertEquals(AgentCommand.ExplainPlan, parser.parse("why did you schedule my work?",day,at))
        assertEquals(AgentCommand.ExplainConflicts, parser.parse("show conflicts",day,at))
        assertEquals(AgentCommand.ExplainConflicts, parser.parse("resolve scheduling issues",day,at))
    }

    @Test fun `explanation uses real plan and selected buffer not an invented reason`() {
        val plan=DayPlan(day,listOf(
            PlanBlock("meeting","Meeting",BlockKind.FIXED_EVENT,at,LocalTime.of(10,30)),
            PlanBlock("work","Work",BlockKind.TASK,LocalTime.of(11,0),LocalTime.of(12,0))),
            emptyList(),emptyList(),"planner/4")
        val explanation=AgentReplies.explainPlan(plan,UserPreferences(bufferMinutes=15))
        assertContains(explanation,"1 task blocks")
        assertContains(explanation,"1 fixed events")
        assertContains(explanation,"15-minute buffer")
        assertContains(explanation,"not guaranteed")
    }

    @Test fun `conflicts report actual warnings and safe alternatives`() {
        val plan=DayPlan(day,emptyList(),listOf("u"),
            listOf(PlanIssue(PlanIssueType.OVERLOADED,"u","Not enough time for this task.")),
            "planner/4")
        val explanation=AgentReplies.explainConflicts(plan)
        assertContains(explanation,"1 unscheduled task")
        assertContains(explanation,"Not enough time")
        assertContains(explanation,"Fixed events are not moved automatically")
    }

    @Test fun `empty plans are explained without fabrication`() {
        assertContains(AgentReplies.explainPlan(null,UserPreferences()),"no saved plan")
        assertContains(AgentReplies.explainConflicts(null),"no saved plan")
    }
}
