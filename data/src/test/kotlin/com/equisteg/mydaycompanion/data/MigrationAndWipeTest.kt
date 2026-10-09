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
import com.equisteg.mydaycompanion.data.repository.RoomSecurityStore
import com.equisteg.mydaycompanion.data.repository.RoomTaskRepository
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
            assertEquals("Call Raj", RoomTaskRepository(db).get("t1")?.title)
            assertEquals(SecuritySettings(), RoomSecurityStore(db).settings(), "defaults until the owner changes them")
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
    }
}
