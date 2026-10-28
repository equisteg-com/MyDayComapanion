package com.equisteg.mydaycompanion.domain.privacy

import com.equisteg.mydaycompanion.domain.agent.AgentProposal
import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemorySensitivity
import com.equisteg.mydaycompanion.domain.memory.MemorySource
import com.equisteg.mydaycompanion.domain.memory.MemoryStatus
import com.equisteg.mydaycompanion.domain.memory.MemoryType
import com.equisteg.mydaycompanion.domain.memory.Retention
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrivacyTest {
    private suspend fun seeded(): TestGraph {
        val g = TestGraph()
        val p = g.orchestrator.propose("Tomorrow finish the proposal, call Raj and I have a meeting at 11") as AgentProposal.Ready
        val fp = (g.orchestrator.execute(p.actions[0], Requester.AGENT) as ExecutionResult.NeedsConfirmation).actionFingerprint
        g.orchestrator.execute(p.actions[0], Requester.AGENT, UserConfirmation(fp))
        g.orchestrator.execute(p.actions[1], Requester.AGENT)
        val now = g.clock.instant()
        g.memories.upsert(MemoryItem("m1", MemoryType.PREFERENCE, "Likes mornings", MemorySource.USER_EXPLICIT, 1.0, MemoryStatus.ACTIVE, MemorySensitivity.LOW, Retention.UNTIL_DELETED, createdAt = now, updatedAt = now))
        return g
    }

    private suspend fun runConfirmed(g: TestGraph, action: ProposedAction): ExecutionResult {
        val first = g.orchestrator.execute(action, Requester.USER)
        assertIs<ExecutionResult.NeedsConfirmation>(first, "user-initiated L3/L4 must still ask")
        return g.orchestrator.execute(action, Requester.USER, UserConfirmation(first.actionFingerprint))
    }

    @Test
    fun `export contains every store and is valid json`() = runTest {
        val g = seeded()
        g.exportTargets.registered += "file-1"
        val result = runConfirmed(g, ProposedAction("data.export", JsonObject(mapOf("target" to JsonPrimitive("file-1")))))
        assertIs<ExecutionResult.Done>(result)
        val text = g.exportTargets.written.getValue("file-1").decodeToString()
        val bundle = Json.decodeFromString(ExportBundle.serializer(), text)
        assertEquals(ExportBundle.FORMAT, bundle.format)
        assertEquals(2, bundle.tasks.size)
        assertEquals(1, bundle.fixedEvents.size)
        assertEquals(1, bundle.memories.size)
        assertTrue(bundle.plans.isNotEmpty())
        assertTrue(bundle.auditLog.isNotEmpty())
    }

    @Test
    fun `export to an unregistered destination is rejected`() = runTest {
        val g = seeded()
        val r = g.orchestrator.execute(ProposedAction("data.export", JsonObject(mapOf("target" to JsonPrimitive("/sdcard/leak.json")))), Requester.USER)
        assertEquals(ExecutionResult.Denied("INVALID_ARGUMENTS"), r)
    }

    @Test
    fun `delete everything leaves every store empty and is verified`() = runTest {
        val g = seeded()
        val result = runConfirmed(g, ProposedAction("data.deleteAll", JsonObject(mapOf("scope" to JsonPrimitive("everything")))))
        assertIs<ExecutionResult.Done>(result)
        listOf(g.tasks, g.events, g.plans, g.routines, g.memories, g.preferences).forEach { assertEquals(0, it.count(), it.storeName) }
        // Only the non-personal deletion receipt remains in the audit log.
        assertEquals(listOf("data.deleteAll"), g.audit.all().map { it.action })
    }
}
