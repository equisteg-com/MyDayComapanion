package com.equisteg.mydaycompanion

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.screens.PlanScreen
import com.equisteg.mydaycompanion.ui.screens.SettingsScreen
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme
import com.equisteg.mydaycompanion.ui.theme.ThemeMode
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
import kotlin.test.assertTrue

/** UPD-010: the redesigned My day (progress, Now / Next, weekly insights) and the Appearance settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class NewLookUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val graph = TestGraph()
    private val services = TestServices(graph)

    @Test
    fun `My day greets, shows progress, what's on now and next, and the week`() {
        runBlocking {
            val now = graph.clock.instant()
            val today = LocalDate.now(graph.clock) // 07:00 in the test clock
            graph.tasks.upsert(
                listOf(
                    Task("t1", "Stretch", date = today, status = TaskStatus.DONE, durationMinutes = 20, createdAt = now),
                    Task("t2", "Write the report", date = today, createdAt = now),
                    Task("t3", "Walk", date = today.minusDays(1), status = TaskStatus.DONE, createdAt = now),
                ),
            )
            graph.plans.save(
                DayPlan(
                    today,
                    listOf(
                        PlanBlock("t1", "Stretch", BlockKind.TASK, LocalTime.of(6, 30), LocalTime.of(6, 50)),
                        PlanBlock("t2", "Write the report", BlockKind.TASK, LocalTime.of(7, 0), LocalTime.of(8, 0)),
                        PlanBlock("e1", "Team call", BlockKind.FIXED_EVENT, LocalTime.of(9, 0), LocalTime.of(9, 30)),
                    ),
                    emptyList(),
                    emptyList(),
                    "test",
                ),
            )
        }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { MyDayCompanionTheme { PlanScreen(vm) } }

        compose.onNodeWithTag("day-title").assertTextContains("Good morning")
        compose.waitUntil(5_000) { compose.onAllNodesWithTagPresent("hero-now") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("hero-progress") and hasText("1 of 2 done")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("hero-now").assertTextContains("Write the report", substring = true)
        compose.onNodeWithTag("hero-next").assertTextContains("Team call", substring = true)

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("insights"))
        compose.onNodeWithTag("insights-summary").assertTextContains("2 done in the last 7 days · 2-day streak · 20 min finished today")

        // Another day has no Now / Next and offers a way back to today.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("day-title"))
        compose.onNodeWithTag("day-next").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagPresent("day-today") }
        assertTrue(!compose.onAllNodesWithTagPresent("hero-now"))
        compose.onNodeWithTag("day-today").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagPresent("hero-now") }
    }

    @Test
    fun `Appearance switches between light, dark and the phone, and wallpaper colours`() {
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { SettingsScreen(vm) }
        compose.onNodeWithTag("theme-DARK").performScrollTo().performClick()
        assertEquals("DARK", services.deviceSettings.themeMode)
        assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
        compose.onNodeWithTag("dynamic-color").performScrollTo().performClick().assertIsOn()
        assertTrue(services.deviceSettings.dynamicColor)
        compose.onNodeWithTag("theme-SYSTEM").performScrollTo().performClick()
        assertEquals("SYSTEM", services.deviceSettings.themeMode)
    }

    @Test
    fun `the dark and light palettes are what they say`() {
        var dark = 0f
        var light = 0f
        compose.setContent {
            MyDayCompanionTheme(ThemeMode.DARK) { dark = MaterialTheme.colorScheme.background.luminance() }
            MyDayCompanionTheme(ThemeMode.LIGHT) { light = MaterialTheme.colorScheme.background.luminance() }
        }
        compose.waitForIdle()
        assertTrue(dark < 0.1f, "dark background: $dark")
        assertTrue(light > 0.8f, "light background: $light")
    }

    @Test
    fun `the saved look is used from the first frame`() {
        services.deviceSettings.themeMode = "DARK"
        services.deviceSettings.dynamicColor = true
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
        assertTrue(vm.state.value.dynamicColor)
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of("something else"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagPresent(tag: String) =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
