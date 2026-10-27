package com.equisteg.mydaycompanion.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.equisteg.mydaycompanion.AppServices
import com.equisteg.mydaycompanion.domain.agent.AgentProposal
import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** A privileged action waiting for the user to tap Confirm in a dialog. */
data class PendingConfirmation(val action: ProposedAction, val fingerprint: String, val title: String, val body: String)

data class UiState(
    val input: String = "",
    val busy: Boolean = false,
    val proposal: AgentProposal.Ready? = null,
    val message: String? = null,
    val selectedDate: LocalDate,
    val pending: PendingConfirmation? = null,
    val history: List<AuditEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val services: AppServices) : ViewModel() {
    private val _state = MutableStateFlow(UiState(selectedDate = LocalDate.now(services.clock)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val selectedDate = MutableStateFlow(_state.value.selectedDate)

    val plan: StateFlow<DayPlan?> = selectedDate
        .flatMapLatest { services.plans.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tasks: StateFlow<List<Task>> = services.tasks.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onInputChange(text: String) = _state.update { it.copy(input = text, message = null) }

    fun onSubmit() = launchBusy {
        when (val p = services.orchestrator.propose(_state.value.input)) {
            is AgentProposal.Ready -> _state.update { it.copy(proposal = p, message = p.warnings.firstOrNull()) }
            is AgentProposal.NotUnderstood -> _state.update { it.copy(message = p.reason) }
            AgentProposal.Empty -> Unit
        }
    }

    fun onDiscardProposal() = _state.update { it.copy(proposal = null) }

    /** The user reviewed the proposed tasks and plan and tapped "Add to my day". */
    fun onAcceptProposal() = launchBusy {
        val proposal = _state.value.proposal ?: return@launchBusy
        for (action in proposal.actions) {
            val result = runWithUserApproval(action, Requester.AGENT)
            if (result !is ExecutionResult.Done) {
                _state.update { it.copy(message = describe(result)) }
                return@launchBusy
            }
        }
        selectDate(proposal.extraction.targetDate)
        _state.update { it.copy(proposal = null, input = "", message = "Added to your day.") }
    }

    fun onCompleteTask(taskId: String) = launchBusy {
        val r = services.orchestrator.execute(ProposedAction("tasks.complete", args("taskId" to taskId)), Requester.USER)
        if (r is ExecutionResult.Done) replan() else _state.update { it.copy(message = describe(r)) }
    }

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

    fun onDeleteEverything() = launchBusy {
        request(
            ProposedAction("data.deleteAll", args("scope" to "everything")),
            "Delete everything?",
            "All tasks, plans, memories, settings and history on this device will be permanently deleted. This cannot be undone.",
        )
    }

    fun onConfirmPending() = launchBusy {
        val pending = _state.value.pending ?: return@launchBusy
        _state.update { it.copy(pending = null) }
        val r = services.orchestrator.execute(pending.action, Requester.USER, UserConfirmation(pending.fingerprint))
        _state.update { it.copy(message = describe(r)) }
        refreshHistory()
    }

    fun onCancelPending() = _state.update { it.copy(pending = null) }

    fun refreshHistory() {
        viewModelScope.launch {
            val entries = services.audit.recent(100)
            _state.update { it.copy(history = entries) }
        }
    }

    private suspend fun request(action: ProposedAction, title: String, body: String) {
        when (val r = services.orchestrator.execute(action, Requester.USER)) {
            is ExecutionResult.NeedsConfirmation -> _state.update { it.copy(pending = PendingConfirmation(action, r.actionFingerprint, title, body)) }
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

    private suspend fun replan() {
        val date = _state.value.selectedDate
        val pairs = buildList {
            add("date" to date.toString())
            if (date == LocalDate.now(services.clock)) add("notBefore" to LocalTime.now(services.clock).truncatedTo(ChronoUnit.MINUTES).toString())
        }
        val r = services.orchestrator.execute(ProposedAction("plan.generate", args(*pairs.toTypedArray())), Requester.USER)
        if (r !is ExecutionResult.Done) _state.update { it.copy(message = describe(r)) }
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
        fun factory(services: AppServices) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(services) as T
        }
    }
}
