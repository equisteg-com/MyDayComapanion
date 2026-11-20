package com.equisteg.mydaycompanion.domain.planner

import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

/**
 * Synthetic 100,000-operation smoke/stress test of PURE OFFLINE planning logic.
 *
 * IMPORTANT: sequential operations on one JVM are not 100k concurrent Android devices,
 * a 100k DAU load test, a download CDN benchmark, or a promise of an SLA.
 * Each mobile device owns its own independent storage and computation.
 */
class OfflineWorkloadSimulationTest {
    @Test
    fun `100000 independent offline planning operations preserve all constraints`() {
        val day = LocalDate.of(2026, 11, 20)
        val timestamp = Instant.parse("2026-11-20T00:00:00Z")
        val requests = (0 until 16).map { variant ->
            PlanRequest(
                date = day,
                tasks = listOf(
                    Task("first$variant", "Review inbox", durationMinutes = 25, date = day, createdAt = timestamp),
                    Task("second$variant", "Prepare report", durationMinutes = 45, date = day, createdAt = timestamp),
                    Task("third$variant", "Stretch", durationMinutes = 30, date = day, createdAt = timestamp),
                ),
                fixedEvents = listOf(
                    FixedEvent("meeting$variant", "Meeting", day, LocalTime.of(12, 0), 45),
                ),
            )
        }
        val planner = DeterministicPlanner()
        val started = TimeSource.Monotonic.markNow()
        var plannedTasks = 0
        repeat(100_000) { iteration ->
            val request = requests[iteration % requests.size]
            val plan = planner.plan(request)
            val violations = PlanValidator.validate(plan, request)
            assertEquals(emptyList(), violations, "Failed on synthetic operation $iteration")
            plannedTasks += plan.blocks.count { it.kind == com.equisteg.mydaycompanion.domain.model.BlockKind.TASK }
        }
        val elapsed = started.elapsedNow()
        println("OFFLINE_SIMULATION operations=100000 planned_tasks=$plannedTasks elapsed=$elapsed")
        assertEquals(300_000, plannedTasks)
    }
}
