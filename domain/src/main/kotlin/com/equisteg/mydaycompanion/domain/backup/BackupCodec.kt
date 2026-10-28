package com.equisteg.mydaycompanion.domain.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed interface Decrypted {
    data class Ok(val plaintext: ByteArray) : Decrypted
    /** The passphrase is wrong, or the file was changed after it was written (the GCM tag doesn't match). */
    data object WrongPassphraseOrTampered : Decrypted
    data class Unreadable(val reason: String) : Decrypted
}

/**
 * The backup file format (build doc §7.2), JDK only:
 *
 * `MDCB` · version (1 byte) · KDF id (1 byte, 1 = PBKDF2-HMAC-SHA256) · iterations (4 bytes) ·
 * salt length (1) · salt · nonce length (1) · nonce · AES-256-GCM ciphertext with its 128-bit tag.
 *
 * The header is authenticated as GCM additional data, so changing any byte of the file, header
 * included, makes it fail to open. The passphrase never leaves this class except as a derived key.
 */
class BackupCodec(
    private val iterations: Int = ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) { "passphrase too short" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = header(iterations, salt, nonce)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): Decrypted {
        if (file.size > MAX_FILE_BYTES) return Decrypted.Unreadable("TOO_LARGE")
        val buf = ByteBuffer.wrap(file)
        try {
            val magic = ByteArray(4).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC)) return Decrypted.Unreadable("NOT_A_BACKUP")
            if (buf.get().toInt() != VERSION) return Decrypted.Unreadable("UNSUPPORTED_VERSION")
            if (buf.get().toInt() != KDF_PBKDF2_SHA256) return Decrypted.Unreadable("UNSUPPORTED_KDF")
            val iters = buf.getInt()
            // A file can't make the phone spin for minutes, nor ask for a weaker key than we write.
            if (iters !in ITERATIONS..MAX_ITERATIONS) return Decrypted.Unreadable("BAD_KDF_PARAMETERS")
            val salt = ByteArray(buf.get().toInt() and 0xFF).also { if (it.size != SALT_BYTES) return Decrypted.Unreadable("BAD_HEADER"); buf.get(it) }
            val nonce = ByteArray(buf.get().toInt() and 0xFF).also { if (it.size != NONCE_BYTES) return Decrypted.Unreadable("BAD_HEADER"); buf.get(it) }
            val headerLength = buf.position()
            val body = file.copyOfRange(headerLength, file.size)
            if (body.size < TAG_BITS / 8) return Decrypted.Unreadable("TRUNCATED")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, iters), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(file, 0, headerLength)
            return Decrypted.Ok(cipher.doFinal(body))
        } catch (e: AEADBadTagException) {
            return Decrypted.WrongPassphraseOrTampered
        } catch (e: java.nio.BufferUnderflowException) {
            return Decrypted.Unreadable("TRUNCATED")
        }
    }

    private fun header(iters: Int, salt: ByteArray, nonce: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + 1 + 1 + 4 + 1 + salt.size + 1 + nonce.size)
            .put(MAGIC).put(VERSION.toByte()).put(KDF_PBKDF2_SHA256.toByte()).putInt(iters)
            .put(salt.size.toByte()).put(salt).put(nonce.size.toByte()).put(nonce)
            .array()

    private fun key(passphrase: CharArray, salt: ByteArray, iters: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iters, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        val MAGIC = byteArrayOf('M'.code.toByte(), 'D'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte())
        const val VERSION = 1
        const val KDF_PBKDF2_SHA256 = 1
        const val ITERATIONS = 600_000
        const val MAX_ITERATIONS = 5_000_000
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val MIN_PASSPHRASE_CHARS = 10
        /** Backup files are untrusted input; anything bigger is refused before decryption (build doc §11). */
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        const val FILE_EXTENSION = "mdcbackup"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** A rough strength label for the passphrase field: length counts most. */
object PassphraseStrength {
    enum class Level(val label: String) { TOO_SHORT("Too short"), WEAK("Weak"), OK("OK"), STRONG("Strong") }

    fun of(p: CharSequence): Level {
        if (p.length < BackupCodec.MIN_PASSPHRASE_CHARS) return Level.TOO_SHORT
        val classes = listOf(p.any(Char::isLowerCase), p.any(Char::isUpperCase), p.any(Char::isDigit), p.any { !it.isLetterOrDigit() }).count { it }
        val distinct = p.toSet().size
        return when {
            distinct < 5 -> Level.WEAK
            p.length >= 16 && classes >= 2 || p.length >= 20 -> Level.STRONG
            classes >= 2 -> Level.OK
            else -> Level.WEAK
        }
    }
}
