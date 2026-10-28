package com.equisteg.mydaycompanion.domain.security

import com.equisteg.mydaycompanion.domain.repository.ErasableStore
import com.equisteg.mydaycompanion.domain.tools.RiskLevel
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** What happens after too many wrong challenge answers in 24 hours (build doc §6.3, §8.3). */
enum class FailureResponse { LOCK_UNTIL_CREDENTIAL, WIPE }

/** How the owner approves actions on personal items (build doc §6.1). */
enum class PersonalApproval { DEVICE_CREDENTIAL, CHALLENGE }

data class SecuritySettings(
    val appLockEnabled: Boolean = true,
    val lockAfterMinutes: Int = 5,
    val wipeOnRepeatedFailure: Boolean = false,
    val personalApproval: PersonalApproval = PersonalApproval.DEVICE_CREDENTIAL,
) {
    init {
        require(lockAfterMinutes in LOCK_AFTER_CHOICES) { "lockAfterMinutes" }
    }

    val failureResponse: FailureResponse get() = if (wipeOnRepeatedFailure) FailureResponse.WIPE else FailureResponse.LOCK_UNTIL_CREDENTIAL

    companion object {
        /** 0 means "lock every time the app leaves the screen". */
        val LOCK_AFTER_CHOICES = listOf(0, 1, 5, 15, 30)
    }
}

/**
 * One challenge question with its answer stored only as a salted PBKDF2 hash. The clear answer is
 * never stored, exported, audited or shown to a model.
 */
data class ChallengeAnswer(
    val id: String,
    val question: String,
    val salt: String,
    val hash: String,
    val iterations: Int,
)

/** Security settings, challenge-answer hashes and failed-answer times. Lives in the encrypted database. */
interface SecurityStore : ErasableStore {
    suspend fun settings(): SecuritySettings
    suspend fun saveSettings(settings: SecuritySettings)
    suspend fun challenges(): List<ChallengeAnswer>
    suspend fun replaceChallenges(answers: List<ChallengeAnswer>)
    suspend fun failures(): List<Instant>
    suspend fun recordFailure(at: Instant)
    suspend fun clearFailures()
}

/** PBKDF2-HMAC-SHA256 hashing of challenge answers, using only the JDK. */
object ChallengeHasher {
    const val ITERATIONS = 310_000
    const val SALT_BYTES = 16
    const val HASH_BYTES = 32
    const val MIN_ANSWER_CHARS = 2
    const val MAX_QUESTION_CHARS = 120
    const val QUESTION_COUNT = 3

    /** Suggested questions; the owner may also write their own. */
    val SUGGESTED_QUESTIONS = listOf(
        "What was the name of your first school?",
        "In which town was your mother born?",
        "What was your childhood nickname?",
        "What is the name of your oldest cousin?",
        "What was the first dish you learned to cook?",
        "What street did you live on at age ten?",
    )

    private val punctuation = Regex("[\\p{P}\\p{S}]")
    private val spaces = Regex("\\s+")

    /** "  Saint Mary's  School!" and "saint marys school" are the same answer. */
    fun normalize(answer: String): String =
        answer.lowercase(Locale.ROOT).replace(punctuation, "").replace(spaces, " ").trim()

    fun isAcceptableAnswer(answer: String): Boolean = normalize(answer).length >= MIN_ANSWER_CHARS

    fun hash(id: String, question: String, answer: String, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): ChallengeAnswer {
        require(isAcceptableAnswer(answer)) { "answer too short" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val digest = pbkdf2(normalize(answer), salt, iterations)
        return ChallengeAnswer(id, question.trim(), b64(salt), b64(digest), iterations)
    }

    fun matches(stored: ChallengeAnswer, attempt: String): Boolean {
        val salt = unb64(stored.salt) ?: return false
        val expected = unb64(stored.hash) ?: return false
        val actual = pbkdf2(normalize(attempt), salt, stored.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    /** Structural checks on a stored or proposed hash; used by the tool that saves them. */
    fun isWellFormed(a: ChallengeAnswer): Boolean =
        a.id.isNotBlank() && a.id.length <= 40 &&
            a.question.isNotBlank() && a.question.length <= MAX_QUESTION_CHARS &&
            unb64(a.salt)?.size == SALT_BYTES && unb64(a.hash)?.size == HASH_BYTES &&
            a.iterations in ITERATIONS..(ITERATIONS * 10)

    private fun pbkdf2(normalized: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(normalized.toCharArray(), salt, iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(s: String) = try { Base64.getDecoder().decode(s) } catch (e: IllegalArgumentException) { null }
}

/** Where the challenge gate stands after counting recent wrong answers. */
sealed interface ChallengeGate {
    data object Open : ChallengeGate
    data class LockedUntil(val until: Instant) : ChallengeGate

    /** Too many wrong answers: only the device credential (fingerprint/PIN) reopens the agent. */
    data object RequireCredential : ChallengeGate

    /** Too many wrong answers and the owner opted in to wiping local data. */
    data object Wipe : ChallengeGate
}

/** 5 wrong answers lock for 15 minutes; 10 in 24 hours escalate to the owner's chosen response. */
object LockoutPolicy {
    const val SHORT_LOCK_AFTER = 5
    const val ESCALATE_AFTER = 10
    val SHORT_LOCK: Duration = Duration.ofMinutes(15)
    val WINDOW: Duration = Duration.ofHours(24)

    fun evaluate(failures: List<Instant>, now: Instant, response: FailureResponse): ChallengeGate {
        val recent = failures.filter { it.isAfter(now.minus(WINDOW)) && !it.isAfter(now) }.sorted()
        if (recent.size >= ESCALATE_AFTER) {
            return if (response == FailureResponse.WIPE) ChallengeGate.Wipe else ChallengeGate.RequireCredential
        }
        if (recent.size >= SHORT_LOCK_AFTER) {
            val until = recent.last().plus(SHORT_LOCK)
            if (now.isBefore(until)) return ChallengeGate.LockedUntil(until)
        }
        return ChallengeGate.Open
    }
}

/** One step the owner completes before an action runs. Steps are shown in this order. */
enum class ApprovalStep { TAP, DEVICE_CREDENTIAL, CHALLENGE }

/** Approval levels from build doc §6.1. The owner can add steps but never remove the L3/L4 defaults. */
object ApprovalRules {
    fun stepsFor(risk: RiskLevel, personal: Boolean, settings: SecuritySettings, hasChallenges: Boolean): List<ApprovalStep> {
        val steps = linkedSetOf(ApprovalStep.TAP)
        if (risk >= RiskLevel.L3) steps += ApprovalStep.DEVICE_CREDENTIAL
        if (risk == RiskLevel.L4 && hasChallenges) steps += ApprovalStep.CHALLENGE
        if (personal) {
            steps += if (settings.personalApproval == PersonalApproval.CHALLENGE && hasChallenges) ApprovalStep.CHALLENGE else ApprovalStep.DEVICE_CREDENTIAL
        }
        return steps.sortedBy { it.ordinal }
    }
}

sealed interface ChallengeResult {
    data object Correct : ChallengeResult
    data class Wrong(val gate: ChallengeGate) : ChallengeResult
    data class Blocked(val gate: ChallengeGate) : ChallengeResult
}

/**
 * Asks and checks challenge questions, counting wrong answers. Failure times are security
 * bookkeeping, not user data, so they are written here rather than through a tool.
 */
class ChallengeVerifier(
    private val store: SecurityStore,
    private val now: () -> Instant,
    private val random: SecureRandom = SecureRandom(),
) {
    suspend fun hasChallenges(): Boolean = store.challenges().isNotEmpty()

    suspend fun gate(): ChallengeGate = LockoutPolicy.evaluate(store.failures(), now(), store.settings().failureResponse)

    /** A random question, or null when none are set up. */
    suspend fun pickQuestion(): ChallengeAnswer? = store.challenges().takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }

    suspend fun answer(questionId: String, attempt: String): ChallengeResult {
        val before = gate()
        if (before != ChallengeGate.Open) return ChallengeResult.Blocked(before)
        val stored = store.challenges().firstOrNull { it.id == questionId } ?: return ChallengeResult.Blocked(ChallengeGate.Open)
        if (ChallengeHasher.matches(stored, attempt)) {
            store.clearFailures()
            return ChallengeResult.Correct
        }
        store.recordFailure(now())
        return ChallengeResult.Wrong(gate())
    }

    /** The owner proved themselves with the device credential, which resets the wrong-answer count. */
    suspend fun onDeviceCredentialPassed() = store.clearFailures()
}
