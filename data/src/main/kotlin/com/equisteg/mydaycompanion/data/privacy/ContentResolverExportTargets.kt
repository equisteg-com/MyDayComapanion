package com.equisteg.mydaycompanion.data.privacy

import android.content.ContentResolver
import android.net.Uri
import com.equisteg.mydaycompanion.domain.tools.ExportTargets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Export destinations chosen by the user through the system file picker (Storage Access
 * Framework), so the app needs no storage permission. Tokens are single-use.
 */
class ContentResolverExportTargets(private val resolver: ContentResolver) : ExportTargets {
    private val targets = ConcurrentHashMap<String, Uri>()

    fun register(uri: Uri): String = UUID.randomUUID().toString().also { targets[it] = uri }

    override fun exists(token: String) = targets.containsKey(token)

    override suspend fun write(token: String, bytes: ByteArray): Long? = withContext(Dispatchers.IO) {
        val uri = targets.remove(token) ?: return@withContext null
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: return@withContext null
        bytes.size.toLong()
    }
}
