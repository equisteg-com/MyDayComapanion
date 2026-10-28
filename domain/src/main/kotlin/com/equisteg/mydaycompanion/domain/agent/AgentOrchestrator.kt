package com.equisteg.mydaycompanion.domain.agent

import com.equisteg.mydaycompanion.domain.ai.AiResult
import com.equisteg.mydaycompanion.domain.ai.AiInferenceRouter
import com.equisteg.mydaycompanion.domain.ai.ContextBuilder
import com.equisteg.mydaycompanion.domain.ai.DeviceStateProvider
import com.equisteg.mydaycompanion.domain.ai.ExtractedEvent
import com.equisteg.mydaycompanion.domain.ai.ExtractedTask
import com.equisteg.mydaycompanion.domain.ai.TaskExtraction
import com.equisteg.mydaycompanion.domain.ai.TaskExtractionValidator
import com.equisteg.mydaycompanion.domain.ai.ValidationOutcome
import com.equisteg.mydaycompanion.domain.audit.AuditActor
import com.equisteg.mydaycompanion.domain.audit.AuditDecision
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.audit.AuditOutcome
import com.equisteg.mydaycompanion.domain.memory.MemoryStore
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.planner.PlanRequest
import com.equisteg.mydaycompanion.domain.planner.PlanValidator
import com.equisteg.mydaycompanion.domain.planner.PlannerEngine
import com.equisteg.mydaycompanion.domain.policy.PolicyDecision
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.ToolAuthorizationPolicy
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.repository.FixedEventRepository
import com.equisteg.mydaycompanion.domain.repository.PreferencesStore
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.safety.InputNormalizer
import com.equisteg.mydaycompanion.domain.tools.ArgsValidation
import com.equisteg.mydaycompanion.domain.tools.IdGenerator
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import com.equisteg.mydaycompanion.domain.tools.ToolOutcome
import com.equisteg.mydaycompanion.domain.tools.ToolRegistry
import com.equisteg.mydaycompanion.domain.tools.canonical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** A structured action the assistant suggests. It does nothing until [AgentOrchestrator.execute] authorises it. */
data class ProposedAction(val toolId: String, val args: JsonObject)

sealed interface AgentProposal {
    data object Empty : AgentProposal

    data class NotUnderstood(val reason: String) : AgentProposal

    data class Ready(
        val extraction: TaskExtraction,
        /** Preview only, one per day the message touches, in date order; nothing is saved until the actions run. */
        val previewPlans: List<DayPlan>,
        val actions: List<ProposedAction>,
        val providerId: String,
        val warnings: List<String>,
    ) : AgentProposal {
        /** The plan for the first day in the proposal. */
        val previewPlan: DayPlan get() = previewPlans.first()
    }
}

sealed interface ExecutionResult {
    data class Done(val summary: String) : ExecutionResult
    /** [riskLevel] tells the UI which approval steps to ask for (build doc §6.1). */
    /** [personal] means the action touches an item the owner marked personal, so the owner's chosen verification applies. */
    data class NeedsConfirmation(
        val actionFingerprint: String,
        val riskLevel: RiskLevel = RiskLevel.L2,
        val personal: Boolean = false,
    ) : ExecutionResult
    data class Denied(val reason: String) : ExecutionResult
    data class Failed(val reason: String) : ExecutionResult
}

/**
 * The only path from user input to device effects:
 * input → normalisation → context → model (via router) → schema + domain validation →
 * proposal → policy → permission → allowlisted tool → verification → audit.
 */
class AgentOrchestrator(
    private val router: AiInferenceRouter,
    private val contextBuilder: ContextBuilder,
    private val planner: PlannerEngine,
    private val registry: ToolRegistry,
    private val policy: ToolAuthorizationPolicy,
    private val audit: AuditLog,
    private val memories: MemoryStore,
    private val tasks: TaskRepository,
    private val events: FixedEventRepository,
    private val preferences: PreferencesStore,
    private val deviceState: DeviceStateProvider,
    private val clock: Clock,
    private val ids: IdGenerator = IdGenerator.RANDOM,
) {
    suspend fun propose(userText: String): AgentProposal {
        val text = InputNormalizer.normalize(userText)
        if (text.isEmpty()) return AgentProposal.Empty
        val today = LocalDate.now(clock)
        val request = contextBuilder.taskExtraction(text, today, memories.all(), LocalTime.now(clock), preferences.get().dateOrder)
        val route = router.route(request, deviceState.current())

        var providerId = route.provider.id
        var extraction = runAndValidate(route.provider, request, today)
        if (extraction == null && route.provider !== router.fallbackProvider()) {
            providerId = router.fallbackProvider().id
            extraction = runAndValidate(router.fallbackProvider(), request, today)
        }
        if (extraction == null) {
            record(AuditActor.AGENT, "ai.extract", null, AuditDecision.NOT_APPLICABLE, AuditOutcome.FAILURE, "OUTPUT_INVALID", "provider=$providerId")
            return AgentProposal.NotUnderstood("I couldn't turn that into tasks. Try listing them separated by commas.")
        }
        if (extraction.tasks.isEmpty() && extraction.fixedEvents.isEmpty()) {
            record(AuditActor.AGENT, "ai.extract", null, AuditDecision.NOT_APPLICABLE, AuditOutcome.SUCCESS, "NOTHING_FOUND", "provider=$providerId")
            return AgentProposal.NotUnderstood(extraction.warnings.firstOrNull() ?: "I didn't find any tasks in that.")
        }

        val dates = extraction.dates
        val previews = dates.map { previewPlan(extraction, it) }
        val actions = dates.flatMap { date ->
            val dayTasks = extraction.tasks.filter { it.date == date }
            val dayEvents = extraction.fixedEvents.filter { it.date == date }
            listOf(ProposedAction("tasks.create", createArgs(date, dayTasks, dayEvents)), ProposedAction("plan.generate", planArgs(date)))
        } + extraction.tasks.filter { it.repeat != null }.distinctBy { it.title.lowercase() }.map {
            ProposedAction("routines.create", routineArgs(it))
        }
        val warnings = extraction.warnings + extraction.tasks.filter { it.repeat != null }.distinctBy { it.title.lowercase() }.map {
            "\"${it.title}\" repeats (${it.repeat!!.name.lowercase()}). Approving also saves it as a routine, so it is added on every day it is due."
        }
        record(
            AuditActor.AGENT, "ai.extract", null, AuditDecision.NOT_APPLICABLE, AuditOutcome.SUCCESS, null,
            "provider=$providerId tasks=${extraction.tasks.size} events=${extraction.fixedEvents.size}",
        )
        return AgentProposal.Ready(
            extraction = extraction,
            previewPlans = previews,
            actions = actions,
            providerId = providerId,
            warnings = warnings,
        )
    }

    private fun createArgs(date: LocalDate, dayTasks: List<ExtractedTask>, dayEvents: List<ExtractedEvent>) = JsonObject(
        mapOf(
            "date" to JsonPrimitive(date.toString()),
            "tasks" to JsonArray(
                dayTasks.map {
                    JsonObject(
                        buildMap {
                            put("title", JsonPrimitive(it.title))
                            put("priority", JsonPrimitive(it.priority.name))
                            put("durationMinutes", JsonPrimitive(it.durationMinutes))
                            put("deadline", it.deadline?.let { d -> JsonPrimitive(d.toString()) } ?: JsonNull)
                            it.preferredStart?.let { p -> put("preferredStart", JsonPrimitive(p.toString())) }
                            it.remindAt?.let { r -> put("remindAt", JsonPrimitive(r.toString())) }
                        },
                    )
                },
            ),
            "fixedEvents" to JsonArray(
                dayEvents.map {
                    JsonObject(
                        mapOf(
                            "title" to JsonPrimitive(it.title),
                            "start" to JsonPrimitive(it.start.toString()),
                            "durationMinutes" to JsonPrimitive(it.durationMinutes),
                        ),
                    )
                },
            ),
        ),
    )

    private fun routineArgs(t: ExtractedTask) = JsonObject(
        buildMap {
            put("title", JsonPrimitive(t.title))
            put("frequency", JsonPrimitive(t.repeat!!.name))
            put("durationMinutes", JsonPrimitive(t.durationMinutes))
            t.preferredStart?.let { put("preferredStart", JsonPrimitive(it.toString())) }
            if (t.repeat == RoutineFrequency.WEEKLY) put("dayOfWeek", JsonPrimitive(t.date.dayOfWeek.name))
        },
    )

    private fun planArgs(date: LocalDate) = JsonObject(
        buildMap {
            put("date", JsonPrimitive(date.toString()))
            notBefore(date)?.let { put("notBefore", JsonPrimitive(it.toString())) }
        },
    )

    /** Fingerprint the UI must echo back in a [UserConfirmation] after the user approves [action]. */
    fun fingerprint(action: ProposedAction): String = ToolAuthorizationPolicy.fingerprint(action.toolId, action.args.canonical())

    suspend fun execute(action: ProposedAction, requester: Requester, confirmation: UserConfirmation? = null): ExecutionResult {
        val tool = registry.find(action.toolId)
        val validation = tool?.validate(action.args)
        val personal = tool != null && validation == ArgsValidation.Ok && tool.touchesPersonal(action.args)
        val decision = policy.decide(
            spec = tool?.spec,
            toolId = action.toolId,
            canonicalArgs = action.args.canonical(),
            argsValid = validation == ArgsValidation.Ok,
            requester = requester,
            confirmation = confirmation,
            personal = personal,
        )
        val actor = if (requester == Requester.USER) AuditActor.USER else AuditActor.AGENT
        val risk = tool?.spec?.riskLevel
        // Unknown tool ids come from untrusted output; never echo them into the audit log verbatim.
        val auditAction = if (tool == null) "unknown" else action.toolId

        return when (decision) {
            is PolicyDecision.Deny -> {
                val code = decision.reason.name
                record(actor, auditAction, risk, AuditDecision.DENIED, AuditOutcome.NOT_EXECUTED, code)
                ExecutionResult.Denied(code)
            }
            is PolicyDecision.RequireConfirmation -> {
                record(actor, auditAction, risk, AuditDecision.CONFIRMATION_REQUIRED, AuditOutcome.NOT_EXECUTED, null)
                ExecutionResult.NeedsConfirmation(decision.actionFingerprint, risk ?: RiskLevel.L2, personal)
            }
            PolicyDecision.Allow -> {
                val outcome = try {
                    tool!!.execute(action.args)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ToolOutcome.Failure("EXCEPTION_${e::class.simpleName}")
                }
                when (outcome) {
                    is ToolOutcome.Failure -> {
                        record(actor, auditAction, risk, AuditDecision.ALLOWED, AuditOutcome.FAILURE, outcome.reasonCode)
                        ExecutionResult.Failed(outcome.reasonCode)
                    }
                    is ToolOutcome.Success -> {
                        val verified = try { tool!!.verify(action.args, outcome) } catch (e: Exception) { false }
                        if (verified) {
                            record(actor, auditAction, risk, AuditDecision.ALLOWED, AuditOutcome.SUCCESS, null, outcome.summary)
                            ExecutionResult.Done(outcome.summary)
                        } else {
                            record(actor, auditAction, risk, AuditDecision.ALLOWED, AuditOutcome.VERIFICATION_FAILED, "VERIFY_FAILED", outcome.summary)
                            ExecutionResult.Failed("VERIFY_FAILED")
                        }
                    }
                }
            }
        }
    }

    private suspend fun runAndValidate(
        provider: com.equisteg.mydaycompanion.domain.ai.AiModelProvider,
        request: com.equisteg.mydaycompanion.domain.ai.AiRequest,
        today: LocalDate,
    ): TaskExtraction? {
        val result = try {
            withTimeoutOrNull(request.timeoutMillis) { provider.generate(request) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val text = (result as? AiResult.Success)?.text?.take(request.maxOutputChars + 1) ?: return null
        if (text.length > request.maxOutputChars) return null
        return when (val v = TaskExtractionValidator.validate(text, today)) {
            is ValidationOutcome.Valid -> v.value
            is ValidationOutcome.Invalid -> null
        }
    }

    private suspend fun previewPlan(extraction: TaskExtraction, date: LocalDate): DayPlan {
        val now = clock.instant()
        val prefs = preferences.get()
        val existingTasks = tasks.all().filter { it.date == date }
        val newTasks = extraction.tasks.filter { it.date == date }.map {
            Task(
                ids.newId(), it.title, it.priority, it.durationMinutes, date, it.deadline?.let { d -> date.atTime(d) },
                createdAt = now, preferredStart = it.preferredStart,
            )
        }
        val newEvents = extraction.fixedEvents.filter { it.date == date }.map { FixedEvent(ids.newId(), it.title, date, it.start, it.durationMinutes) }
        val request = PlanRequest(
            date = date,
            tasks = existingTasks + newTasks,
            fixedEvents = events.forDate(date) + newEvents,
            dayStart = prefs.dayStart,
            dayEnd = prefs.dayEnd,
            bufferMinutes = prefs.bufferMinutes,
            notBefore = notBefore(date),
        )
        val plan = planner.plan(request)
        check(PlanValidator.validate(plan, request).isEmpty()) { "Planner produced an invalid plan" }
        return plan
    }

    /** When planning today, nothing goes before the current time (rounded to the minute). */
    private fun notBefore(date: LocalDate): LocalTime? =
        if (date == LocalDate.now(clock)) LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES) else null

    private suspend fun record(
        actor: AuditActor,
        action: String,
        risk: com.equisteg.mydaycompanion.domain.tools.RiskLevel?,
        decision: AuditDecision,
        outcome: AuditOutcome,
        reason: String?,
        detail: String? = null,
    ) {
        audit.record(
            AuditEntry(
                id = ids.newId(),
                timestamp = clock.instant(),
                actor = actor,
                action = action,
                riskLevel = risk,
                decision = decision,
                outcome = outcome,
                reasonCode = reason,
                detail = detail?.take(AuditEntry.MAX_DETAIL_CHARS),
            ),
        )
    }
}
