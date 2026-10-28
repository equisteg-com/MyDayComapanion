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
        SecuritySettingsEntity::class,
        ChallengeAnswerEntity::class,
        AuthFailureEntity::class,
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
    abstract fun security(): SecurityDao

    companion object {
        const val VERSION = 4
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

    /** v3: owner verification (build doc §6): security settings, hashed challenge answers, failed-answer times. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `security_settings` (`id` INTEGER NOT NULL, `appLockEnabled` INTEGER NOT NULL, " +
                    "`lockAfterMinutes` INTEGER NOT NULL, `wipeOnRepeatedFailure` INTEGER NOT NULL, `personalApproval` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `challenge_answers` (`id` TEXT NOT NULL, `question` TEXT NOT NULL, `salt` TEXT NOT NULL, " +
                    "`hash` TEXT NOT NULL, `iterations` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS `auth_failures` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_auth_failures_timestamp` ON `auth_failures` (`timestamp`)")
        }
    }

    /**
     * v4: task notes and the personal mark; date order, reminder lead time and the first-run flag.
     * Installs that already hold data skip first-run setup.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `notes` TEXT")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `personal` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `preferences` ADD COLUMN `dateOrder` TEXT NOT NULL DEFAULT 'DAY_MONTH'")
            db.execSQL("ALTER TABLE `preferences` ADD COLUMN `reminderLeadMinutes` INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE `preferences` ADD COLUMN `onboardingDone` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `preferences` SET `onboardingDone` = 1")
            db.execSQL(
                "INSERT OR IGNORE INTO `preferences` (`id`, `dayStart`, `dayEnd`, `bufferMinutes`, `speechOutputEnabled`, `dateOrder`, " +
                    "`reminderLeadMinutes`, `onboardingDone`) SELECT 1, '09:00', '21:00', 10, 0, 'DAY_MONTH', 10, 1 WHERE EXISTS (SELECT 1 FROM `tasks`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
