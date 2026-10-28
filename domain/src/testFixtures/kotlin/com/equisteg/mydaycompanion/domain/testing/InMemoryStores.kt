package com.equisteg.mydaycompanion.domain.testing

import com.equisteg.mydaycompanion.domain.ai.DeviceState
import com.equisteg.mydaycompanion.domain.ai.DeviceStateProvider
import com.equisteg.mydaycompanion.domain.audit.AuditEntry
import com.equisteg.mydaycompanion.domain.audit.AuditLog
import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemoryStore
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.repository.FixedEventRepository
import com.equisteg.mydaycompanion.domain.repository.PlanRepository
import com.equisteg.mydaycompanion.domain.repository.PreferencesStore
import com.equisteg.mydaycompanion.domain.repository.RoutineRepository
import com.equisteg.mydaycompanion.domain.repository.TaskRepository
import com.equisteg.mydaycompanion.domain.security.ChallengeAnswer
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import com.equisteg.mydaycompanion.domain.security.SecurityStore
import com.equisteg.mydaycompanion.domain.tools.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class InMemoryTaskRepository : TaskRepository {
    private val state = MutableStateFlow<Map<String, Task>>(emptyMap())
    override val storeName = "tasks"
    override fun observeAll(): Flow<List<Task>> = state.map { it.values.toList() }
    override suspend fun all() = state.value.values.toList()
    override suspend fun get(id: String) = state.value[id]
    override suspend fun upsert(tasks: List<Task>) { state.value = state.value + tasks.associateBy { it.id } }
    override suspend fun delete(id: String) { state.value = state.value - id }
    override suspend fun deleteAll() { state.value = emptyMap() }
    override suspend fun count() = state.value.size
}

class InMemoryFixedEventRepository : FixedEventRepository {
    private val state = MutableStateFlow<Map<String, FixedEvent>>(emptyMap())
    override val storeName = "fixed_events"
    override fun observeForDate(date: LocalDate) = state.map { m -> m.values.filter { it.date == date } }
    override suspend fun forDate(date: LocalDate) = state.value.values.filter { it.date == date }
    override suspend fun all() = state.value.values.toList()
    override suspend fun upsert(events: List<FixedEvent>) { state.value = state.value + events.associateBy { it.id } }
    override suspend fun deleteAll() { state.value = emptyMap() }
    override suspend fun count() = state.value.size
}

class InMemoryPlanRepository : PlanRepository {
    private val state = MutableStateFlow<Map<LocalDate, DayPlan>>(emptyMap())
    override val storeName = "plans"
    override fun observe(date: LocalDate) = state.map { it[date] }
    override suspend fun get(date: LocalDate) = state.value[date]
    override suspend fun all() = state.value.values.toList()
    override suspend fun save(plan: DayPlan) { state.value = state.value + (plan.date to plan) }
    override suspend fun deleteAll() { state.value = emptyMap() }
    override suspend fun count() = state.value.size
}

class InMemoryRoutineRepository : RoutineRepository {
    private val items = linkedMapOf<String, Routine>()
    override val storeName = "routines"
    override suspend fun all() = items.values.toList()
    override suspend fun upsert(routine: Routine) { items[routine.id] = routine }
    override suspend fun deleteAll() = items.clear()
    override suspend fun count() = items.size
}

class InMemoryMemoryStore : MemoryStore {
    private val items = linkedMapOf<String, MemoryItem>()
    override val storeName = "memories"
    override suspend fun all() = items.values.toList()
    override suspend fun get(id: String) = items[id]
    override suspend fun upsert(item: MemoryItem) { items[item.id] = item }
    override suspend fun delete(id: String) { items.remove(id) }
    override suspend fun deleteAll() = items.clear()
    override suspend fun count() = items.size
}

class InMemoryAuditLog : AuditLog {
    val entries = mutableListOf<AuditEntry>()
    override val storeName = "audit_log"
    override suspend fun record(entry: AuditEntry) { entries += entry }
    override suspend fun recent(limit: Int) = entries.takeLast(limit).reversed()
    override suspend fun all() = entries.toList()
    override suspend fun deleteAll() = entries.clear()
    override suspend fun count() = entries.size
}

class InMemoryPreferencesStore(private var prefs: UserPreferences? = null) : PreferencesStore {
    override val storeName = "preferences"
    override suspend fun get() = prefs ?: UserPreferences()
    override suspend fun set(preferences: UserPreferences) { prefs = preferences }
    override suspend fun deleteAll() { prefs = null }
    override suspend fun count() = if (prefs == null) 0 else 1
}

class FixedDeviceState(var state: DeviceState = DeviceState(batteryPercent = 80, isCharging = false)) : DeviceStateProvider {
    override fun current() = state
}

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var n = 0
    override fun newId() = "$prefix-${++n}"
}

class InMemorySecurityStore(private var current: SecuritySettings? = null) : SecurityStore {
    private val answers = mutableListOf<ChallengeAnswer>()
    private val failed = mutableListOf<Instant>()
    override val storeName = "security"
    override suspend fun settings() = current ?: SecuritySettings()
    override suspend fun saveSettings(settings: SecuritySettings) { current = settings }
    override suspend fun challenges() = answers.toList()
    override suspend fun replaceChallenges(answers: List<ChallengeAnswer>) { this.answers.clear(); this.answers += answers }
    override suspend fun failures() = failed.toList()
    override suspend fun recordFailure(at: Instant) { failed += at }
    override suspend fun clearFailures() = failed.clear()
    override suspend fun deleteAll() { current = null; answers.clear(); failed.clear() }
    override suspend fun count() = (if (current == null) 0 else 1) + answers.size + failed.size
}
