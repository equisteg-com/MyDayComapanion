package com.equisteg.mydaycompanion.domain.testing

import com.equisteg.mydaycompanion.domain.agent.AgentOrchestrator
import com.equisteg.mydaycompanion.domain.ai.AiInferenceRouter
import com.equisteg.mydaycompanion.domain.ai.AiModelProvider
import com.equisteg.mydaycompanion.domain.ai.ContextBuilder
import com.equisteg.mydaycompanion.domain.ai.RuleBasedTaskExtractor
import com.equisteg.mydaycompanion.domain.config.FeatureFlags
import com.equisteg.mydaycompanion.domain.future.NotificationScheduler
import com.equisteg.mydaycompanion.domain.reminders.Reminder
import com.equisteg.mydaycompanion.domain.planner.DeterministicPlanner
import com.equisteg.mydaycompanion.domain.policy.PermissionChecker
import com.equisteg.mydaycompanion.domain.policy.ToolAuthorizationPolicy
import com.equisteg.mydaycompanion.domain.privacy.DataDeletionService
import com.equisteg.mydaycompanion.domain.privacy.DataExportService
import com.equisteg.mydaycompanion.domain.tools.AddSubtaskTool
import com.equisteg.mydaycompanion.domain.tools.CompleteTaskTool
import com.equisteg.mydaycompanion.domain.tools.CreateTasksTool
import com.equisteg.mydaycompanion.domain.tools.DeleteAllDataTool
import com.equisteg.mydaycompanion.domain.tools.DeleteTaskTool
import com.equisteg.mydaycompanion.domain.tools.ExportDataTool
import com.equisteg.mydaycompanion.domain.tools.ExportTargets
import com.equisteg.mydaycompanion.domain.tools.GeneratePlanTool
import com.equisteg.mydaycompanion.domain.tools.CreateRoutineTool
import com.equisteg.mydaycompanion.domain.tools.DeleteRoutineTool
import com.equisteg.mydaycompanion.domain.tools.MaterializeRoutinesTool
import com.equisteg.mydaycompanion.domain.tools.ReopenTaskTool
import com.equisteg.mydaycompanion.domain.tools.SyncRemindersTool
import com.equisteg.mydaycompanion.domain.tools.UpdateRoutineTool
import com.equisteg.mydaycompanion.domain.tools.SetChallengeQuestionsTool
import com.equisteg.mydaycompanion.domain.tools.ToolRegistry
import com.equisteg.mydaycompanion.domain.tools.UpdateSecuritySettingsTool
import com.equisteg.mydaycompanion.domain.tools.UpdateSettingsTool
import com.equisteg.mydaycompanion.domain.tools.UpdateTaskTool
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

/** Keeps the last set of reminders it was given, as the Android scheduler would. */
class RecordingNotificationScheduler : NotificationScheduler {
    var registered: List<Reminder> = emptyList()
        private set
    var available = true
    override suspend fun replaceAll(reminders: List<Reminder>): Int {
        if (!available) return 0
        registered = reminders
        return reminders.size
    }
    override suspend fun cancelAll() { registered = emptyList() }
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
    val security = InMemorySecurityStore()
    val device = FixedDeviceState()
    val ids = SequentialIds()
    val exportTargets = InMemoryExportTargets()
    val scheduler = RecordingNotificationScheduler()
    val planner = DeterministicPlanner()
    val rules = RuleBasedTaskExtractor()
    val permissions = PermissionChecker { it in grantedPermissions }
    val policy = ToolAuthorizationPolicy(flags, permissions)
    val export = DataExportService(tasks, events, plans, routines, memories, audit, preferences, clock)
    val deletion = DataDeletionService(listOf(tasks, events, plans, routines, memories, audit, preferences, security))
    val registry = ToolRegistry(
        listOf(
            CreateTasksTool(tasks, events, ids, clock),
            GeneratePlanTool(tasks, events, plans, preferences, planner),
            CompleteTaskTool(tasks, clock),
            ReopenTaskTool(tasks, clock),
            UpdateTaskTool(tasks, clock),
            DeleteTaskTool(tasks),
            AddSubtaskTool(tasks, ids, clock),
            UpdateSettingsTool(preferences),
            SyncRemindersTool(plans, tasks, routines, preferences, scheduler, clock),
            CreateRoutineTool(routines, ids, clock),
            UpdateRoutineTool(routines),
            DeleteRoutineTool(routines),
            MaterializeRoutinesTool(routines, tasks, ids, clock),
            ExportDataTool(export, exportTargets),
            DeleteAllDataTool(deletion),
            UpdateSecuritySettingsTool(security),
            SetChallengeQuestionsTool(security),
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
