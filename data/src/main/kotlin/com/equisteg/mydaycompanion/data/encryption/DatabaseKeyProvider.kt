package com.equisteg.mydaycompanion.data.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Supplies the passphrase that encrypts the local database. */
interface DatabaseKeyProvider {
    fun getOrCreatePassphrase(): ByteArray
}

/**
 * The database passphrase is 32 random bytes. It is stored only in wrapped form (AES-256-GCM)
 * in no-backup storage; the wrapping key lives in the Android Keystore and never leaves it.
 * Losing the Keystore key (e.g. device reset) makes the database unreadable, which is the
 * intended failure mode for local-only data.
 */
class KeystoreDatabaseKeyProvider(private val context: Context) : DatabaseKeyProvider {

    override fun getOrCreatePassphrase(): ByteArray {
        val file = wrappedKeyFile()
        if (file.exists()) return unwrap(file.readBytes())
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(wrap(passphrase))
        check(tmp.renameTo(file)) { "Could not store wrapped database key" }
        return passphrase
    }

    private fun wrappedKeyFile() = File(context.noBackupFilesDir, WRAPPED_KEY_FILE)

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        require(ivLen == GCM_IV_BYTES && blob.size > 1 + ivLen) { "Wrapped database key is malformed" }
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Decrypt only: the IV is the one the Keystore generated randomly for this blob in wrap(); it is never reused to encrypt.
        // nosemgrep: kotlin.lang.security.gcm-detection.gcm-detection
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(blob.copyOfRange(1 + ivLen, blob.size))
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mydaycompanion.db.wrap.v1"
        const val WRAPPED_KEY_FILE = "db.key.v1.enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
        const val PASSPHRASE_BYTES = 32
    }
}
