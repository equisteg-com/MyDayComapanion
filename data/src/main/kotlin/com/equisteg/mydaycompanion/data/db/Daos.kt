package com.equisteg.mydaycompanion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt") fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks ORDER BY createdAt") suspend fun all(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: String): TaskEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(items: List<TaskEntity>)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM tasks") suspend fun deleteAll()
    @Query("SELECT COUNT(*) FROM tasks") suspend fun count(): Int
}

@Dao
interface FixedEventDao {
    @Query("SELECT * FROM fixed_events WHERE date = :date ORDER BY start") fun observeForDate(date: String): Flow<List<FixedEventEntity>>
    @Query("SELECT * FROM fixed_events WHERE date = :date ORDER BY start") suspend fun forDate(date: String): List<FixedEventEntity>
    @Query("SELECT * FROM fixed_events ORDER BY date, start") suspend fun all(): List<FixedEventEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(items: List<FixedEventEntity>)
    @Query("DELETE FROM fixed_events") suspend fun deleteAll()
    @Query("SELECT COUNT(*) FROM fixed_events") suspend fun count(): Int
}

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM plans WHERE date = :date") abstract fun observePlan(date: String): Flow<PlanEntity?>
    @Query("SELECT * FROM plans WHERE date = :date") abstract suspend fun plan(date: String): PlanEntity?
    @Query("SELECT * FROM plans ORDER BY date") abstract suspend fun allPlans(): List<PlanEntity>
    @Query("SELECT * FROM plan_blocks WHERE date = :date ORDER BY position") abstract suspend fun blocks(date: String): List<PlanBlockEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertPlan(plan: PlanEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertBlocks(blocks: List<PlanBlockEntity>)
    @Query("DELETE FROM plan_blocks WHERE date = :date") abstract suspend fun deleteBlocks(date: String)
    @Query("DELETE FROM plans") abstract suspend fun deleteAllPlans()
    @Query("DELETE FROM plan_blocks") abstract suspend fun deleteAllBlocks()
    @Query("SELECT (SELECT COUNT(*) FROM plans) + (SELECT COUNT(*) FROM plan_blocks)") abstract suspend fun count(): Int

    @Transaction
    open suspend fun replace(plan: PlanEntity, blocks: List<PlanBlockEntity>) {
        deleteBlocks(plan.date)
        insertPlan(plan)
        insertBlocks(blocks)
    }

    @Transaction
    open suspend fun deleteAll() {
        deleteAllBlocks()
        deleteAllPlans()
    }
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt") suspend fun all(): List<RoutineEntity>
    @Query("SELECT * FROM routines ORDER BY createdAt") fun observeAll(): Flow<List<RoutineEntity>>
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun get(id: String): RoutineEntity?
    @Query("DELETE FROM routines WHERE id = :id") suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: RoutineEntity)
    @Query("DELETE FROM routines") suspend fun deleteAll()
    @Query("SELECT COUNT(*) FROM routines") suspend fun count(): Int
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt") suspend fun all(): List<MemoryEntity>
    @Query("SELECT * FROM memories WHERE id = :id") suspend fun get(id: String): MemoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: MemoryEntity)
    @Query("DELETE FROM memories WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM memories") suspend fun deleteAll()
    @Query("SELECT COUNT(*) FROM memories") suspend fun count(): Int
}

@Dao
interface AuditDao {
    @Insert suspend fun insert(entry: AuditEntity)
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit") suspend fun recent(limit: Int): List<AuditEntity>
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<AuditEntity>>
    @Query("SELECT * FROM audit_log ORDER BY timestamp") suspend fun all(): List<AuditEntity>
    // Timestamps are ISO-8601 instants compared as text. An entry with a fraction of a second in the
    // same second as the cut-off sorts before it and goes too; at most one second early, which is fine for retention.
    @Query("SELECT COUNT(*) FROM audit_log WHERE timestamp < :before") suspend fun countBefore(before: String): Int
    @Query("DELETE FROM audit_log WHERE timestamp < :before") suspend fun deleteBefore(before: String): Int
    @Query(
        "SELECT * FROM audit_log WHERE (:actor IS NULL OR actor = :actor) " +
            "AND (:attention = 0 OR decision = 'DENIED' OR outcome IN ('FAILURE', 'VERIFICATION_FAILED')) " +
            "AND (:highRisk = 0 OR riskLevel IN ('L3', 'L4')) ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun recentMatching(actor: String?, attention: Boolean, highRisk: Boolean, limit: Int): List<AuditEntity>
    @Query("SELECT * FROM audit_log WHERE action = :action AND outcome = 'SUCCESS' ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestSuccess(action: String): AuditEntity?
    @Query("DELETE FROM audit_log") suspend fun deleteAll()
    @Query("SELECT COUNT(*) FROM audit_log") suspend fun count(): Int
}

@Dao
interface PreferencesDao {
    @Query("SELECT * FROM preferences WHERE id = 1") suspend fun get(): PreferencesEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun set(prefs: PreferencesEntity)
    @Query("DELETE FROM preferences") suspend fun deleteAll()
    @Query("SELECT COUNT(*) FROM preferences") suspend fun count(): Int
}

@Dao
abstract class SecurityDao {
    @Query("SELECT * FROM security_settings WHERE id = 1") abstract suspend fun settings(): SecuritySettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveSettings(settings: SecuritySettingsEntity)
    @Query("SELECT * FROM challenge_answers ORDER BY id") abstract suspend fun challenges(): List<ChallengeAnswerEntity>
    @Insert abstract suspend fun insertChallenges(answers: List<ChallengeAnswerEntity>)
    @Query("DELETE FROM challenge_answers") abstract suspend fun deleteChallenges()
    @Query("SELECT timestamp FROM auth_failures ORDER BY timestamp") abstract suspend fun failures(): List<String>
    @Insert abstract suspend fun insertFailure(failure: AuthFailureEntity)
    @Query("DELETE FROM auth_failures") abstract suspend fun clearFailures()
    @Query("DELETE FROM security_settings") abstract suspend fun deleteSettings()
    @Query("SELECT (SELECT COUNT(*) FROM security_settings) + (SELECT COUNT(*) FROM challenge_answers) + (SELECT COUNT(*) FROM auth_failures)")
    abstract suspend fun count(): Int

    @Transaction
    open suspend fun replaceChallenges(answers: List<ChallengeAnswerEntity>) {
        deleteChallenges()
        insertChallenges(answers)
    }

    @Transaction
    open suspend fun deleteAll() {
        deleteSettings()
        deleteChallenges()
        clearFailures()
    }
}
