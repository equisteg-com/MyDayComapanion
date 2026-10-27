package com.equisteg.mydaycompanion.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.equisteg.mydaycompanion.data.encryption.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object DatabaseFactory {
    /** Opens the SQLCipher-encrypted database. Never falls back to an unencrypted database. */
    fun openEncrypted(context: Context, keys: DatabaseKeyProvider): MdcDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = keys.getOrCreatePassphrase()
        return Room.databaseBuilder(context, MdcDatabase::class.java, MdcDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(*Migrations.ALL)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Overwrite deleted content so "delete everything" leaves nothing recoverable in free pages.
                        db.query("PRAGMA secure_delete = ON").close()
                    }
                },
            )
            .build()
    }
}
