package com.equisteg.mydaycompanion.domain.testing

import com.equisteg.mydaycompanion.domain.agent.AgentOrchestrator
import com.equisteg.mydaycompanion.domain.ai.AiInferenceRouter
import com.equisteg.mydaycompanion.domain.ai.AiModelProvider
import com.equisteg.mydaycompanion.domain.ai.ContextBuilder
import com.equisteg.mydaycompanion.domain.ai.RuleBasedTaskExtractor
import com.equisteg.mydaycompanion.domain.config.FeatureFlags
import com.equisteg.mydaycompanion.domain.future.NoNotificationScheduler
import com.equisteg.mydaycompanion.domain.planner.DeterministicPlanner
import com.equisteg.mydaycompanion.domain.policy.PermissionChecker
import com.equisteg.mydaycompanion.domain.policy.ToolAuthorizationPolicy
import com.equisteg.mydaycompanion.domain.privacy.DataDeletionService
import com.equisteg.mydaycompanion.domain.privacy.DataExportService
import com.equisteg.mydaycompanion.domain.tools.CompleteTaskTool
import com.equisteg.mydaycompanion.domain.tools.CreateTasksTool
import com.equisteg.mydaycompanion.domain.tools.DeleteAllDataTool
import com.equisteg.mydaycompanion.domain.tools.ExportDataTool
import com.equisteg.mydaycompanion.domain.tools.ExportTargets
import com.equisteg.mydaycompanion.domain.tools.GeneratePlanTool
import com.equisteg.mydaycompanion.domain.tools.ScheduleReminderTool
import com.equisteg.mydaycompanion.domain.tools.ToolRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InMemoryExportTargets : ExportTargets {
    val written = mutableMapOf<String, ByteArray>()
    val registered = mutableSetOf<String>()
    override fun exists(token: String) = token in registered
    override suspend fun write(token: String, bytes: ByteArray): Long? {
        if (token !in registered) return null
        written[token] = bytes
        return bytes.size.toLong()
    }
}

/** Fully wired in-memory object graph for tests. Defaults: 2026-10-06 07:00 UTC, battery 80%. */
class TestGraph(
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-06T07:00:00Z"), ZoneOffset.UTC),
    flags: FeatureFlags = FeatureFlags(),
    grantedPermissions: Set<String> = emptySet(),
    extraProviders: List<AiModelProvider> = emptyList(),
) {
    val tasks = InMemoryTaskRepository()
    val events = InMemoryFixedEventRepository()
    val plans = InMemoryPlanRepository()
    val routines = InMemoryRoutineRepository()
    val memories = InMemoryMemoryStore()
    val audit = InMemoryAuditLog()
    val preferences = InMemoryPreferencesStore()
    val device = FixedDeviceState()
    val ids = SequentialIds()
    val exportTargets = InMemoryExportTargets()
    val planner = DeterministicPlanner()
    val rules = RuleBasedTaskExtractor()
    val permissions = PermissionChecker { it in grantedPermissions }
    val policy = ToolAuthorizationPolicy(flags, permissions)
    val export = DataExportService(tasks, events, plans, routines, memories, audit, preferences, clock)
    val deletion = DataDeletionService(listOf(tasks, events, plans, routines, memories, audit, preferences))
    val registry = ToolRegistry(
        listOf(
            CreateTasksTool(tasks, events, ids, clock),
            GeneratePlanTool(tasks, events, plans, preferences, planner),
            CompleteTaskTool(tasks, clock),
            ScheduleReminderTool(tasks, NoNotificationScheduler),
            ExportDataTool(export, exportTargets),
            DeleteAllDataTool(deletion),
        ),
    )
    val orchestrator = AgentOrchestrator(
        router = AiInferenceRouter(extraProviders + rules, fallback = rules),
        contextBuilder = ContextBuilder(),
        planner = planner,
        registry = registry,
        policy = policy,
        audit = audit,
        memories = memories,
        tasks = tasks,
        events = events,
        preferences = preferences,
        deviceState = device,
        clock = clock,
        ids = ids,
    )
}
