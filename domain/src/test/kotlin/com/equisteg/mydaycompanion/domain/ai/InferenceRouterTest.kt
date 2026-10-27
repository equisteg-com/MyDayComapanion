package com.equisteg.mydaycompanion.domain.ai

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FakeProvider(
    override val id: String,
    private val modelClass: ModelClass,
    private val onDevice: Boolean = true,
    var available: Boolean = true,
    var output: AiResult? = null,
) : AiModelProvider {
    override val version = "fake/1"
    override fun capabilities() = ProviderCapabilities(modelClass, onDevice, true, false, 2_000, 16_000)
    override fun estimateCost(request: AiRequest) = CostEstimate(0.0, 5)
    override suspend fun availability() = if (available) Availability.Available else Availability.Unavailable("off")
    override suspend fun generate(request: AiRequest) = output ?: AiResult.Failure("no output", id)
}

class InferenceRouterTest {
    private val rules = RuleBasedTaskExtractor()
    private val small = FakeProvider("small", ModelClass.SMALL_LOCAL)
    private val large = FakeProvider("large", ModelClass.LARGE_LOCAL)
    private val cloud = FakeProvider("cloud", ModelClass.CLOUD, onDevice = false)
    private val router = AiInferenceRouter(listOf(cloud, large, small, rules), rules)
    private val request = ContextBuilder().taskExtraction("call Raj", LocalDate.of(2026, 10, 6), emptyList())

    @Test
    fun `cloud providers are never selected in on-device privacy mode`() = runTest {
        assertEquals("large", router.route(request, DeviceState(90, false)).provider.id)
    }

    @Test
    fun `energy policy downgrades model class`() = runTest {
        assertEquals("small", router.route(request, DeviceState(30, false)).provider.id)
        assertEquals("rules", router.route(request, DeviceState(10, false)).provider.id)
        assertEquals("rules", router.route(request, DeviceState(90, true, thermal = ThermalState.SEVERE)).provider.id)
        assertEquals("large", router.route(request, DeviceState(10, true)).provider.id)
    }

    @Test
    fun `unavailable providers fall through to the deterministic fallback`() = runTest {
        large.available = false
        small.available = false
        assertEquals("rules", router.route(request, DeviceState(90, false)).provider.id)
    }

    @Test
    fun `fallback must be deterministic and on device`() {
        assertFailsWith<IllegalArgumentException> { AiInferenceRouter(emptyList(), small) }
    }

    @Test
    fun `energy modes`() {
        assertEquals(EnergyMode.MAINTENANCE_ALLOWED, EnergyPolicy.modeFor(DeviceState(50, isCharging = true, isIdle = true)))
        assertEquals(EnergyMode.REDUCED, EnergyPolicy.modeFor(DeviceState(90, false, isPowerSaveMode = true)))
        assertEquals(EnergyMode.REDUCED, EnergyPolicy.modeFor(DeviceState(90, false, totalRamMb = 2_048)))
        assertEquals(EnergyMode.NORMAL, EnergyPolicy.modeFor(DeviceState(90, false)))
    }
}
