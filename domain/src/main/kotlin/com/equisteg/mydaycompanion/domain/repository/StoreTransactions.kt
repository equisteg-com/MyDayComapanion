package com.equisteg.mydaycompanion.domain.repository

/**
 * Runs a group of store writes as one unit: either all of them are kept, or none (a backup restore
 * must never leave the phone half-replaced). The data layer runs [atomically] in one database
 * transaction; stores that live outside the database must not be written inside it.
 */
interface StoreTransactions {
    suspend fun <T> atomically(block: suspend () -> T): T
}

/** For in-memory test stores, which have no transaction to join. */
object NoStoreTransactions : StoreTransactions {
    override suspend fun <T> atomically(block: suspend () -> T): T = block()
}
