package com.equisteg.mydaycompanion.domain.privacy

import com.equisteg.mydaycompanion.domain.repository.ErasableStore

data class DeletionReport(val remaining: Map<String, Int>) {
    val verified: Boolean get() = remaining.values.all { it == 0 }
}

/**
 * Deletes every user-data store, runs platform wipes (encryption key, scheduled reminders,
 * caches), then re-counts each store to prove nothing is left.
 */
class DataDeletionService(
    private val stores: List<ErasableStore>,
    private val platformWipe: suspend () -> Unit = {},
) {
    suspend fun deleteEverything(): DeletionReport {
        stores.forEach { it.deleteAll() }
        platformWipe()
        return DeletionReport(stores.associate { it.storeName to it.count() })
    }
}
