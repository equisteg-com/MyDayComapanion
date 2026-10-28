package com.equisteg.mydaycompanion.domain.agent

import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.memory.HabitObserver
import com.equisteg.mydaycompanion.domain.memory.MemoryPolicy
import com.equisteg.mydaycompanion.domain.memory.MemoryStore
import com.equisteg.mydaycompanion.domain.memory.MemoryType
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.tools.ProposeMemoryTool
import com.equisteg.mydaycompanion.domain.tools.PruneHistoryTool
import com.equisteg.mydaycompanion.domain.repository.PlanRepository
import com.equisteg.mydaycompanion.domain.repository.RoutineRepository
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.routines.RoutineExpander
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * The work the app does on its own: when it opens, after a restart, at the start of each day and
 * after a plan changes. It adds the day's routine tasks, replans a day that gained tasks,
 * re-registers reminders, proposes habits it noticed as memories, and applies the memory and
 * History retention periods. Every step is an ordinary tool call through [AgentOrchestrator.execute],
 * so it is checked by the policy and audited like any other. It runs as [Requester.SYSTEM], which the
 * policy limits to L0/L1 tools that never need a confirmation. Runs never overlap: the app start,
 * a restart broadcast and the nightly alarm can all fire at once, so a lock keeps them in turn.
 */
class DailyMaintenance(
    private val orchestrator: AgentOrchestrator,
    private val tasks: TaskRepository,
    private val plans: PlanRepository,
    private val routines: RoutineRepository,
    private val clock: Clock,
    /** False while reminders are switched off or notifications are not allowed; skips a call the policy would refuse anyway. */
    private val remindersReady: () -> Boolean = { true },
    /** Called after each full run; the app uses it to set the next nightly alarm, with or without reminders. */
    private val afterRun: suspend () -> Unit = {},
    /** Memory housekeeping (F9) runs only when the store is given. */
    private val memories: MemoryStore? = null,
    /** False while the memory feature is off; habits are then not proposed (expired memories are still removed). */
    private val proposeHabits: () -> Boolean = { true },
    private val habits: HabitObserver = HabitObserver(),
    /** History retention (F16): the owner's choice in days, applied when [audit] is given. */
    private val audit: AuditLog? = null,
    private val historyKeepDays: () -> Int = { com.equisteg.mydaycompanion.domain.audit.HistoryRetention.DEFAULT_DAYS },
) {
    private val lock = Mutex()

    data class Report(
        val routineTasksAdded: Int,
        val replanned: List<LocalDate>,
        val reminders: ExecutionResult?,
        val memoriesProposed: Int = 0,
    )

    /** Runs for today and tomorrow. Results that are only "feature off" or "permission missing" are normal, not errors. */
    suspend fun run(): Report = lock.withLock { runLocked().also { afterRun() } }

    private suspend fun runLocked(): Report {
        val today = LocalDate.now(clock)
        var added = 0
        val replanned = mutableListOf<LocalDate>()
        for (date in listOf(today, today.plusDays(1))) {
            val before = tasks.all().count { it.date == date }
            // Only call the tool when a routine is actually missing its task, so History is not filled with no-op runs.
            if (RoutineExpander.tasksFor(date, routines.all(), tasks.all(), clock.instant()).isNotEmpty()) {
                orchestrator.execute(ProposedAction("routines.materialize", obj("date" to date.toString())), Requester.SYSTEM)
            }
            val after = tasks.all().count { it.date == date }
            added += after - before
            if (after > before || (after > 0 && plans.get(date) == null)) {
                val notBefore = if (date == today) LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES).toString() else null
                val args = if (notBefore != null) obj("date" to date.toString(), "notBefore" to notBefore) else obj("date" to date.toString())
                if (orchestrator.execute(ProposedAction("plan.generate", args), Requester.SYSTEM) is ExecutionResult.Done) replanned += date
            }
        }
        val reminders = syncLocked()
        val proposed = housekeeping()
        return Report(added, replanned, reminders, proposed)
    }

    /**
     * Each step is called only when it has something to do, so History is not filled with empty runs.
     * Returns how many memories were proposed.
     */
    private suspend fun housekeeping(): Int {
        val now = clock.instant()
        val log = audit
        if (log != null) {
            val keep = historyKeepDays()
            val prune = PruneHistoryTool(log, clock)
            if (log.countBefore(prune.cutoff(keep)) > 0) {
                orchestrator.execute(ProposedAction("history.prune", PruneHistoryTool.args(keep)), Requester.SYSTEM)
            }
        }
        val store = memories ?: return 0
        val policy = MemoryPolicy()
        if (store.all().any { policy.isExpired(it, now) }) {
            orchestrator.execute(ProposedAction("memory.purgeExpired", JsonObject(emptyMap())), Requester.SYSTEM)
        }
        if (!proposeHabits()) return 0
        return habits.habits(tasks.all(), store.all(), now, clock.zone).count { h ->
            orchestrator.execute(
                ProposedAction("memory.propose", ProposeMemoryTool.args(MemoryType.BEHAVIOUR, h.content, h.confidence)),
                Requester.SYSTEM,
            ) is ExecutionResult.Done
        }
    }

    /** Re-registers reminders for today and tomorrow; call after any change to a plan or a task. */
    suspend fun syncReminders(): ExecutionResult? = lock.withLock { syncLocked() }

    private suspend fun syncLocked(): ExecutionResult? = if (!remindersReady()) null else
        orchestrator.execute(
            ProposedAction("reminders.sync", JsonObject(mapOf("from" to JsonPrimitive(LocalDate.now(clock).toString()), "days" to JsonPrimitive(2)))),
            Requester.SYSTEM,
        )

    private fun obj(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })
}
