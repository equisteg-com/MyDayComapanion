package com.equisteg.mydaycompanion.data.repository

import androidx.room.withTransaction
import com.equisteg.mydaycompanion.data.db.MdcDatabase
import com.equisteg.mydaycompanion.domain.repository.StoreTransactions

/** Every Room store write inside [atomically] commits together or is rolled back together. */
class RoomStoreTransactions(private val db: MdcDatabase) : StoreTransactions {
    override suspend fun <T> atomically(block: suspend () -> T): T = db.withTransaction { block() }
}
