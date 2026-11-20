package com.equisteg.mydaycompanion

import android.os.Looper
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.productivity.BatchEditAction
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PremiumBatchIntegrationTest {
    private val graph = TestGraph()
    private val day = LocalDate.now(graph.clock)
    private fun task(id: String, personal: Boolean = false) =
        Task(id=id,title=id,date=day,personal=personal,createdAt=graph.clock.instant())

    private fun flush() {
        repeat(12) {
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun `bulk complete runs through orchestrator and emits an audit entry for each task`() {
        runBlocking { graph.tasks.upsert(listOf(task("a"),task("b"))) }
        val vm = MainViewModel(TestServices(graph), Dispatchers.Unconfined)
        flush()
        vm.onBatchEdit(setOf("a","b"),BatchEditAction.COMPLETE)
        flush()
        assertEquals(TaskStatus.DONE,runBlocking { graph.tasks.get("a")?.status })
        assertEquals(TaskStatus.DONE,runBlocking { graph.tasks.get("b")?.status })
        assertTrue(graph.audit.entries.count { it.action=="tasks.complete" } >= 2)
    }

    @Test fun `private task mixed with ordinary task stops the entire bulk edit`() {
        runBlocking { graph.tasks.upsert(listOf(task("a"),task("protected", personal=true))) }
        val vm=MainViewModel(TestServices(graph),Dispatchers.Unconfined)
        flush()
        vm.onBatchEdit(setOf("a","protected"),BatchEditAction.COMPLETE)
        flush()
        assertEquals(TaskStatus.OPEN,runBlocking { graph.tasks.get("a")?.status })
        assertEquals(TaskStatus.OPEN,runBlocking { graph.tasks.get("protected")?.status })
        assertTrue(vm.state.value.message.orEmpty().contains("Individual review",ignoreCase=true))
    }

    @Test fun `bulk moving a flexible task recalculates both plan dates`() {
        val invoice=task("invoice").copy(deadline=day.atTime(17,0))
        runBlocking { graph.tasks.upsert(listOf(invoice)) }
        val vm=MainViewModel(TestServices(graph),Dispatchers.Unconfined)
        flush()
        vm.onBatchEdit(setOf("invoice"),BatchEditAction.MOVE_TOMORROW)
        flush()
        assertEquals(day.plusDays(1),runBlocking { graph.tasks.get("invoice")?.date })
        assertEquals(day.plusDays(1).atTime(17,0),runBlocking { graph.tasks.get("invoice")?.deadline })
        assertEquals(day,runBlocking { graph.plans.get(day)?.date })
        assertEquals(day.plusDays(1),runBlocking { graph.plans.get(day.plusDays(1))?.date })
    }
}
