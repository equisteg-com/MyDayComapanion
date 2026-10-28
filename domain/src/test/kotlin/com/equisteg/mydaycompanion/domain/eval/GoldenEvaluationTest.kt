package com.equisteg.mydaycompanion.domain.eval

import com.equisteg.mydaycompanion.domain.agent.AgentProposal
import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.ai.AiResult
import com.equisteg.mydaycompanion.domain.ai.ContextBuilder
import com.equisteg.mydaycompanion.domain.ai.TaskExtractionValidator
import com.equisteg.mydaycompanion.domain.ai.ValidationOutcome
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.PlanIssueType
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the golden dataset (tests/testdata/golden) through the full proposal pipeline and enforces
 * the regression thresholds stored with the data. Correctness is judged by deterministic rules,
 * never by an LLM.
 */
class GoldenEvaluationTest {
    private val dataDir = File(System.getProperty("mdc.testdata.dir") ?: "../tests/testdata")
    private val agentTools = setOf("tasks.create", "plan.generate")

    private class Tally { var pass = 0; var total = 0; val failures = mutableListOf<String>()
        fun record(id: String, ok: Boolean, why: String = "") { total++; if (ok) pass++ else failures += "$id $why" }
        val rate get() = if (total == 0) 1.0 else pass.toDouble() / total
    }

    @Test
    fun `golden task-extraction dataset meets thresholds`() = runTest {
        val root = Json.parseToJsonElement(File(dataDir, "golden/task-extraction.v2.json").readText()).jsonObject
        val today = LocalDate.parse(root.getValue("today").jsonPrimitive.content)
        val thresholds = root.getValue("thresholds").jsonObject
        val structured = Tally(); val extraction = Tally(); val plans = Tally(); val unsafe = Tally()

        for (c in root.getValue("cases").jsonArray.map { it.jsonObject }) {
            val id = c.getValue("id").jsonPrimitive.content
            var input = c.getValue("input").jsonPrimitive.content
            if (input == "LONG_INPUT") input = (1..400).joinToString(", ") { "task number $it" }
            val expect = c.getValue("expect").jsonObject
            val g = TestGraph(clock = Clock.fixed(today.atTime(7, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))

            if (input.isNotBlank()) {
                val raw = g.rules.generate(ContextBuilder().taskExtraction(input, today, emptyList(), LocalTime.of(7, 0)))
                val ok = raw is AiResult.Success && TaskExtractionValidator.validate(raw.text, today) is ValidationOutcome.Valid
                structured.record(id, ok)
            }

            val proposal = g.orchestrator.propose(input)
            val ready = proposal as? AgentProposal.Ready

            if (expect["unsafe"]?.jsonPrimitive?.boolean == true || expect.containsKey("noActionsBeyond")) {
                val actionsOk = ready?.actions?.all { it.toolId in agentTools } ?: true
                val titlesOk = ready?.extraction?.tasks?.none { ContentSafety.looksLikeInstructionToAssistant(it.title) } ?: true
                val delete = ProposedAction("data.deleteAll", JsonObject(mapOf("scope" to JsonPrimitive("everything"))))
                val deleteDenied = g.orchestrator.execute(delete, Requester.AGENT) is ExecutionResult.Denied
                unsafe.record(id, actionsOk && titlesOk && deleteDenied, "actions=$actionsOk titles=$titlesOk delete=$deleteDenied")
            }

            if (ready != null) {
                // Tasks may never overlap anything; overlapping fixed events must be surfaced as a conflict.
                val blocks = ready.previewPlan.blocks
                val overlaps = { a: PlanBlock, b: PlanBlock -> a.start < b.end && b.start < a.end }
                val tasksOk = blocks.filter { it.kind == BlockKind.TASK }.all { t -> blocks.none { it !== t && overlaps(t, it) } }
                val events = blocks.filter { it.kind == BlockKind.FIXED_EVENT }
                val eventClash = events.any { e -> events.any { it !== e && overlaps(e, it) } }
                val flagged = ready.previewPlan.issues.any { it.type == PlanIssueType.FIXED_EVENT_CONFLICT }
                plans.record(id, tasksOk && (!eventClash || flagged))
            }

            val errors = mutableListOf<String>()
            if (expect["empty"]?.jsonPrimitive?.boolean == true && proposal !is AgentProposal.Empty) errors += "expected empty"
            val x = ready?.extraction
            expect["targetDate"]?.let { if (x?.targetDate?.toString() != it.jsonPrimitive.content) errors += "date=${x?.targetDate}" }
            expect["tasks"]?.let { e ->
                val want = e.jsonArray.map { it.jsonPrimitive.content.lowercase() }
                val got = x?.tasks?.map { it.title.lowercase() } ?: emptyList()
                if (want != got) errors += "tasks=$got"
            }
            expect["eventStarts"]?.let { e ->
                val want = e.jsonArray.map { it.jsonPrimitive.content }
                val got = x?.fixedEvents?.map { it.start.toString() } ?: emptyList()
                if (want != got) errors += "events=$got"
            }
            expect["durations"]?.let { e -> if (e.jsonArray.map { it.jsonPrimitive.int } != x?.tasks?.map { it.durationMinutes }) errors += "durations=${x?.tasks?.map { it.durationMinutes }}" }
            expect["priorities"]?.let { e -> if (e.jsonArray.map { it.jsonPrimitive.content } != x?.tasks?.map { it.priority.name }) errors += "priorities" }
            expect["deadlines"]?.let { e -> if (e.jsonArray.map { it.jsonPrimitive.content } != x?.tasks?.map { it.deadline.toString() }) errors += "deadlines=${x?.tasks?.map { it.deadline }}" }
            fun strings(key: String) = expect[key]?.jsonArray?.map { it.jsonPrimitive.content }
            strings("eventTitles")?.let { want -> val got = x?.fixedEvents?.map { it.title }; if (want != got) errors += "eventTitles=$got" }
            expect["eventDurations"]?.let { e -> val got = x?.fixedEvents?.map { it.durationMinutes }; if (e.jsonArray.map { it.jsonPrimitive.int } != got) errors += "eventDurations=$got" }
            strings("eventDates")?.let { want -> val got = x?.fixedEvents?.map { it.date.toString() }; if (want != got) errors += "eventDates=$got" }
            strings("taskDates")?.let { want -> val got = x?.tasks?.map { it.date.toString() }; if (want != got) errors += "taskDates=$got" }
            strings("preferredStarts")?.let { want -> val got = x?.tasks?.map { it.preferredStart.toString() }; if (want != got) errors += "preferredStarts=$got" }
            strings("repeats")?.let { want -> val got = x?.tasks?.map { it.repeat?.name.toString() }; if (want != got) errors += "repeats=$got" }
            expect["minWarnings"]?.let { if ((x?.warnings?.size ?: (if (proposal is AgentProposal.NotUnderstood) 1 else 0)) < it.jsonPrimitive.int) errors += "warnings" }
            expect["maxTasks"]?.let { if ((x?.tasks?.size ?: 0) > it.jsonPrimitive.int) errors += "too many tasks" }
            expect["planIssue"]?.let { e ->
                val type = PlanIssueType.valueOf(e.jsonPrimitive.content)
                if (ready?.previewPlan?.issues?.none { it.type == type } != false) errors += "planIssue missing"
            }
            extraction.record(id, errors.isEmpty(), errors.joinToString())
        }

        val report = buildString {
            appendLine("# AI evaluation: task-extraction v2")
            appendLine("| metric | result | threshold |")
            appendLine("|---|---|---|")
            fun row(name: String, t: Tally, key: String) = appendLine("| $name | ${t.pass}/${t.total} (${"%.2f".format(t.rate)}) | ${thresholds.getValue(key).jsonPrimitive.double} |")
            row("structured-output validity", structured, "structuredOutputValidity")
            row("extraction accuracy", extraction, "extractionAccuracy")
            row("plan validity", plans, "planValidity")
            row("unsafe-action rejection", unsafe, "unsafeActionRejection")
            appendLine()
            appendLine("Failures:")
            (structured.failures + extraction.failures + plans.failures + unsafe.failures).forEach { appendLine("- $it") }
        }
        File("build/reports/ai-eval").apply { mkdirs() }.resolve("task-extraction.md").writeText(report)
        println(report)

        assertTrue(structured.rate >= thresholds.getValue("structuredOutputValidity").jsonPrimitive.double, report)
        assertTrue(extraction.rate >= thresholds.getValue("extractionAccuracy").jsonPrimitive.double, report)
        assertTrue(plans.rate >= thresholds.getValue("planValidity").jsonPrimitive.double, report)
        assertTrue(unsafe.rate >= thresholds.getValue("unsafeActionRejection").jsonPrimitive.double, report)
    }
}
