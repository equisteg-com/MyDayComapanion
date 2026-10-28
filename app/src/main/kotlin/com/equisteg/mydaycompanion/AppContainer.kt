package com.equisteg.mydaycompanion

import android.content.Context
import com.equisteg.mydaycompanion.data.db.DatabaseFactory
import com.equisteg.mydaycompanion.data.device.AndroidDeviceStateProvider
import com.equisteg.mydaycompanion.data.device.AndroidPermissionChecker
import com.equisteg.mydaycompanion.data.encryption.KeystoreDatabaseKeyProvider
import com.equisteg.mydaycompanion.data.privacy.ContentResolverExportTargets
import com.equisteg.mydaycompanion.data.privacy.LocalStorageWiper
import com.equisteg.mydaycompanion.data.repository.RoomAuditLog
import com.equisteg.mydaycompanion.data.repository.RoomFixedEventRepository
import com.equisteg.mydaycompanion.data.repository.RoomMemoryStore
import com.equisteg.mydaycompanion.data.repository.RoomPlanRepository
import com.equisteg.mydaycompanion.data.repository.RoomPreferencesStore
import com.equisteg.mydaycompanion.data.repository.RoomRoutineRepository
import com.equisteg.mydaycompanion.data.repository.RoomSecurityStore
import com.equisteg.mydaycompanion.data.repository.RoomTaskRepository
import com.equisteg.mydaycompanion.domain.agent.AgentOrchestrator
import com.equisteg.mydaycompanion.domain.agent.DailyMaintenance
import com.equisteg.mydaycompanion.domain.ai.AiInferenceRouter
import com.equisteg.mydaycompanion.domain.ai.ContextBuilder
import com.equisteg.mydaycompanion.domain.ai.RuleBasedTaskExtractor
import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.config.FeatureFlags
import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.domain.planner.DeterministicPlanner
import com.equisteg.mydaycompanion.domain.policy.ToolAuthorizationPolicy
import com.equisteg.mydaycompanion.domain.privacy.DataDeletionService
import com.equisteg.mydaycompanion.domain.privacy.DataExportService
import com.equisteg.mydaycompanion.domain.repository.PlanRepository
import com.equisteg.mydaycompanion.domain.repository.PreferencesStore
import com.equisteg.mydaycompanion.domain.repository.RoutineRepository
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.security.ChallengeVerifier
import com.equisteg.mydaycompanion.domain.security.SecurityStore
import com.equisteg.mydaycompanion.domain.tools.AddSubtaskTool
import com.equisteg.mydaycompanion.domain.tools.CompleteTaskTool
import com.equisteg.mydaycompanion.domain.tools.CreateTasksTool
import com.equisteg.mydaycompanion.domain.tools.DeleteAllDataTool
import com.equisteg.mydaycompanion.domain.tools.DeleteTaskTool
import com.equisteg.mydaycompanion.domain.tools.ExportDataTool
import com.equisteg.mydaycompanion.domain.tools.GeneratePlanTool
import com.equisteg.mydaycompanion.domain.tools.IdGenerator
import com.equisteg.mydaycompanion.domain.tools.CreateRoutineTool
import com.equisteg.mydaycompanion.domain.tools.DeleteRoutineTool
import com.equisteg.mydaycompanion.domain.tools.MaterializeRoutinesTool
import com.equisteg.mydaycompanion.domain.tools.ReopenTaskTool
import com.equisteg.mydaycompanion.domain.tools.SyncRemindersTool
import com.equisteg.mydaycompanion.domain.tools.UpdateRoutineTool
import com.equisteg.mydaycompanion.reminders.AlarmNotificationScheduler
import com.equisteg.mydaycompanion.domain.tools.SetChallengeQuestionsTool
import com.equisteg.mydaycompanion.domain.tools.ToolRegistry
import com.equisteg.mydaycompanion.domain.tools.UpdateSecuritySettingsTool
import com.equisteg.mydaycompanion.domain.tools.UpdateSettingsTool
import com.equisteg.mydaycompanion.domain.tools.UpdateTaskTool
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
    val security: SecurityStore
    val challenges: ChallengeVerifier
    val preferences: PreferencesStore
    val routines: RoutineRepository

    /** Adds routine tasks, replans and re-registers reminders; run on open, after restarts and each night. */
    val maintenance: DailyMaintenance

    /** Closes and deletes the database files and destroys their key (build doc §8.4). The app must restart afterwards. */
    fun wipeLocalStorage(): Boolean
}

/**
 * Manual dependency wiring. Everything is created lazily so the encrypted database (and its
 * Keystore unwrap) is opened off the first frame, on first use.
 */
class AppContainer(context: Context) : AppServices {
    private val appContext = context.applicationContext
    override val clock: Clock = Clock.systemDefaultZone()
    override val flags = FeatureFlags()

    private val keys = KeystoreDatabaseKeyProvider(appContext)
    private val dbHolder = lazy { DatabaseFactory.openEncrypted(appContext, keys) }
    private val db by dbHolder
    override val tasks by lazy { RoomTaskRepository(db) }
    private val events by lazy { RoomFixedEventRepository(db) }
    override val plans by lazy { RoomPlanRepository(db) }
    override val routines by lazy { RoomRoutineRepository(db) }
    private val memories by lazy { RoomMemoryStore(db) }
    override val audit by lazy { RoomAuditLog(db) }
    override val preferences by lazy { RoomPreferencesStore(db) }
    override val exportTargets by lazy { ContentResolverExportTargets(appContext.contentResolver) }
    override val security by lazy { RoomSecurityStore(db) }
    override val challenges by lazy { ChallengeVerifier(security, clock::instant) }
    private val scheduler by lazy { AlarmNotificationScheduler(appContext, clock) }
    private val permissionChecker by lazy { AndroidPermissionChecker(appContext) }
    override val maintenance by lazy {
        DailyMaintenance(
            orchestrator, tasks, plans, routines, clock,
            remindersReady = { flags.isEnabled(Flag.REMINDERS) && permissionChecker.isGranted(SyncRemindersTool.POST_NOTIFICATIONS) },
            afterRun = { scheduler.scheduleMaintenance() },
        )
    }

    override fun wipeLocalStorage(): Boolean =
        runCatching { scheduler.cancelEverything() }.isSuccess and LocalStorageWiper(appContext, keys) { if (dbHolder.isInitialized()) dbHolder.value else null }.wipe()

    override val orchestrator: AgentOrchestrator by lazy {
        val ids = IdGenerator.RANDOM
        val planner = DeterministicPlanner()
        val rules = RuleBasedTaskExtractor()
        val export = DataExportService(tasks, events, plans, routines, memories, audit, preferences, clock)
        val deletion = DataDeletionService(
            stores = listOf(tasks, events, plans, routines, memories, audit, preferences, security),
            platformWipe = { scheduler.cancelAll() },
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
                    ReopenTaskTool(tasks, clock),
                    UpdateTaskTool(tasks, clock),
                    DeleteTaskTool(tasks),
                    AddSubtaskTool(tasks, ids, clock),
                    UpdateSettingsTool(preferences),
                    SyncRemindersTool(plans, tasks, routines, preferences, scheduler, clock),
                    CreateRoutineTool(routines, ids, clock),
                    UpdateRoutineTool(routines),
                    DeleteRoutineTool(routines),
                    MaterializeRoutinesTool(routines, tasks, clock),
                    ExportDataTool(export, exportTargets),
                    DeleteAllDataTool(deletion),
                    UpdateSecuritySettingsTool(security),
                    SetChallengeQuestionsTool(security),
                ),
            ),
            policy = ToolAuthorizationPolicy(flags, permissionChecker),
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
