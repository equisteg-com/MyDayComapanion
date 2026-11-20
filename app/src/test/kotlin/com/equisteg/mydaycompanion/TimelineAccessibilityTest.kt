package com.equisteg.mydaycompanion

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.ui.screens.Timeline
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TimelineAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    private val plan = DayPlan(
        LocalDate.of(2026, 11, 20),
        listOf(PlanBlock("a", "Write report", BlockKind.TASK, LocalTime.of(10, 0), LocalTime.of(10, 30))),
        emptyList(),
        emptyList(),
        "test",
    )

    @Test
    fun `timeline completion checkbox explains its action to screen readers`() {
        var toggled: String? = null
        compose.setContent {
            MyDayCompanionTheme {
                Timeline(plan, emptyMap(), onToggle = { toggled = it }, tag = "timeline")
            }
        }
        compose.onNodeWithTag("timeline-toggle-a", useUnmergedTree = true)
            .assertContentDescriptionEquals("Mark Write report as done")
            .performClick()
        assertEquals("a", toggled)
    }

    @Test
    fun `timeline completion button describes the reverse action`() {
        compose.setContent {
            MyDayCompanionTheme {
                Timeline(plan, mapOf("a" to TaskStatus.DONE), onToggle = {}, tag = "timeline")
            }
        }
        compose.onNodeWithTag("timeline-toggle-a", useUnmergedTree = true)
            .assertExists()
            .assertContentDescriptionEquals("Mark Write report as not done")
    }
}
