package com.equisteg.mydaycompanion.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.equisteg.mydaycompanion.data.db.MdcDatabase
import com.equisteg.mydaycompanion.data.db.Migrations
import com.equisteg.mydaycompanion.data.encryption.DatabaseKeyProvider
import com.equisteg.mydaycompanion.data.privacy.LocalStorageWiper
import com.equisteg.mydaycompanion.data.repository.RoomPreferencesStore
import com.equisteg.mydaycompanion.data.repository.RoomRoutineRepository
import com.equisteg.mydaycompanion.data.repository.RoomSecurityStore
import com.equisteg.mydaycompanion.data.repository.RoomTaskRepository
import com.equisteg.mydaycompanion.domain.model.RoutineFrequency
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.security.SecuritySettings
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Migrations run against the committed schema history; the wipe removes files and keys, not just rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationAndWipeTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MdcDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a version 1 database migrates to the current version and keeps its tasks`() = runTest {
        helper.createDatabase(MIGRATED, 1).apply {
            execSQL(
                "INSERT INTO tasks (id, title, priority, durationMinutes, date, deadline, status, parentId, createdAt, updatedAt) " +
                    "VALUES ('t1', 'Call Raj', 'HIGH', 15, '2026-10-07', NULL, 'OPEN', NULL, '2026-10-06T07:00:00Z', '2026-10-06T07:00:00Z')",
            )
            close()
        }
        helper.runMigrationsAndValidate(MIGRATED, MdcDatabase.VERSION, true, *Migrations.ALL).close()

        val db = Room.databaseBuilder(context, MdcDatabase::class.java, MIGRATED).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            val task = RoomTaskRepository(db).get("t1")!!
            assertEquals("Call Raj", task.title)
            assertEquals(null, task.notes)
            assertFalse(task.personal)
            assertEquals(SecuritySettings(), RoomSecurityStore(db).settings(), "defaults until the owner changes them")
            assertTrue(RoomPreferencesStore(db).get().onboardingDone, "an install that already has tasks skips first-run setup")
        } finally {
            db.close()
        }
    }

    @Test
    fun `an empty version 3 database still shows first-run setup after migrating`() = runTest {
        helper.createDatabase(EMPTY, 3).close()
        helper.runMigrationsAndValidate(EMPTY, MdcDatabase.VERSION, true, *Migrations.ALL).close()
        val db = Room.databaseBuilder(context, MdcDatabase::class.java, EMPTY).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            assertFalse(RoomPreferencesStore(db).get().onboardingDone)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a version 4 routine keeps its fields and gets reminders on after migrating`() = runTest {
        helper.createDatabase(ROUTINES, 4).apply {
            execSQL(
                "INSERT INTO routines (id, title, frequency, preferredStart, durationMinutes, enabled, createdAt) " +
                    "VALUES ('r1', 'Gym', 'WEEKDAYS', '07:00', 45, 1, '2026-10-06T07:00:00Z')",
            )
            close()
        }
        helper.runMigrationsAndValidate(ROUTINES, MdcDatabase.VERSION, true, *Migrations.ALL).close()
        val db = Room.databaseBuilder(context, MdcDatabase::class.java, ROUTINES).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            val r = RoomRoutineRepository(db).get("r1")!!
            assertEquals("Gym", r.title)
            assertEquals(RoutineFrequency.WEEKDAYS, r.frequency)
            assertTrue(r.remind)
            assertEquals(null, r.dayOfWeek)
        } finally {
            db.close()
        }
    }

    @Test
    fun `wipe deletes the database files and destroys the key`() = runTest {
        val keys = FakeKeys()
        val db = Room.databaseBuilder(context, MdcDatabase::class.java, MdcDatabase.NAME).allowMainThreadQueries().build()
        RoomTaskRepository(db).upsert(listOf(Task("t", "x", createdAt = Instant.parse("2026-10-06T07:00:00Z"))))
        File(context.noBackupFilesDir, "db.key.v1.enc").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(context.getDatabasePath(MdcDatabase.NAME).exists())

        assertTrue(LocalStorageWiper(context, keys) { db }.wipe())
        assertFalse(context.getDatabasePath(MdcDatabase.NAME).exists())
        assertFalse(File(context.noBackupFilesDir, "db.key.v1.enc").exists())
        assertTrue(keys.destroyed)
    }

    private class FakeKeys : DatabaseKeyProvider {
        var destroyed = false
        override fun getOrCreatePassphrase() = ByteArray(32)
        override fun destroy() { destroyed = true }
    }

    private companion object {
        const val MIGRATED = "migration-test.db"
        const val EMPTY = "migration-empty-test.db"
        const val ROUTINES = "migration-routines-test.db"
    }
}
