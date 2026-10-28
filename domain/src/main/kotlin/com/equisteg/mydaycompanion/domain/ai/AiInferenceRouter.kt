package com.equisteg.mydaycompanion.domain.ai

enum class PrivacyMode {
    /** V1 default and only mode: inference never leaves the device. */
    ON_DEVICE_ONLY,
}

data class RouteDecision(
    val provider: AiModelProvider,
    val energyMode: EnergyMode,
    /** Providers considered before this one and why they were skipped (for diagnostics, no user data). */
    val skipped: List<String>,
)

/**
 * Picks which provider handles a request based on device resources, privacy mode and
 * availability. The deterministic provider is the guaranteed fallback and must always be present.
 */
class AiInferenceRouter(
    private val providers: List<AiModelProvider>,
    private val fallback: AiModelProvider,
    private val privacyMode: PrivacyMode = PrivacyMode.ON_DEVICE_ONLY,
) {
    init {
        require(fallback.capabilities().modelClass == ModelClass.DETERMINISTIC) { "Fallback must be deterministic" }
        require(fallback.capabilities().runsOnDevice) { "Fallback must run on device" }
    }

    suspend fun route(request: AiRequest, device: DeviceState): RouteDecision {
        val mode = EnergyPolicy.modeFor(device)
        val allowed = EnergyPolicy.allowedModelClasses(mode)
        val skipped = mutableListOf<String>()
        val ordered = providers.sortedByDescending { it.capabilities().modelClass.ordinal }
        for (p in ordered) {
            val caps = p.capabilities()
            val reason = when {
                privacyMode == PrivacyMode.ON_DEVICE_ONLY && !caps.runsOnDevice -> "off-device provider blocked by privacy mode"
                caps.modelClass !in allowed -> "model class ${caps.modelClass} not allowed in $mode"
                !caps.supportsStructuredOutput -> "no structured output"
                request.untrustedInput.length > caps.maxInputChars -> "input too large"
                p.availability() is Availability.Unavailable -> "unavailable"
                else -> null
            }
            if (reason == null) return RouteDecision(p, mode, skipped)
            skipped += "${p.id}: $reason"
        }
        return RouteDecision(fallback, mode, skipped)
    }

    fun fallbackProvider(): AiModelProvider = fallback
}
