package com.equisteg.mydaycompanion

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.domain.widget.WidgetLine
import com.equisteg.mydaycompanion.domain.widget.WidgetSummary
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.screens.SettingsScreen
import com.equisteg.mydaycompanion.widget.TodayWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class WidgetUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val graph = TestGraph()
    private val services = TestServices(graph)

    private fun text(root: View, id: Int) = root.findViewById<TextView>(id)

    @Test
    fun `the widget reads today's plan and hides names until the owner allows them`() = runBlocking {
        val now = graph.clock.instant()
        val today = LocalDate.now(graph.clock)
        graph.tasks.upsert(listOf(Task("t1", "Call Raj", createdAt = now), Task("t2", "Pick up medicine", createdAt = now, personal = true)))
        graph.plans.save(
            DayPlan(
                today,
                listOf(PlanBlock("t1", "Call Raj", BlockKind.TASK, LocalTime.of(16, 0), LocalTime.of(16, 15)), PlanBlock("t2", "Pick up medicine", BlockKind.TASK, LocalTime.of(17, 0), LocalTime.of(17, 30))),
                emptyList(),
                emptyList(),
                "test",
            ),
        )
        assertEquals(listOf("Task", "Personal task"), TodayWidgetProvider.load(services).lines.map { it.text })
        services.deviceSettings.widgetTitles = true
        assertEquals(listOf("Call Raj", "Personal task"), TodayWidgetProvider.load(services).lines.map { it.text })
    }

    @Test
    fun `the widget shows the headline and up to three rows, or only Open after a wipe`() {
        val summary = WidgetSummary("2 left today · next at 16:00", listOf(WidgetLine(LocalTime.of(16, 0), "Task"), WidgetLine(LocalTime.of(17, 0), "Event")))
        val shown = TodayWidgetProvider.render(context, summary).apply(context, FrameLayout(context))
        assertEquals("2 left today · next at 16:00", text(shown, R.id.widget_headline).text.toString())
        assertEquals("16:00", text(shown, R.id.widget_time_1).text.toString())
        assertEquals("Event", text(shown, R.id.widget_line_2).text.toString())
        assertEquals(View.GONE, text(shown, R.id.widget_line_3).visibility)

        val empty = TodayWidgetProvider.render(context, null).apply(context, FrameLayout(context))
        assertEquals("Open MyDayCompanion", text(empty, R.id.widget_headline).text.toString())
        assertEquals(View.GONE, text(empty, R.id.widget_line_1).visibility)
    }

    @Test
    fun `the names switch is off by default and redraws the widget`() {
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { SettingsScreen(vm) }
        assertFalse(services.deviceSettings.widgetTitles)
        val before = services.widgetRefreshes
        compose.onNodeWithTag("widget-titles").performScrollTo().performClick().assertIsOn()
        assertTrue(services.deviceSettings.widgetTitles)
        assertTrue(services.widgetRefreshes > before)

        val beforeReplan = services.widgetRefreshes
        vm.onReplan()
        compose.waitUntil(5_000) { services.widgetRefreshes > beforeReplan }
    }
}
