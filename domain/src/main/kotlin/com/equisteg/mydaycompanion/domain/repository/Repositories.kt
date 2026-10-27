package com.equisteg.mydaycompanion.domain.repository

import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.FixedEvent
import com.equisteg.mydaycompanion.domain.model.Routine
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Every store that holds user data implements this so full deletion can be verified store by store. */
interface ErasableStore {
    val storeName: String
    suspend fun deleteAll()
    suspend fun count(): Int
}

interface TaskRepository : ErasableStore {
    fun observeAll(): Flow<List<Task>>
    suspend fun all(): List<Task>
    suspend fun get(id: String): Task?
    suspend fun upsert(tasks: List<Task>)
    suspend fun delete(id: String)
}

interface FixedEventRepository : ErasableStore {
    fun observeForDate(date: LocalDate): Flow<List<FixedEvent>>
    suspend fun forDate(date: LocalDate): List<FixedEvent>
    suspend fun all(): List<FixedEvent>
    suspend fun upsert(events: List<FixedEvent>)
}

interface PlanRepository : ErasableStore {
    fun observe(date: LocalDate): Flow<DayPlan?>
    suspend fun get(date: LocalDate): DayPlan?
    suspend fun all(): List<DayPlan>
    suspend fun save(plan: DayPlan)
}

interface RoutineRepository : ErasableStore {
    suspend fun all(): List<Routine>
    suspend fun upsert(routine: Routine)
}

interface PreferencesStore : ErasableStore {
    suspend fun get(): UserPreferences
    suspend fun set(preferences: UserPreferences)
}
