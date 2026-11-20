package com.equisteg.mydaycompanion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.productivity.BatchEditAction
import com.equisteg.mydaycompanion.ui.screens.CaptureTemplateGallery
import com.equisteg.mydaycompanion.ui.screens.MonthCalendar
import com.equisteg.mydaycompanion.ui.screens.TaskDiscovery
import com.equisteg.mydaycompanion.ui.screens.VisualRescheduler
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PremiumWorkflowUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 11, 21)
    private val created = Instant.parse("2026-11-21T00:00:00Z")
    private fun task(id:String, personal:Boolean=false) =
        Task(id, "Task $id", date=day, createdAt=created, personal=personal)

    @Test fun `month view changes month and navigates to selected day`() {
        var selected:LocalDate?=null
        var month:YearMonth?=null
        compose.setContent { MyDayCompanionTheme {
            MonthCalendar(YearMonth.of(2026,11),day,day,listOf(task("a")),
                onSelectDate={selected=it},onMonthChange={month=it})
        } }
        compose.onNodeWithTag("month-day-2026-11-22").performClick()
        assertEquals(LocalDate.of(2026,11,22),selected)
        compose.onNodeWithTag("month-next").performClick()
        assertEquals(YearMonth.of(2026,12),month)
    }

    @Test fun `templates populate an editable prompt without committing data`() {
        var prompt:String?=null
        compose.setContent { MyDayCompanionTheme {
            CaptureTemplateGallery(enabled=true,onChoose={prompt=it})
        } }
        compose.onNodeWithTag("template-daily").performClick()
        assertNotNull(prompt)
        kotlin.test.assertTrue(prompt!!.contains("review inbox"))
    }

    @Test fun `bulk editor requires selection and explicit confirmation`() {
        var chosen:Pair<Set<String>,BatchEditAction>?=null
        compose.setContent { MyDayCompanionTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TaskDiscovery(listOf(task("a"),task("private",personal=true)),
                    onToggle={},onEdit={},onBatch={ids,action->chosen=ids to action})
            }
        } }
        compose.onNodeWithTag("task-search-toggle").performClick()
        compose.onNodeWithTag("batch-mode").performScrollTo().performClick()
        compose.onNodeWithTag("batch-select-private").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("batch-select-a").performScrollTo().performClick()
        compose.onNodeWithTag("batch-action-COMPLETE").performScrollTo().performClick()
        assertEquals(null,chosen)
        compose.onNodeWithTag("batch-confirm").performClick()
        assertEquals(setOf("a") to BatchEditAction.COMPLETE,chosen)
    }

    @Test fun `accessible reschedule arrows really change preferred start`() {
        val task=task("t")
        val plan=DayPlan(day, listOf(PlanBlock("t","Task t",BlockKind.TASK,
            LocalTime.of(9,0),LocalTime.of(9,30))), emptyList(),emptyList(),"test")
        var updated:Task?=null
        compose.setContent { MyDayCompanionTheme {
            VisualRescheduler(plan,listOf(task),UserPreferences(),busy=false,
                onMove={_,next->updated=next})
        } }
        compose.onNodeWithTag("shift-later-t").performClick()
        assertEquals(LocalTime.of(9,15),updated?.preferredStart)
        assertEquals(day,updated?.date)
    }
}
