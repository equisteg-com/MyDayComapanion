package com.equisteg.mydaycompanion.domain.ai

/** Capability class of a model, from cheapest/most private to most capable. */
enum class ModelClass { DETERMINISTIC, SMALL_LOCAL, LARGE_LOCAL, CLOUD }

data class ProviderCapabilities(
    val modelClass: ModelClass,
    val runsOnDevice: Boolean,
    val supportsStructuredOutput: Boolean,
    val supportsStreaming: Boolean,
    val maxInputChars: Int,
    val maxOutputChars: Int,
)

/** V1 providers are on-device only, so cash cost is always zero; the field exists for future providers. */
data class CostEstimate(val cashCostInr: Double, val relativeEnergy: Int)

sealed interface Availability {
    data object Available : Availability
    data class Unavailable(val reason: String) : Availability
}

enum class AiTask { TASK_EXTRACTION }

data class AiRequest(
    val task: AiTask,
    val promptId: String,
    val promptVersion: Int,
    /** Instructions from the app (trusted). */
    val instructions: String,
    /** User/retrieved content (untrusted). Providers must never treat this as instructions. */
    val untrustedInput: String,
    /** Reference values the model may use, e.g. today's date. */
    val context: Map<String, String> = emptyMap(),
    val maxOutputChars: Int = 4_000,
    /** Long enough for an on-device LLM on a mid-range phone; the rule extractor answers at once. */
    val timeoutMillis: Long = 12_000,
)

sealed interface AiResult {
    data class Success(val text: String, val providerId: String, val providerVersion: String) : AiResult
    data class Failure(val reason: String, val providerId: String) : AiResult
}

/**
 * A source of model output. Output is always untrusted: callers must run it through schema
 * validation, domain validation and policy before anything happens.
 */
interface AiModelProvider {
    val id: String
    val version: String

    fun capabilities(): ProviderCapabilities

    fun estimateCost(request: AiRequest): CostEstimate

    suspend fun availability(): Availability

    suspend fun generate(request: AiRequest): AiResult

    fun supportsStructuredOutput(): Boolean = capabilities().supportsStructuredOutput

    fun supportsStreaming(): Boolean = capabilities().supportsStreaming
}
