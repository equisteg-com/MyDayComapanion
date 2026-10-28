package com.equisteg.mydaycompanion.data.privacy

import android.content.Context
import com.equisteg.mydaycompanion.data.db.MdcDatabase
import com.equisteg.mydaycompanion.data.encryption.DatabaseKeyProvider
import java.io.File

/**
 * The last step of "Delete everything" and of a wipe after repeated wrong answers (build doc §8.4):
 * after every store is emptied and verified, close the database, delete its files (including the
 * -wal, -shm and -journal side files) and destroy the key that encrypted it. The app must restart
 * afterwards; it then starts fresh with a new key.
 */
class LocalStorageWiper(
    private val context: Context,
    private val keys: DatabaseKeyProvider,
    private val openDatabase: () -> MdcDatabase?,
) {
    /** Returns true when no database file and no wrapped key remain. */
    fun wipe(): Boolean {
        openDatabase()?.close()
        context.deleteDatabase(MdcDatabase.NAME)
        val db = context.getDatabasePath(MdcDatabase.NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
        keys.destroy()
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("db.key") }?.forEach { it.delete() }
        return listOf("", "-wal", "-shm", "-journal").none { File(db.path + it).exists() } &&
            context.noBackupFilesDir.listFiles()?.none { it.name.startsWith("db.key") } != false
    }
}
