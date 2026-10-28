package com.equisteg.mydaycompanion

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class RoutinesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val graph = TestGraph()
    private val services = TestServices(graph)
    private val viewModel = MainViewModel(services, Dispatchers.Unconfined)
    private val today = LocalDate.parse("2026-10-06")

    @Test
    fun `owner adds a routine and today's task appears once`() {
        compose.setContent { AppNavigation(viewModel) }
        compose.onNodeWithTag("nav-ROUTINES").performClick()
        compose.onNodeWithTag("routine-title").performScrollTo().performTextInput("Stretch")
        compose.onNodeWithTag("routine-frequency-DAILY").performScrollTo().performClick()
        compose.onNodeWithTag("routine-time").performScrollTo().performTextInput("6pm")
        compose.onNodeWithTag("routine-save").performScrollTo().performClick()

        compose.waitUntil(5_000) { viewModel.state.value.message == "Routine saved." }
        val routine = runBlocking { graph.routines.all() }.single()
        assertEquals(RoutineFrequency.DAILY, routine.frequency)
        assertEquals("18:00", routine.preferredStart.toString())
        val todays = runBlocking { graph.tasks.all() }.filter { it.date == today && it.routineId == routine.id }
        assertEquals(1, todays.size, "today's routine task added by maintenance")
        assertTrue(runBlocking { graph.plans.get(today) } != null, "today replanned around it")
    }

    @Test
    fun `pausing a routine and deleting it needs a tap`() {
        runBlocking {
            graph.routines.upsert(
                com.equisteg.mydaycompanion.domain.model.Routine("r1", "Gym", RoutineFrequency.WEEKDAYS, null, 45, createdAt = graph.clock.instant()),
            )
        }
        compose.setContent { AppNavigation(viewModel) }
        compose.onNodeWithTag("nav-ROUTINES").performClick()
        compose.onNodeWithTag("routine-enabled-r1").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.routines.get("r1") }?.enabled == false }

        compose.onNodeWithTag("routine-delete-r1").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { graph.routines.get("r1") } != null, "nothing deleted before the tap")
        compose.onNodeWithTag("confirm").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.routines.count() } == 0 }
    }

    @Test
    fun `deleting a routine task skips it today and hides it`() {
        runBlocking {
            graph.routines.upsert(
                com.equisteg.mydaycompanion.domain.model.Routine("r1", "Gym", RoutineFrequency.DAILY, null, 45, createdAt = graph.clock.instant()),
            )
        }
        val task = runBlocking {
            services.maintenance.run()
            graph.tasks.all().single { it.routineId == "r1" && it.date == today }
        }
        viewModel.onDeleteTask(task.id)
        compose.setContent { AppNavigation(viewModel) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Skip today?", viewModel.state.value.pending?.title)
        compose.onNodeWithTag("confirm").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.tasks.get(task.id) }?.status == TaskStatus.CANCELLED }
        assertFalse(compose.onAllNodes(hasTestTag("task-${task.id}")).fetchSemanticsNodes().isNotEmpty(), "a skipped task is not shown")
        runBlocking { services.maintenance.run() }
        assertEquals(1, runBlocking { graph.tasks.all() }.count { it.routineId == "r1" && it.date == today }, "not added back")
    }
}
