package com.equisteg.mydaycompanion.ui

import com.equisteg.mydaycompanion.domain.ai.LocalModelStatus
import com.equisteg.mydaycompanion.domain.ai.ModelDownload
import kotlinx.coroutines.CancellationException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.equisteg.mydaycompanion.AppServices
import com.equisteg.mydaycompanion.domain.agent.AgentProposal
import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.audit.HistoryFilter
import com.equisteg.mydaycompanion.domain.audit.HistoryRetention
import com.equisteg.mydaycompanion.domain.backup.BackupCodec
import com.equisteg.mydaycompanion.domain.backup.BackupRead
import com.equisteg.mydaycompanion.domain.backup.BackupReader
import com.equisteg.mydaycompanion.domain.backup.Decrypted
import com.equisteg.mydaycompanion.domain.tools.CreateBackupTool
import com.equisteg.mydaycompanion.domain.tools.RestoreBackupTool
import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemoryStatus
import com.equisteg.mydaycompanion.domain.tools.AddMemoryTool
import com.equisteg.mydaycompanion.domain.tools.OneMemoryTool
import com.equisteg.mydaycompanion.domain.tools.PruneHistoryTool
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.reminders.DailyBrief
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.security.ApprovalRules
import com.equisteg.mydaycompanion.domain.security.ApprovalStep
import com.equisteg.mydaycompanion.domain.security.ChallengeGate
import com.equisteg.mydaycompanion.domain.security.ChallengeHasher
import com.equisteg.mydaycompanion.domain.security.ChallengeResult
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import com.equisteg.mydaycompanion.domain.tools.CreateRoutineTool
import com.equisteg.mydaycompanion.domain.tools.SetChallengeQuestionsTool
import com.equisteg.mydaycompanion.domain.tools.UpdateRoutineTool
import com.equisteg.mydaycompanion.domain.tools.UpdateSettingsTool
import com.equisteg.mydaycompanion.domain.tools.UpdateTaskTool
import com.equisteg.mydaycompanion.domain.tools.UpdateSecuritySettingsTool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * A privileged action waiting for the owner. [steps] are the approval steps still to complete, in
 * order (build doc §6.1): a tap on Confirm, then the device credential, then a challenge answer.
 */
data class PendingConfirmation(
    val action: ProposedAction,
    val fingerprint: String,
    val title: String,
    val body: String,
    val steps: List<ApprovalStep> = listOf(ApprovalStep.TAP),
)

enum class AuthPurpose { UNLOCK, APPROVE }

/** Asks the activity to show the system fingerprint/face/PIN prompt. */
data class AuthRequest(val purpose: AuthPurpose, val title: String, val subtitle: String)

enum class AuthOutcome { SUCCEEDED, CANCELLED, UNAVAILABLE }

data class ChallengePrompt(val questionId: String, val question: String, val error: String? = null)

/** The passphrase dialog for a new backup, or for opening one the owner picked (build doc §7.2, §7.3). */
data class BackupPrompt(val kind: Kind, val error: String? = null) {
    enum class Kind { CREATE, RESTORE }
}

data class UiState(
    val input: String = "",
    val busy: Boolean = false,
    val proposal: AgentProposal.Ready? = null,
    val message: String? = null,
    val selectedDate: LocalDate,
    val pending: PendingConfirmation? = null,
    val history: List<AuditEntry> = emptyList(),
    /** True while the app lock screen covers everything (build doc §8). */
    val locked: Boolean = false,
    val authRequest: AuthRequest? = null,
    val challenge: ChallengePrompt? = null,
    val security: SecuritySettings = SecuritySettings(),
    val hasChallenges: Boolean = false,
    /** Local data and keys were destroyed; the activity restarts the app. */
    val wiped: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    /** False until preferences are read, so first-run setup does not flash for returning owners. */
    val preferencesLoaded: Boolean = false,
    val calendarInPlans: Boolean = false,
    /** The owner's "use Gemini Nano" switch and what the phone says about the model (null while checking). */
    val smartModel: Boolean = true,
    val modelStatus: LocalModelStatus? = null,
    /** Shown under the switch while the phone downloads the model. */
    val modelDownload: String? = null,
    /** Proposed and kept memories, newest first; turned-down ones are not shown (F9). */
    val memories: List<MemoryItem> = emptyList(),
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val historyKeepDays: Int = HistoryRetention.DEFAULT_DAYS,
    /** "Show reminder text on the lock screen" (off by default). */
    val lockScreenText: Boolean = false,
    val backupPrompt: BackupPrompt? = null,
) {
    val proposedMemories: List<MemoryItem> get() = memories.filter { it.status == MemoryStatus.PROPOSED }
    val keptMemories: List<MemoryItem> get() = memories.filter { it.status == MemoryStatus.ACTIVE }
    val shownHistory: List<AuditEntry> get() = history.filter(historyFilter::matches)

    val showOnboarding: Boolean get() = preferencesLoaded && !preferences.onboardingDone
}

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * [background] runs hashing and file deletion off the main thread; tests pass a dispatcher that
 * stays on the test thread so Robolectric's paused main looper never waits on another thread.
 */
class MainViewModel(
    private val services: AppServices,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // Starts locked so nothing shows before the security settings are read; unlocks at once when the app lock is off.
    private val _state = MutableStateFlow(UiState(selectedDate = LocalDate.now(services.clock), locked = true))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The LOCAL_LLM flag hides the Gemini Nano settings as well as turning the model off. */
    val smartModelAllowed: Boolean get() = services.flags.isEnabled(com.equisteg.mydaycompanion.domain.config.Flag.LOCAL_LLM)

    /** The MEMORY switch stops new memories; the owner can still see and delete what is stored. */
    val memoryEnabled: Boolean get() = services.flags.isEnabled(com.equisteg.mydaycompanion.domain.config.Flag.MEMORY)

    /** The VOICE kill switch covers the microphone as well as spoken replies. */
    val voiceEnabled: Boolean get() = services.flags.isEnabled(com.equisteg.mydaycompanion.domain.config.Flag.VOICE)

    private val selectedDate = MutableStateFlow(_state.value.selectedDate)

    val plan: StateFlow<DayPlan?> = selectedDate
        .flatMapLatest { services.plans.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tasks: StateFlow<List<Task>> = services.tasks.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routines: StateFlow<List<Routine>> = services.routines.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var backgroundedAt: Instant? = null

    /** Where the next backup goes (an export-target token), and the backup file picked for restore. Kept out of [state]. */
    private var backupTarget: String? = null
    private var backupFile: ByteArray? = null

    init {
        viewModelScope.launch {
            val settings = services.security.settings()
            val prefs = services.preferences.get()
            _state.update {
                it.copy(
                    security = settings,
                    hasChallenges = services.challenges.hasChallenges(),
                    locked = settings.appLockEnabled,
                    preferences = prefs,
                    preferencesLoaded = true,
                    calendarInPlans = services.deviceSettings.calendarInPlans,
                    smartModel = services.deviceSettings.smartModel,
                    historyKeepDays = services.deviceSettings.historyKeepDays,
                    lockScreenText = services.deviceSettings.lockScreenText,
                )
            }
            // ML Kit is not touched at all while the flag or the owner's switch is off.
            if (smartModelAllowed && services.deviceSettings.smartModel) refreshModelStatus()
            // Today's routine tasks and reminders; also re-run each night by the maintenance alarm.
            if (prefs.onboardingDone) services.maintenance.run()
            loadMemories()
        }
    }

    fun refreshMemories() {
        viewModelScope.launch { loadMemories() }
    }

    private suspend fun loadMemories() {
        val items = services.memories.all().filter { it.status != MemoryStatus.REJECTED && it.deletedAt == null }.sortedByDescending { it.updatedAt }
        _state.update { it.copy(memories = items) }
    }

    /** The owner typed something for the agent to remember. Private ones are never given to the model. */
    fun onAddMemory(content: String, private: Boolean) = launchBusy {
        val action = ProposedAction("memory.add", AddMemoryTool.args(content, private))
        val r = services.orchestrator.execute(action, Requester.USER)
        if (r is ExecutionResult.NeedsConfirmation) {
            // Re-typing a private memory replaces it, so the owner's chosen verification comes first.
            val s = _state.value
            val steps = ApprovalRules.stepsFor(r.riskLevel, personal = r.personal, settings = s.security, hasChallenges = s.hasChallenges)
            advance(PendingConfirmation(action, r.actionFingerprint, "Replace a private memory?", "Your private memory with the same words is replaced by this one.", steps))
            return@launchBusy
        }
        _state.update {
            it.copy(
                message = when (r) {
                    is ExecutionResult.Done -> "Remembered."
                    is ExecutionResult.Denied -> if (r.reason == "INVALID_ARGUMENTS") {
                        "That can't be remembered. Keep it short, and leave out passwords, PINs, links and rules for the app."
                    } else {
                        describe(r)
                    }
                    else -> describe(r)
                },
            )
        }
        loadMemories()
    }

    /** "Keep" on a suggestion. The tap is the approval; a private memory also needs the owner's verification. */
    fun onKeepMemory(memoryId: String) = launchBusy {
        val action = ProposedAction("memory.activate", OneMemoryTool.args(memoryId))
        when (val r = services.orchestrator.execute(action, Requester.USER)) {
            is ExecutionResult.NeedsConfirmation -> {
                val s = _state.value
                val steps = ApprovalRules.stepsFor(r.riskLevel, personal = r.personal, settings = s.security, hasChallenges = s.hasChallenges) - ApprovalStep.TAP
                advance(PendingConfirmation(action, r.actionFingerprint, "Keep this memory?", "", steps))
            }
            else -> {
                _state.update { it.copy(message = describe(r)) }
                loadMemories()
            }
        }
    }

    /** "No thanks" on a suggestion: it is not used and not suggested again. */
    fun onRejectMemory(memoryId: String) = launchBusy {
        val r = services.orchestrator.execute(ProposedAction("memory.reject", OneMemoryTool.args(memoryId)), Requester.USER)
        if (r !is ExecutionResult.Done) _state.update { it.copy(message = describe(r)) }
        loadMemories()
    }

    fun onDeleteMemory(memoryId: String) = launchBusy {
        request(ProposedAction("memory.delete", OneMemoryTool.args(memoryId)), "Forget this?", "The agent stops using it, and it is deleted from this phone.")
    }

    fun onHistoryFilter(filter: HistoryFilter) = _state.update { it.copy(historyFilter = filter) }

    /**
     * How long History keeps entries. A longer period applies at once; a shorter one removes older
     * entries now, which can't be undone, so the owner confirms it first when there are any.
     */
    fun onHistoryKeepDays(days: Int) = launchBusy {
        if (days !in HistoryRetention.CHOICES_DAYS) return@launchBusy
        val action = ProposedAction("history.prune", PruneHistoryTool.args(days))
        val removing = services.audit.countBefore(PruneHistoryTool(services.audit, services.clock).cutoff(days))
        if (removing == 0) {
            applyHistoryKeepDays(days)
            return@launchBusy
        }
        advance(
            PendingConfirmation(
                action, services.orchestrator.fingerprint(action), "Remove older history?",
                "$removing ${if (removing == 1) "entry" else "entries"} older than $days days will be deleted. This can't be undone.",
            ),
        )
    }

    private fun applyHistoryKeepDays(days: Int) {
        services.deviceSettings.historyKeepDays = days
        _state.update { it.copy(historyKeepDays = days) }
    }

    /** Whether reminders show what they are for on the secure lock screen. */
    fun onLockScreenText(show: Boolean) {
        services.deviceSettings.lockScreenText = show
        _state.update { it.copy(lockScreenText = show) }
    }

    fun onInputChange(text: String) = _state.update { it.copy(input = text, message = null) }

    fun onSubmit() = launchBusy {
        when (val p = services.orchestrator.propose(_state.value.input)) {
            is AgentProposal.Ready -> _state.update { it.copy(proposal = p, message = null) }
            is AgentProposal.NotUnderstood -> _state.update { it.copy(message = p.reason) }
            AgentProposal.Empty -> Unit
        }
    }

    /** Words from press-to-talk go into the input box; nothing runs until the owner taps "Plan it". */
    fun onVoiceText(text: String) = _state.update { it.copy(input = listOf(it.input.trim(), text.trim()).filter(String::isNotEmpty).joinToString(" "), message = null) }

    fun onInfo(text: String) = _state.update { it.copy(message = text) }

    /** "Read my day": a short spoken summary of the shown day. Personal task titles are never read out. */
    fun onReadMyDay() = launchBusy {
        val date = _state.value.selectedDate
        val today = LocalDate.now(services.clock)
        val label = when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            today.minusDays(1) -> "yesterday"
            else -> "on " + date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        }
        val day = services.plans.get(date)
        val text = day?.let { DailyBrief.summary(it, services.tasks.all(), label) } ?: "Nothing planned yet."
        speak(text, quietly = false)
    }

    private suspend fun speak(text: String, quietly: Boolean) {
        val r = services.orchestrator.execute(ProposedAction("voice.speak", args("text" to text)), Requester.USER)
        if (r !is ExecutionResult.Done && !quietly) _state.update { it.copy(message = "Reading aloud isn't available. Check the phone's text-to-speech settings.") }
    }

    /**
     * A device action from the task screen (alarm, call, message draft, directions, calendar). All
     * are L3: a Confirm tap and then the fingerprint, face or screen lock, every time.
     */
    fun onDeviceAction(toolId: String, actionArgs: JsonObject, title: String) = launchBusy {
        request(ProposedAction(toolId, actionArgs), title, "Another app opens with this filled in. You finish it there; nothing is sent by MyDayCompanion.")
    }

    /** "Use Gemini Nano". Turning it on checks the phone's model; off, ML Kit isn't called at all. */
    fun onSmartModel(enabled: Boolean) {
        services.deviceSettings.smartModel = enabled
        _state.update { it.copy(smartModel = enabled, modelStatus = null, modelDownload = null) }
        if (enabled && smartModelAllowed) viewModelScope.launch { refreshModelStatus() }
    }

    /** Asks the phone's AICore service to download Gemini Nano; this app itself never goes online. */
    fun onDownloadModel() {
        viewModelScope.launch {
            _state.update { it.copy(modelDownload = "Starting the download…", modelStatus = LocalModelStatus.DOWNLOADING) }
            services.localModel.download().collect { step ->
                val text = when (step) {
                    ModelDownload.Started -> "Starting the download…"
                    is ModelDownload.Progress -> "Downloading… ${step.bytes / 1_000_000} MB so far"
                    ModelDownload.Done -> null
                    ModelDownload.Failed -> "The download stopped. Try again on Wi-Fi with enough free space."
                }
                _state.update { it.copy(modelDownload = text) }
            }
            refreshModelStatus()
        }
    }

    private suspend fun refreshModelStatus() {
        val status = try {
            services.localModel.status()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocalModelStatus.UNAVAILABLE
        }
        _state.update { it.copy(modelStatus = status) }
    }

    /** "Show my calendar in plans". Turning it on asks for calendar access first (the activity does that). */
    fun onCalendarInPlans(enabled: Boolean) = launchBusy {
        services.deviceSettings.calendarInPlans = enabled
        _state.update { it.copy(calendarInPlans = enabled) }
        replan()
    }

    /** The owner allowed notifications; register today's reminders now rather than at the next change. */
    fun onNotificationsAllowed() = launchBusy { services.maintenance.syncReminders() }

    fun onDiscardProposal() = _state.update { it.copy(proposal = null) }

    /** The user reviewed the proposed tasks and plan and tapped "Add to my day". */
    fun onAcceptProposal() = launchBusy {
        val proposal = _state.value.proposal ?: return@launchBusy
        var routineProblem: String? = null
        for (action in proposal.actions) {
            val result = runWithUserApproval(action, Requester.AGENT)
            if (result is ExecutionResult.Done) continue
            // The tasks are already saved by now; a routine that can't be saved must not leave the
            // proposal on screen, or a second tap would add the tasks again.
            if (action.toolId == "routines.create") {
                routineProblem = "The tasks were added, but the repeat couldn't be saved (${describe(result).lowercase()})."
                continue
            }
            _state.update { it.copy(message = describe(result)) }
            return@launchBusy
        }
        services.maintenance.run()
        selectDate(proposal.previewPlans.first().date)
        val days = proposal.previewPlans.size
        val done = routineProblem ?: if (days > 1) "Added to $days days." else "Added to your day."
        _state.update { it.copy(proposal = null, input = "", message = done) }
        if (_state.value.preferences.speechOutputEnabled) speak(done, quietly = true)
    }

    /** Ticks a task done, or back to not done. */
    fun onToggleTask(taskId: String) = launchBusy {
        val done = services.tasks.get(taskId)?.status == TaskStatus.DONE
        val action = ProposedAction(if (done) "tasks.reopen" else "tasks.complete", args("taskId" to taskId))
        runUserAction(action, if (done) "Mark as not done?" else "Mark as done?", "This is a personal task.")
    }

    /** The owner saved the edit form. Every field is sent, so the approval covers exactly what was on screen. */
    fun onSaveTask(task: Task) = launchBusy {
        runUserAction(ProposedAction("tasks.update", UpdateTaskTool.args(task)), "Save changes?", "This is a personal task.")
    }

    fun onDeleteTask(taskId: String) = launchBusy {
        val subtasks = services.tasks.all().count { it.parentId == taskId }
        if (services.tasks.get(taskId)?.routineId != null) {
            request(ProposedAction("tasks.delete", args("taskId" to taskId)), "Skip today?", "This one repeats. It is skipped today and comes back on its next day. Change or stop it in Routines.")
            return@launchBusy
        }
        request(
            ProposedAction("tasks.delete", args("taskId" to taskId)),
            "Delete this task?",
            if (subtasks > 0) "The task and its $subtasks subtasks will be deleted." else "The task will be deleted.",
        )
    }

    /** New routine from the Routines screen. The owner typed it, so it runs at once. */
    fun onCreateRoutine(routine: Routine) = launchBusy {
        val r = services.orchestrator.execute(ProposedAction("routines.create", CreateRoutineTool.args(routine)), Requester.USER)
        if (r is ExecutionResult.Done) {
            services.maintenance.run()
            _state.update { it.copy(message = "Routine saved.") }
        } else {
            _state.update { it.copy(message = describe(r)) }
        }
    }

    /** Edit, pause/resume, or switch reminders for a routine. */
    fun onUpdateRoutine(routine: Routine) = launchBusy {
        val r = services.orchestrator.execute(ProposedAction("routines.update", UpdateRoutineTool.args(routine)), Requester.USER)
        if (r is ExecutionResult.Done) services.maintenance.run() else _state.update { it.copy(message = describe(r)) }
    }

    fun onDeleteRoutine(routineId: String) = launchBusy {
        request(ProposedAction("routines.delete", args("routineId" to routineId)), "Delete this routine?", "It stops adding tasks. Tasks it already added stay.")
    }

    fun onAddSubtask(parentId: String, title: String, durationMinutes: Int) = launchBusy {
        val action = ProposedAction(
            "tasks.addSubtask",
            JsonObject(mapOf("parentId" to JsonPrimitive(parentId), "title" to JsonPrimitive(title.trim()), "durationMinutes" to JsonPrimitive(durationMinutes))),
        )
        runUserAction(action, "Add this step?", "This is a personal task.")
    }

    /** Day window, buffer, date format and the rest of "Your day". Saved at once; the plan is rebuilt. */
    fun onPreferencesChange(preferences: UserPreferences) = launchBusy {
        val r = services.orchestrator.execute(ProposedAction("settings.update", UpdateSettingsTool.args(preferences)), Requester.USER)
        if (r is ExecutionResult.Done) {
            _state.update { it.copy(preferences = services.preferences.get()) }
            replan()
        } else {
            _state.update { it.copy(message = "Those settings don't work together. The day needs at least two hours.") }
        }
    }

    fun onFinishOnboarding(preferences: UserPreferences) = onPreferencesChange(preferences.copy(onboardingDone = true))

    fun onReplan() = launchBusy { replan() }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        _state.update { it.copy(selectedDate = date) }
    }

    fun onExportDestinationChosen(uri: Uri) = launchBusy {
        val token = services.exportTargets.register(uri)
        request(
            ProposedAction("data.export", args("target" to token)),
            "Export your data?",
            "Everything MyDayCompanion stores will be written to the file you chose. The file is not encrypted.",
        )
    }

    /** The name offered in the system "save as" screen: mydaycompanion-YYYYMMDD.mdcbackup. */
    fun backupFileName(): String =
        "mydaycompanion-${LocalDate.now(services.clock).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)}.${BackupCodec.FILE_EXTENSION}"

    /** The owner picked where the backup goes; next they choose its passphrase. */
    fun onBackupDestinationChosen(uri: Uri) {
        backupTarget = services.exportTargets.register(uri)
        _state.update { it.copy(backupPrompt = BackupPrompt(BackupPrompt.Kind.CREATE)) }
    }

    /**
     * The passphrase is staged and only a random token goes into the action, so it never reaches
     * the approval fingerprint or History. Both arrays are cleared here.
     */
    fun onBackupPassphrase(passphrase: CharArray, repeat: CharArray) = launchBusy {
        try {
            val error = when {
                passphrase.size < BackupCodec.MIN_PASSPHRASE_CHARS -> "Use at least ${BackupCodec.MIN_PASSPHRASE_CHARS} characters."
                !passphrase.contentEquals(repeat) -> "The two passphrases don't match."
                else -> null
            }
            if (error != null) {
                _state.update { it.copy(backupPrompt = BackupPrompt(BackupPrompt.Kind.CREATE, error)) }
                return@launchBusy
            }
            val target = backupTarget
            backupTarget = null
            _state.update { it.copy(backupPrompt = null) }
            if (target == null) return@launchBusy
            val key = services.backupStaging.stagePassphrase(passphrase)
            request(
                ProposedAction("backup.create", CreateBackupTool.args(target, key)),
                "Back up your data?",
                "Everything is encrypted with your passphrase and saved to the file you chose. " +
                    "Without the passphrase nobody can open it, and it can't be recovered.",
            )
        } finally {
            passphrase.fill(' ')
            repeat.fill(' ')
        }
    }

    /** The owner picked a backup file (Settings, or first-run setup on a new phone). */
    fun onRestoreFileChosen(uri: Uri) = launchBusy {
        restoreLockedMessage()?.let { msg ->
            _state.update { it.copy(message = msg) }
            return@launchBusy
        }
        val bytes = services.readBackupFile(uri)
        when {
            bytes == null -> _state.update { it.copy(message = "That file couldn't be read.") }
            bytes.size > BackupCodec.MAX_FILE_BYTES -> _state.update { it.copy(message = "That file is too big to be a MyDayCompanion backup.") }
            else -> {
                backupFile = bytes
                _state.update { it.copy(backupPrompt = BackupPrompt(BackupPrompt.Kind.RESTORE)) }
            }
        }
    }

    /**
     * Opens the picked backup with [passphrase], checks every item in it, and asks the owner to
     * approve replacing everything (L4). Ten wrong passphrases close Restore for an hour.
     */
    fun onRestorePassphrase(passphrase: CharArray) = launchBusy {
        try {
            restoreLockedMessage()?.let { msg ->
                backupFile = null
                _state.update { it.copy(backupPrompt = null, message = msg) }
                return@launchBusy
            }
            val file = backupFile ?: return@launchBusy
            when (val opened = withContext(background) { BackupCodec().decrypt(file, passphrase) }) {
                Decrypted.WrongPassphraseOrTampered -> onWrongBackupPassphrase()
                is Decrypted.Unreadable -> {
                    backupFile = null
                    val msg = if (opened.reason == "UNSUPPORTED_VERSION") {
                        "This backup was made by a newer version of MyDayCompanion. Update the app, then try again."
                    } else {
                        "This isn't a MyDayCompanion backup, or it is damaged. Nothing was changed."
                    }
                    _state.update { it.copy(backupPrompt = null, message = msg) }
                }
                is Decrypted.Ok -> {
                    services.deviceSettings.restoreFailures = 0
                    backupFile = null
                    val read = withContext(background) { BackupReader.read(opened.plaintext).also { opened.plaintext.fill(0) } }
                    _state.update { it.copy(backupPrompt = null) }
                    when (read) {
                        is BackupRead.Invalid -> _state.update {
                            it.copy(message = "This backup holds something MyDayCompanion wouldn't have written, so nothing was restored.")
                        }
                        is BackupRead.Valid -> {
                            val c = read.contents
                            val day = c.createdAt.atZone(services.clock.zone).toLocalDate()
                            request(
                                ProposedAction("backup.restore", RestoreBackupTool.args(services.backupStaging.stageRestore(c), c.digest)),
                                "Replace everything with this backup?",
                                "Backup from $day: ${c.tasks.size} tasks, ${c.routines.size} routines, ${c.memories.size} memories. " +
                                    "Everything now on this phone is replaced. This cannot be undone.",
                            )
                        }
                    }
                }
            }
        } finally {
            passphrase.fill(' ')
        }
    }

    fun onCancelBackupPrompt() {
        backupTarget = null
        backupFile = null
        _state.update { it.copy(backupPrompt = null) }
    }

    private fun onWrongBackupPassphrase() {
        val settings = services.deviceSettings
        val failures = settings.restoreFailures + 1
        if (failures >= MAX_RESTORE_FAILURES) {
            settings.restoreFailures = 0
            settings.restoreLockedUntilMillis = services.clock.millis() + RESTORE_LOCK.toMillis()
            backupFile = null
            _state.update { it.copy(backupPrompt = null, message = restoreLockedMessage()) }
        } else {
            settings.restoreFailures = failures
            val left = MAX_RESTORE_FAILURES - failures
            _state.update {
                it.copy(backupPrompt = BackupPrompt(BackupPrompt.Kind.RESTORE, "That passphrase doesn't open this backup, or the file was changed. $left tries left."))
            }
        }
    }

    private fun restoreLockedMessage(): String? {
        val until = services.deviceSettings.restoreLockedUntilMillis
        if (until <= services.clock.millis()) return null
        val at = Instant.ofEpochMilli(until).atZone(services.clock.zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        return "Too many wrong passphrases. Restore opens again at $at."
    }

    /** Forgets a staged passphrase or backup when its approval is abandoned. */
    private fun discardStaged(pending: PendingConfirmation?) {
        if (pending == null || !pending.action.toolId.startsWith("backup.")) return
        listOf("key", "staged").forEach { k -> (pending.action.args[k] as? JsonPrimitive)?.content?.let(services.backupStaging::discard) }
    }

    /** A restore replaced every store: read everything again and re-register reminders and routines. */
    private suspend fun afterRestore() {
        reloadSecurity()
        _state.update { it.copy(preferences = services.preferences.get()) }
        loadMemories()
        services.maintenance.run()
        replan()
    }

    fun onDeleteEverything() = launchBusy {
        request(
            ProposedAction("data.deleteAll", args("scope" to "everything")),
            "Delete everything?",
            "All tasks, plans, memories, settings and history on this device will be permanently deleted. This cannot be undone.",
        )
    }

    /** The owner tapped Confirm in the dialog: move on to the next approval step. */
    fun onConfirmPending() = launchBusy {
        val pending = _state.value.pending ?: return@launchBusy
        advance(pending.copy(steps = pending.steps - ApprovalStep.TAP))
    }

    fun onCancelPending() {
        discardStaged(_state.value.pending)
        _state.update { it.copy(pending = null, authRequest = null, challenge = null) }
    }

    /** Result of the system prompt the activity showed for [UiState.authRequest]. */
    fun onDeviceAuthResult(outcome: AuthOutcome) = launchBusy {
        val request = _state.value.authRequest ?: return@launchBusy
        _state.update { it.copy(authRequest = null) }
        when (request.purpose) {
            AuthPurpose.UNLOCK -> when (outcome) {
                AuthOutcome.SUCCEEDED -> {
                    services.challenges.onDeviceCredentialPassed()
                    _state.update { it.copy(locked = false) }
                }
                AuthOutcome.UNAVAILABLE -> _state.update {
                    it.copy(locked = false, message = "This phone has no screen lock, so the app lock is off. Set a screen lock in Android settings to protect your agent.")
                }
                AuthOutcome.CANCELLED -> Unit
            }
            AuthPurpose.APPROVE -> {
                val pending = _state.value.pending ?: return@launchBusy
                when (outcome) {
                    AuthOutcome.SUCCEEDED -> {
                        services.challenges.onDeviceCredentialPassed()
                        advance(pending.copy(steps = pending.steps - ApprovalStep.DEVICE_CREDENTIAL))
                    }
                    AuthOutcome.CANCELLED -> {
                        discardStaged(pending)
                        _state.update { it.copy(pending = null, message = "Cancelled.") }
                    }
                    AuthOutcome.UNAVAILABLE ->
                        if (_state.value.hasChallenges) {
                            val rest = pending.steps - ApprovalStep.DEVICE_CREDENTIAL
                            advance(pending.copy(steps = if (ApprovalStep.CHALLENGE in rest) rest else rest + ApprovalStep.CHALLENGE))
                        } else {
                            _state.update {
                                it.copy(pending = null, message = "Set a screen lock on this phone (or challenge questions in Privacy) to approve this.")
                            }
                        }
                }
            }
        }
    }

    fun onChallengeAnswer(answer: String) = launchBusy {
        val prompt = _state.value.challenge ?: return@launchBusy
        val pending = _state.value.pending
        val result = withContext(background) { services.challenges.answer(prompt.questionId, answer) }
        when (result) {
            ChallengeResult.Correct -> {
                _state.update { it.copy(challenge = null) }
                if (pending != null) advance(pending.copy(steps = pending.steps - ApprovalStep.CHALLENGE))
            }
            is ChallengeResult.Wrong -> onChallengeGate(result.gate, prompt)
            is ChallengeResult.Blocked -> onChallengeGate(result.gate, prompt)
        }
    }

    fun onCancelChallenge() {
        discardStaged(_state.value.pending)
        _state.update { it.copy(challenge = null, pending = null) }
    }

    fun onUnlockRequested() = _state.update {
        it.copy(authRequest = AuthRequest(AuthPurpose.UNLOCK, "Unlock MyDayCompanion", "Use your fingerprint, face or screen lock"))
    }

    /** "Wipe now" from the lock screen, for a phone about to leave the owner's hands. Needs a challenge answer. */
    fun onPanicWipe() = launchBusy {
        if (!_state.value.hasChallenges) {
            _state.update { it.copy(message = "Set up challenge questions first, or unlock and use Delete everything.") }
            return@launchBusy
        }
        val action = ProposedAction("data.deleteAll", args("scope" to "everything"))
        val r = services.orchestrator.execute(action, Requester.USER)
        if (r is ExecutionResult.NeedsConfirmation) {
            advance(PendingConfirmation(action, r.actionFingerprint, "Wipe this agent?", "", listOf(ApprovalStep.CHALLENGE)))
        }
    }

    fun onAppBackground() {
        // The system credential screen sends the app to the background on older Android; that is not leaving the app.
        if (_state.value.authRequest != null) return
        backgroundedAt = services.clock.instant()
    }

    fun onAppForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val settings = _state.value.security
        if (settings.appLockEnabled && Duration.between(since, services.clock.instant()) >= Duration.ofMinutes(settings.lockAfterMinutes.toLong())) {
            discardStaged(_state.value.pending)
            onCancelBackupPrompt()
            _state.update { it.copy(locked = true, pending = null, challenge = null, proposal = null) }
        }
    }

    fun onSecuritySettingsChange(settings: SecuritySettings) = launchBusy {
        request(
            ProposedAction("security.updateSettings", UpdateSecuritySettingsTool.args(settings)),
            "Change security settings?",
            "This changes how MyDayCompanion protects your data. You will confirm with your screen lock.",
        )
    }

    /** Hashes the answers here so clear answers never reach the orchestrator, the fingerprint or the audit log. */
    fun onSaveChallenges(questionsAndAnswers: List<Pair<String, String>>) = launchBusy {
        val clean = questionsAndAnswers.map { it.first.trim() to it.second }
        val valid = clean.size == ChallengeHasher.QUESTION_COUNT &&
            clean.all { it.first.isNotEmpty() && it.first.length <= ChallengeHasher.MAX_QUESTION_CHARS && ChallengeHasher.isAcceptableAnswer(it.second) } &&
            clean.map { it.first.lowercase() }.toSet().size == clean.size
        if (!valid) {
            _state.update { it.copy(message = "Pick three different questions and give each an answer.") }
            return@launchBusy
        }
        val hashed = withContext(background) {
            clean.mapIndexed { i, (q, a) -> ChallengeHasher.hash("q${i + 1}", q, a) }
        }
        request(
            ProposedAction("security.setChallenges", SetChallengeQuestionsTool.args(hashed)),
            if (_state.value.hasChallenges) "Change challenge questions?" else "Save challenge questions?",
            "Answers are stored only as a one-way hash on this phone. Nobody, including Equisteg, can read them or recover them.",
        )
    }

    fun onRemoveChallenges() = launchBusy {
        request(
            ProposedAction("security.setChallenges", SetChallengeQuestionsTool.args(emptyList())),
            "Remove challenge questions?",
            "Personal and high-risk actions will then be approved with your screen lock only.",
        )
    }

    private suspend fun onChallengeGate(gate: ChallengeGate, prompt: ChallengePrompt) {
        when (gate) {
            ChallengeGate.Open -> _state.update { it.copy(challenge = prompt.copy(error = "That's not right. Try again.")) }
            is ChallengeGate.LockedUntil -> {
                val at = gate.until.atZone(services.clock.zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                _state.update { it.copy(challenge = null, pending = null, message = "Too many wrong answers. Try again after $at.") }
            }
            ChallengeGate.RequireCredential -> _state.update {
                it.copy(challenge = null, pending = null, locked = it.security.appLockEnabled || it.locked, message = "Too many wrong answers. Unlock with your screen lock.")
            }
            ChallengeGate.Wipe -> wipeEverything()
        }
    }

    /** Runs the next approval step, or the action itself once every step is done. */
    private suspend fun advance(pending: PendingConfirmation) {
        when (pending.steps.firstOrNull()) {
            ApprovalStep.TAP -> _state.update { it.copy(pending = pending) }
            ApprovalStep.DEVICE_CREDENTIAL -> _state.update {
                it.copy(pending = pending, authRequest = AuthRequest(AuthPurpose.APPROVE, "Approve: ${pending.title.removeSuffix("?")}", "Confirm it's you"))
            }
            ApprovalStep.CHALLENGE -> {
                val q = services.challenges.pickQuestion()
                if (q == null) {
                    advance(pending.copy(steps = pending.steps - ApprovalStep.CHALLENGE))
                } else {
                    when (val gate = services.challenges.gate()) {
                        ChallengeGate.Open -> _state.update { it.copy(pending = pending, challenge = ChallengePrompt(q.id, q.question)) }
                        else -> onChallengeGate(gate, ChallengePrompt(q.id, q.question))
                    }
                }
            }
            null -> {
                _state.update { it.copy(pending = null, challenge = null) }
                val confirmation = UserConfirmation(pending.fingerprint)
                // Key derivation and encryption take a second or two: keep them off the main thread.
                val r = if (pending.action.toolId.startsWith("backup.")) {
                    withContext(background) { services.orchestrator.execute(pending.action, Requester.USER, confirmation) }
                } else {
                    services.orchestrator.execute(pending.action, Requester.USER, confirmation)
                }
                if (r is ExecutionResult.Done && pending.action.toolId == "data.deleteAll") {
                    wipeEverything()
                    return
                }
                if (r is ExecutionResult.Done && pending.action.toolId.startsWith("security.")) reloadSecurity()
                if (r is ExecutionResult.Done && pending.action.toolId.startsWith("tasks.")) replanAfter(pending.action)
                if (r is ExecutionResult.Done && pending.action.toolId.startsWith("routines.")) services.maintenance.run()
                if (pending.action.toolId.startsWith("memory.")) loadMemories()
                if (r is ExecutionResult.Done && pending.action.toolId == "backup.restore") afterRestore()
                if (r is ExecutionResult.Done && pending.action.toolId == "history.prune") {
                    (pending.action.args["keepDays"] as? JsonPrimitive)?.content?.toIntOrNull()?.let(::applyHistoryKeepDays)
                }
                val done = r is ExecutionResult.Done
                val message = when {
                    done && pending.action.toolId == "memory.activate" -> "Kept. The agent will use it."
                    done && pending.action.toolId == "backup.create" -> "Backup saved. Keep the file and your passphrase somewhere safe."
                    done && pending.action.toolId == "backup.restore" -> "Restored. Your tasks, routines, memories and settings are back."
                    else -> describe(r)
                }
                _state.update { it.copy(message = message) }
                refreshHistory()
            }
        }
    }

    /**
     * Destroys local storage: after "Delete everything" ran and was verified through the
     * orchestrator, or when the owner's opt-in wipe-on-failure rule fired. Deleting the database
     * files and destroying their key leaves nothing readable, rows included.
     */
    private suspend fun wipeEverything() {
        withContext(background) { services.wipeLocalStorage() }
        _state.update { UiState(selectedDate = it.selectedDate, wiped = true, message = "Everything on this phone was deleted.") }
    }

    private suspend fun reloadSecurity() {
        val settings = services.security.settings()
        _state.update { it.copy(security = settings, hasChallenges = services.challenges.hasChallenges()) }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            val entries = services.audit.recent(HISTORY_SHOWN)
            _state.update { it.copy(history = entries) }
        }
    }

    private suspend fun request(action: ProposedAction, title: String, body: String) {
        when (val r = services.orchestrator.execute(action, Requester.USER)) {
            is ExecutionResult.NeedsConfirmation -> {
                val s = _state.value
                val steps = ApprovalRules.stepsFor(r.riskLevel, personal = r.personal, settings = s.security, hasChallenges = s.hasChallenges)
                advance(PendingConfirmation(action, r.actionFingerprint, title, body, steps))
            }
            else -> _state.update { it.copy(message = describe(r)) }
        }
    }

    /**
     * An everyday action the owner started: it runs at once unless the policy asks for approval
     * (a personal task), in which case the owner's chosen verification runs first.
     */
    private suspend fun runUserAction(action: ProposedAction, title: String, personalBody: String) {
        when (val r = services.orchestrator.execute(action, Requester.USER)) {
            is ExecutionResult.Done -> replanAfter(action)
            is ExecutionResult.NeedsConfirmation -> {
                val s = _state.value
                val steps = ApprovalRules.stepsFor(r.riskLevel, personal = r.personal, settings = s.security, hasChallenges = s.hasChallenges)
                advance(PendingConfirmation(action, r.actionFingerprint, title, personalBody, steps))
            }
            else -> _state.update { it.copy(message = describe(r)) }
        }
    }

    /**
     * Runs an assistant-proposed action the user has just approved on screen. The confirmation is
     * created here, from the user's tap, and covers exactly the arguments that were displayed.
     */
    private suspend fun runWithUserApproval(action: ProposedAction, requester: Requester): ExecutionResult {
        val first = services.orchestrator.execute(action, requester)
        return if (first is ExecutionResult.NeedsConfirmation) {
            services.orchestrator.execute(action, requester, UserConfirmation(first.actionFingerprint))
        } else {
            first
        }
    }

    /** Rebuilds the shown day, and the day a task was moved to. */
    private suspend fun replanAfter(action: ProposedAction) {
        replan()
        val movedTo = (action.args["date"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (movedTo != null && movedTo != _state.value.selectedDate) replan(movedTo)
    }

    private suspend fun replan(date: LocalDate = _state.value.selectedDate) {
        val pairs = buildList {
            add("date" to date.toString())
            if (date == LocalDate.now(services.clock)) add("notBefore" to LocalTime.now(services.clock).truncatedTo(ChronoUnit.MINUTES).toString())
        }
        val r = services.orchestrator.execute(ProposedAction("plan.generate", args(*pairs.toTypedArray())), Requester.USER)
        if (r !is ExecutionResult.Done) _state.update { it.copy(message = describe(r)) }
        // A changed plan changes when reminders fire. "Feature off" or "no permission" is fine here.
        services.maintenance.syncReminders()
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun args(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

    private fun describe(r: ExecutionResult): String = when (r) {
        is ExecutionResult.Done -> "Done."
        is ExecutionResult.NeedsConfirmation -> "Needs your confirmation."
        is ExecutionResult.Denied -> "Not allowed (${r.reason.lowercase().replace('_', ' ')})."
        is ExecutionResult.Failed -> "That didn't work (${r.reason.lowercase().replace('_', ' ')})."
    }

    companion object {
        /** Newest entries loaded for History; the filters apply to these. */
        const val HISTORY_SHOWN = 300

        /** Wrong backup passphrases in a row before Restore closes for [RESTORE_LOCK] (build doc §7.3). */
        const val MAX_RESTORE_FAILURES = 10
        val RESTORE_LOCK: Duration = Duration.ofHours(1)

        fun factory(services: AppServices) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(services) as T
        }
    }
}
