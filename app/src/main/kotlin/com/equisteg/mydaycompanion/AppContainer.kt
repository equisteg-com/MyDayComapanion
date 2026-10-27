package com.equisteg.mydaycompanion

import android.content.Context
import com.equisteg.mydaycompanion.data.db.DatabaseFactory
import com.equisteg.mydaycompanion.data.device.AndroidDeviceStateProvider
import com.equisteg.mydaycompanion.data.device.AndroidPermissionChecker
import com.equisteg.mydaycompanion.data.encryption.KeystoreDatabaseKeyProvider
import com.equisteg.mydaycompanion.data.privacy.ContentResolverExportTargets
import com.equisteg.mydaycompanion.data.repository.RoomAuditLog
import com.equisteg.mydaycompanion.data.repository.RoomFixedEventRepository
import com.equisteg.mydaycompanion.data.repository.RoomMemoryStore
import com.equisteg.mydaycompanion.data.repository.RoomPlanRepository
import com.equisteg.mydaycompanion.data.repository.RoomPreferencesStore
import com.equisteg.mydaycompanion.data.repository.RoomRoutineRepository
import com.equisteg.mydaycompanion.data.repository.RoomTaskRepository
import com.equisteg.mydaycompanion.domain.agent.AgentOrchestrator
import com.equisteg.mydaycompanion.domain.ai.AiInferenceRouter
import com.equisteg.mydaycompanion.domain.ai.ContextBuilder
import com.equisteg.mydaycompanion.domain.ai.RuleBasedTaskExtractor
import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.config.FeatureFlags
import com.equisteg.mydaycompanion.domain.future.NoNotificationScheduler
import com.equisteg.mydaycompanion.domain.planner.DeterministicPlanner
import com.equisteg.mydaycompanion.domain.policy.ToolAuthorizationPolicy
import com.equisteg.mydaycompanion.domain.privacy.DataDeletionService
import com.equisteg.mydaycompanion.domain.privacy.DataExportService
import com.equisteg.mydaycompanion.domain.repository.PlanRepository
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.tools.CompleteTaskTool
import com.equisteg.mydaycompanion.domain.tools.CreateTasksTool
import com.equisteg.mydaycompanion.domain.tools.DeleteAllDataTool
import com.equisteg.mydaycompanion.domain.tools.ExportDataTool
import com.equisteg.mydaycompanion.domain.tools.GeneratePlanTool
import com.equisteg.mydaycompanion.domain.tools.IdGenerator
import com.equisteg.mydaycompanion.domain.tools.ScheduleReminderTool
import com.equisteg.mydaycompanion.domain.tools.ToolRegistry
import java.time.Clock

/** What the UI needs. Implemented by [AppContainer] on device and by in-memory graphs in tests. */
interface AppServices {
    val clock: Clock
    val orchestrator: AgentOrchestrator
    val tasks: TaskRepository
    val plans: PlanRepository
    val audit: AuditLog
    val flags: FeatureFlags
    val exportTargets: ContentResolverExportTargets
}

/**
 * Manual dependency wiring. Everything is created lazily so the encrypted database (and its
 * Keystore unwrap) is opened off the first frame, on first use.
 */
class AppContainer(context: Context) : AppServices {
    private val appContext = context.applicationContext
    override val clock: Clock = Clock.systemDefaultZone()
    override val flags = FeatureFlags()

    private val db by lazy { DatabaseFactory.openEncrypted(appContext, KeystoreDatabaseKeyProvider(appContext)) }
    override val tasks by lazy { RoomTaskRepository(db) }
    private val events by lazy { RoomFixedEventRepository(db) }
    override val plans by lazy { RoomPlanRepository(db) }
    private val routines by lazy { RoomRoutineRepository(db) }
    private val memories by lazy { RoomMemoryStore(db) }
    override val audit by lazy { RoomAuditLog(db) }
    private val preferences by lazy { RoomPreferencesStore(db) }
    override val exportTargets by lazy { ContentResolverExportTargets(appContext.contentResolver) }

    override val orchestrator: AgentOrchestrator by lazy {
        val ids = IdGenerator.RANDOM
        val planner = DeterministicPlanner()
        val rules = RuleBasedTaskExtractor()
        val export = DataExportService(tasks, events, plans, routines, memories, audit, preferences, clock)
        val deletion = DataDeletionService(
            stores = listOf(tasks, events, plans, routines, memories, audit, preferences),
            platformWipe = { NoNotificationScheduler.cancelAll() },
        )
        AgentOrchestrator(
            router = AiInferenceRouter(providers = listOf(rules), fallback = rules),
            contextBuilder = ContextBuilder(),
            planner = planner,
            registry = ToolRegistry(
                listOf(
                    CreateTasksTool(tasks, events, ids, clock),
                    GeneratePlanTool(tasks, events, plans, preferences, planner),
                    CompleteTaskTool(tasks, clock),
                    ScheduleReminderTool(tasks, NoNotificationScheduler),
                    ExportDataTool(export, exportTargets),
                    DeleteAllDataTool(deletion),
                ),
            ),
            policy = ToolAuthorizationPolicy(flags, AndroidPermissionChecker(appContext)),
            audit = audit,
            memories = memories,
            tasks = tasks,
            events = events,
            preferences = preferences,
            deviceState = AndroidDeviceStateProvider(appContext),
            clock = clock,
            ids = ids,
        )
    }
}
