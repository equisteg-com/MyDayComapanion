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
import com.equisteg.mydaycompanion.ai.MlKitNanoEngine
import com.equisteg.mydaycompanion.domain.ai.AiInferenceRouter
import com.equisteg.mydaycompanion.domain.ai.LocalModelControl
import com.equisteg.mydaycompanion.domain.ai.OnDeviceLlmProvider
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
import com.equisteg.mydaycompanion.data.device.ContentResolverCalendarProvider
import com.equisteg.mydaycompanion.device.AndroidDeviceActionLauncher
import com.equisteg.mydaycompanion.device.AndroidSpeaker
import com.equisteg.mydaycompanion.domain.tools.CalendarInsertTool
import com.equisteg.mydaycompanion.domain.tools.ComposeMessageTool
import com.equisteg.mydaycompanion.domain.tools.CreateRoutineTool
import com.equisteg.mydaycompanion.domain.tools.DialTool
import com.equisteg.mydaycompanion.domain.tools.NavigateTool
import com.equisteg.mydaycompanion.domain.tools.SetAlarmTool
import com.equisteg.mydaycompanion.domain.tools.SpeakTool
import com.equisteg.mydaycompanion.domain.tools.DeleteRoutineTool
import com.equisteg.mydaycompanion.domain.tools.MaterializeRoutinesTool
import com.equisteg.mydaycompanion.domain.tools.ReopenTaskTool
import com.equisteg.mydaycompanion.domain.tools.RollOverTasksTool
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

    /** What the agent remembers about the owner (F9); read by the Memory screen. Changes go through the orchestrator. */
    val memories: com.equisteg.mydaycompanion.domain.memory.MemoryStore

    /** Passphrases and decrypted backups waiting for the owner's approval; only tokens reach the tools. */
    val backupStaging: com.equisteg.mydaycompanion.domain.tools.BackupStaging

    /** Reads a backup file the owner picked; null when it can't be read or is over the size limit. */
    suspend fun readBackupFile(uri: android.net.Uri): ByteArray?

    /** Owner switches kept on the phone only: "show my calendar in plans", "use Gemini Nano". */
    val deviceSettings: DeviceSettings

    /** Status and download of the phone's on-device model (Gemini Nano), for Settings. */
    val localModel: LocalModelControl

    /** Adds routine tasks, replans and re-registers reminders; run on open, after restarts and each night. */
    val maintenance: DailyMaintenance

    /** Closes and deletes the database files and destroys their key (build doc §8.4). The app must restart afterwards. */
    fun wipeLocalStorage(): Boolean

    /** Asks the home-screen widget to read today again (F19). */
    fun refreshWidget() {}
}

/** Small owner switches that are not part of the encrypted data (they hold no personal content). */
interface DeviceSettings {
    var calendarInPlans: Boolean

    /** Use the phone's Gemini Nano to understand typed and spoken text, where the phone has it. */
    var smartModel: Boolean

    /** How many days History keeps (F16); one of [com.equisteg.mydaycompanion.domain.audit.HistoryRetention.CHOICES_DAYS]. */
    var historyKeepDays: Int

    /** Show what a reminder is for on the secure lock screen. Off by default (build doc §11). */
    var lockScreenText: Boolean

    /** Show task and event names on the home-screen widget (F19). Off by default: the widget is outside the app lock. */
    var widgetTitles: Boolean

    /** Light, dark or match the phone ([com.equisteg.mydaycompanion.ui.theme.ThemeMode] name), and wallpaper colours on Android 12+. */
    var themeMode: String
    var dynamicColor: Boolean

    /**
     * Wrong backup passphrases in a row, and until when Restore stays closed after ten of them
     * (build doc §7.3). Not part of [reset]: wiping the phone doesn't reopen guessing.
     */
    var restoreFailures: Int
    var restoreLockedUntilMillis: Long

    companion object {
        /** Match the phone; the UI maps unknown names to this too. */
        const val DEFAULT_THEME_MODE = "SYSTEM"
    }

    /** Back to the defaults; part of "Delete everything" and the panic wipe. */
    fun reset() {
        calendarInPlans = false
        smartModel = true
        historyKeepDays = com.equisteg.mydaycompanion.domain.audit.HistoryRetention.DEFAULT_DAYS
        lockScreenText = false
        widgetTitles = false
        themeMode = DEFAULT_THEME_MODE
        dynamicColor = false
    }
}

class SharedPrefsDeviceSettings(context: Context) : DeviceSettings {
    private val prefs = context.getSharedPreferences("device_settings", Context.MODE_PRIVATE)
    override var calendarInPlans: Boolean
        get() = prefs.getBoolean("calendar_in_plans", false)
        set(value) = prefs.edit().putBoolean("calendar_in_plans", value).apply()
    override var smartModel: Boolean
        get() = prefs.getBoolean("smart_model", true)
        set(value) = prefs.edit().putBoolean("smart_model", value).apply()
    override var historyKeepDays: Int
        get() = prefs.getInt("history_keep_days", com.equisteg.mydaycompanion.domain.audit.HistoryRetention.DEFAULT_DAYS)
            .takeIf { it in com.equisteg.mydaycompanion.domain.audit.HistoryRetention.CHOICES_DAYS }
            ?: com.equisteg.mydaycompanion.domain.audit.HistoryRetention.DEFAULT_DAYS
        set(value) = prefs.edit().putInt("history_keep_days", value).apply()
    override var lockScreenText: Boolean
        get() = prefs.getBoolean("lock_screen_text", false)
        set(value) = prefs.edit().putBoolean("lock_screen_text", value).apply()
    override var widgetTitles: Boolean
        get() = prefs.getBoolean("widget_titles", false)
        set(value) = prefs.edit().putBoolean("widget_titles", value).apply()
    override var themeMode: String
        get() = prefs.getString("theme_mode", null) ?: DeviceSettings.DEFAULT_THEME_MODE
        set(value) = prefs.edit().putString("theme_mode", value).apply()
    override var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", false)
        set(value) = prefs.edit().putBoolean("dynamic_color", value).apply()
    override var restoreFailures: Int
        get() = prefs.getInt("restore_failures", 0)
        set(value) { prefs.edit().putInt("restore_failures", value).commit() }
    override var restoreLockedUntilMillis: Long
        get() = prefs.getLong("restore_locked_until", 0L)
        set(value) { prefs.edit().putLong("restore_locked_until", value).commit() }
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
    override val memories by lazy { RoomMemoryStore(db) }
    override val audit by lazy { RoomAuditLog(db) }
    override val preferences by lazy { RoomPreferencesStore(db) }
    override val exportTargets by lazy { ContentResolverExportTargets(appContext.contentResolver) }
    override val security by lazy { RoomSecurityStore(db) }
    override val challenges by lazy { ChallengeVerifier(security, clock::instant) }
    private val scheduler by lazy { AlarmNotificationScheduler(appContext, clock) }
    override val deviceSettings by lazy { SharedPrefsDeviceSettings(appContext) }
    override val backupStaging by lazy { com.equisteg.mydaycompanion.domain.tools.BackupStaging(clock) }

    override suspend fun readBackupFile(uri: android.net.Uri): ByteArray? = readLimited(appContext.contentResolver, uri)
    private val calendar by lazy {
        ContentResolverCalendarProvider(appContext, { clock.zone }) { flags.isEnabled(Flag.CALENDAR_READ) && deviceSettings.calendarInPlans }
    }
    private val nano by lazy { MlKitNanoEngine() }
    override val localModel: LocalModelControl get() = nano
    private val launcher by lazy { AndroidDeviceActionLauncher(appContext) { clock.zone } }
    private val speaker by lazy { AndroidSpeaker(appContext) }
    private val permissionChecker by lazy { AndroidPermissionChecker(appContext) }
    override val maintenance by lazy {
        DailyMaintenance(
            orchestrator, tasks, plans, routines, clock,
            remindersReady = { flags.isEnabled(Flag.REMINDERS) && permissionChecker.isGranted(SyncRemindersTool.POST_NOTIFICATIONS) },
            afterRun = {
                scheduler.scheduleMaintenance()
                refreshWidget()
            },
            memories = memories,
            proposeHabits = { flags.isEnabled(Flag.MEMORY) },
            audit = audit,
            historyKeepDays = { deviceSettings.historyKeepDays },
        )
    }

    override fun wipeLocalStorage(): Boolean = com.equisteg.mydaycompanion.widget.TodayWidgetProvider.whileWiping(appContext) {
        runCatching { scheduler.cancelEverything(); deviceSettings.reset() }.isSuccess and
            LocalStorageWiper(appContext, keys) { if (dbHolder.isInitialized()) dbHolder.value else null }.wipe()
    }

    override fun refreshWidget() = com.equisteg.mydaycompanion.widget.TodayWidgetProvider.refresh(appContext)

    override val orchestrator: AgentOrchestrator by lazy {
        val ids = IdGenerator.RANDOM
        val planner = DeterministicPlanner()
        val rules = RuleBasedTaskExtractor()
        val export = DataExportService(tasks, events, plans, routines, memories, audit, preferences, clock)
        val deletion = DataDeletionService(
            stores = listOf(tasks, events, plans, routines, memories, audit, preferences, security),
            platformWipe = {
                scheduler.cancelAll()
                deviceSettings.reset()
            },
        )
        AgentOrchestrator(
            router = AiInferenceRouter(
                providers = listOf(OnDeviceLlmProvider(nano) { flags.isEnabled(Flag.LOCAL_LLM) && deviceSettings.smartModel }, rules),
                fallback = rules,
            ),
            contextBuilder = ContextBuilder(),
            planner = planner,
            registry = ToolRegistry(
                listOf(
                    CreateTasksTool(tasks, events, ids, clock),
                    GeneratePlanTool(tasks, events, plans, preferences, planner, calendar),
                    CompleteTaskTool(tasks, clock),
                    ReopenTaskTool(tasks, clock),
                    RollOverTasksTool(tasks, clock),
                    UpdateTaskTool(tasks, clock),
                    DeleteTaskTool(tasks),
                    AddSubtaskTool(tasks, ids, clock),
                    UpdateSettingsTool(preferences),
                    SyncRemindersTool(plans, tasks, routines, preferences, scheduler, clock),
                    CreateRoutineTool(routines, ids, clock),
                    UpdateRoutineTool(routines),
                    DeleteRoutineTool(routines),
                    MaterializeRoutinesTool(routines, tasks, clock),
                    SetAlarmTool(launcher),
                    DialTool(launcher),
                    ComposeMessageTool(launcher),
                    NavigateTool(launcher),
                    CalendarInsertTool(launcher),
                    SpeakTool(speaker),
                    ExportDataTool(export, exportTargets),
                    DeleteAllDataTool(deletion),
                    UpdateSecuritySettingsTool(security),
                    SetChallengeQuestionsTool(security),
                    com.equisteg.mydaycompanion.domain.tools.ProposeMemoryTool(memories, ids, clock),
                    com.equisteg.mydaycompanion.domain.tools.AddMemoryTool(memories, ids, clock),
                    com.equisteg.mydaycompanion.domain.tools.ActivateMemoryTool(memories, clock),
                    com.equisteg.mydaycompanion.domain.tools.RejectMemoryTool(memories, clock),
                    com.equisteg.mydaycompanion.domain.tools.DeleteMemoryTool(memories),
                    com.equisteg.mydaycompanion.domain.tools.PurgeExpiredMemoriesTool(memories, clock),
                    com.equisteg.mydaycompanion.domain.tools.PruneHistoryTool(audit, clock),
                    com.equisteg.mydaycompanion.domain.tools.CreateBackupTool(com.equisteg.mydaycompanion.domain.backup.BackupService(export, security), exportTargets, backupStaging),
                    com.equisteg.mydaycompanion.domain.tools.RestoreBackupTool(backupStaging, tasks, events, plans, routines, memories, audit, preferences, security, com.equisteg.mydaycompanion.data.repository.RoomStoreTransactions(db)),
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

/** Reads at most [com.equisteg.mydaycompanion.domain.backup.BackupCodec.MAX_FILE_BYTES] + 1 bytes, so an oversized file is refused without loading it all. */
internal suspend fun readLimited(resolver: android.content.ContentResolver, uri: android.net.Uri): ByteArray? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val limit = com.equisteg.mydaycompanion.domain.backup.BackupCodec.MAX_FILE_BYTES
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > limit) break
                }
                out.toByteArray()
            }
        }.getOrNull()
    }
