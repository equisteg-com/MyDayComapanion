package com.equisteg.mydaycompanion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TaskEntity::class,
        FixedEventEntity::class,
        PlanEntity::class,
        PlanBlockEntity::class,
        RoutineEntity::class,
        MemoryEntity::class,
        AuditEntity::class,
        PreferencesEntity::class,
    ],
    version = MdcDatabase.VERSION,
    exportSchema = true,
)
abstract class MdcDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun fixedEvents(): FixedEventDao
    abstract fun plans(): PlanDao
    abstract fun routines(): RoutineDao
    abstract fun memories(): MemoryDao
    abstract fun audit(): AuditDao
    abstract fun preferences(): PreferencesDao

    companion object {
        const val VERSION = 1
        const val NAME = "mydaycompanion.db"
    }
}

/**
 * Migration policy: every schema change bumps [MdcDatabase.VERSION], commits the exported schema
 * in data/schemas, and adds an explicit migration here. Destructive fallback is never enabled,
 * because silently wiping a user's local-only data would be unrecoverable.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
