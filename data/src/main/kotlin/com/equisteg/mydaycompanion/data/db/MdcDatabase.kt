package com.equisteg.mydaycompanion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        const val VERSION = 2
        const val NAME = "mydaycompanion.db"
    }
}

/**
 * Migration policy: every schema change bumps [MdcDatabase.VERSION], commits the exported schema
 * in data/schemas, and adds an explicit migration here. Destructive fallback is never enabled,
 * because silently wiping a user's local-only data would be unrecoverable.
 */
object Migrations {
    /** v2: tasks get a nullable preferred start time for soft times ("in the morning", "after 6"). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `preferredStart` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
