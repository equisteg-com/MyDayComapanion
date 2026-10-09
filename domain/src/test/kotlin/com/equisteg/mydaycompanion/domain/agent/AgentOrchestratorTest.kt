package com.equisteg.mydaycompanion.domain.agent

import com.equisteg.mydaycompanion.domain.ai.AiResult
import com.equisteg.mydaycompanion.domain.ai.FakeProvider
import com.equisteg.mydaycompanion.domain.ai.ModelClass
import com.equisteg.mydaycompanion.domain.audit.AuditOutcome
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentOrchestratorTest {
    private val magic = "Tomorrow I need to finish the proposal, call Raj, work out, buy groceries, and I have a meeting at 11."

    @Test
    fun `magic moment end to end - propose, confirm, save, plan, audit`() = runTest {
        val g = TestGraph()
        val proposal = g.orchestrator.propose(magic)
        assertIs<AgentProposal.Ready>(proposal)
        assertEquals(LocalDate.of(2026, 10, 7), proposal.previewPlan.date)
        assertEquals(5, proposal.previewPlan.blocks.size)
        assertTrue(proposal.previewPlan.isRealistic)
        assertEquals(0, g.tasks.count(), "proposing must not save anything")

        val create = proposal.actions[0]
        val first = g.orchestrator.execute(create, Requester.AGENT)
        assertIs<ExecutionResult.NeedsConfirmation>(first)
        assertEquals(0, g.tasks.count())

        val confirmed = g.orchestrator.execute(create, Requester.AGENT, UserConfirmation(first.actionFingerprint))
        assertIs<ExecutionResult.Done>(confirmed)
        assertEquals(4, g.tasks.count())

        assertIs<ExecutionResult.Done>(g.orchestrator.execute(proposal.actions[1], Requester.AGENT))
        val saved = g.plans.get(LocalDate.of(2026, 10, 7))!!
        assertEquals(LocalTime.of(11, 0), saved.blocks.first { it.title == "Meeting" }.start)

        val auditText = g.audit.all().joinToString { it.toString() }
        listOf("proposal", "Raj", "groceries").forEach { assertTrue(it !in auditText, "audit must not contain '$it'") }
        assertTrue(g.audit.all().any { it.action == "tasks.create" && it.outcome == AuditOutcome.SUCCESS })
    }

    @Test
    fun `malformed model output falls back to the deterministic extractor`() = runTest {
        val broken = FakeProvider("llm", ModelClass.SMALL_LOCAL, output = AiResult.Success("{\"oops\":", "llm", "1"))
        val g = TestGraph(extraProviders = listOf(broken))
        val p = g.orchestrator.propose(magic)
        assertIs<AgentProposal.Ready>(p)
        assertEquals("rules", p.providerId)
    }

    @Test
    fun `provider exceptions and failures fall back too`() = runTest {
        val failing = object : com.equisteg.mydaycompanion.domain.ai.AiModelProvider by FakeProvider("x", ModelClass.LARGE_LOCAL) {
            override suspend fun generate(request: com.equisteg.mydaycompanion.domain.ai.AiRequest): AiResult = error("model crashed")
        }
        val g = TestGraph(extraProviders = listOf(failing))
        assertIs<AgentProposal.Ready>(g.orchestrator.propose("call Raj"))
    }

    @Test
    fun `empty or meaningless input does not create anything`() = runTest {
        val g = TestGraph()
        assertIs<AgentProposal.Empty>(g.orchestrator.propose("   "))
        assertIs<AgentProposal.NotUnderstood>(g.orchestrator.propose("thanks"))
        assertEquals(0, g.tasks.count())
    }

    @Test
    fun `replanning today starts from the current time`() = runTest {
        val g = TestGraph() // clock: 2026-10-06 07:00 UTC, before the 09:00 day start
        val p = g.orchestrator.propose("today write report, call bank") as AgentProposal.Ready
        assertEquals(LocalTime.of(9, 0), p.previewPlan.blocks.first().start)
        assertTrue(p.actions[1].args.containsKey("notBefore"))
    }

    @Test
    fun `one message can plan several days, each with its own confirmation and plan`() = runTest {
        val g = TestGraph()
        val proposal = g.orchestrator.propose("today finish report, tomorrow call Raj at 10, friday dentist at 4:30pm")
        assertIs<AgentProposal.Ready>(proposal)
        val days = listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9))
        assertEquals(days, proposal.previewPlans.map { it.date })
        assertEquals(listOf("tasks.create", "plan.generate").let { it + it + it }, proposal.actions.map { it.toolId })

        for (action in proposal.actions) {
            val first = g.orchestrator.execute(action, Requester.AGENT)
            val result = if (first is ExecutionResult.NeedsConfirmation) {
                g.orchestrator.execute(action, Requester.AGENT, UserConfirmation(first.actionFingerprint))
            } else {
                first
            }
            assertIs<ExecutionResult.Done>(result)
        }
        assertEquals(LocalTime.of(10, 0), g.plans.get(days[1])!!.blocks.single().start)
        assertEquals(LocalTime.of(16, 30), g.plans.get(days[2])!!.blocks.single().start)
        assertEquals("Finish report", g.plans.get(days[0])!!.blocks.single().title)
    }

    @Test
    fun `soft times reach the saved task and the plan`() = runTest {
        val g = TestGraph()
        val proposal = g.orchestrator.propose("tomorrow walk the dog in the evening and finish the report")
        assertIs<AgentProposal.Ready>(proposal)
        val walk = proposal.previewPlan.blocks.first { it.title == "Walk the dog" }
        assertEquals(LocalTime.of(18, 0), walk.start)
        val create = proposal.actions[0]
        val fp = (g.orchestrator.execute(create, Requester.AGENT) as ExecutionResult.NeedsConfirmation).actionFingerprint
        g.orchestrator.execute(create, Requester.AGENT, UserConfirmation(fp))
        assertEquals(LocalTime.of(18, 0), g.tasks.all().first { it.title == "Walk the dog" }.preferredStart)
    }
}
