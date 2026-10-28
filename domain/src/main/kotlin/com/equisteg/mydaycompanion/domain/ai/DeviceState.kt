package com.equisteg.mydaycompanion.domain.ai

enum class ThermalState { NORMAL, ELEVATED, SEVERE }

/** Snapshot of the device resources that matter for choosing how much AI work to do. */
data class DeviceState(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isIdle: Boolean = false,
    val thermal: ThermalState = ThermalState.NORMAL,
    val totalRamMb: Long = 4_096,
    val isPowerSaveMode: Boolean = false,
) {
    companion object {
        val UNKNOWN = DeviceState(batteryPercent = 50, isCharging = false)
    }
}

interface DeviceStateProvider {
    fun current(): DeviceState
}

enum class EnergyMode {
    /** Rules only: no model calls, postpone non-critical maintenance. */
    DETERMINISTIC_ONLY,

    /** Smallest suitable local model. */
    REDUCED,

    NORMAL,

    /** Charging and idle: optional heavier maintenance is allowed. */
    MAINTENANCE_ALLOWED,
}

/** Deterministic energy policy from the build spec (section 14). Thresholds are versioned with the code. */
object EnergyPolicy {
    const val VERY_LOW_BATTERY = 15
    const val LOW_BATTERY = 40
    const val LOW_RAM_MB = 3_072L

    fun modeFor(state: DeviceState): EnergyMode = when {
        state.thermal == ThermalState.SEVERE -> EnergyMode.DETERMINISTIC_ONLY
        !state.isCharging && state.batteryPercent <= VERY_LOW_BATTERY -> EnergyMode.DETERMINISTIC_ONLY
        state.isCharging && state.isIdle && state.thermal == ThermalState.NORMAL -> EnergyMode.MAINTENANCE_ALLOWED
        state.isPowerSaveMode || state.thermal == ThermalState.ELEVATED -> EnergyMode.REDUCED
        !state.isCharging && state.batteryPercent <= LOW_BATTERY -> EnergyMode.REDUCED
        state.totalRamMb < LOW_RAM_MB -> EnergyMode.REDUCED
        else -> EnergyMode.NORMAL
    }

    fun allowedModelClasses(mode: EnergyMode): Set<ModelClass> = when (mode) {
        EnergyMode.DETERMINISTIC_ONLY -> setOf(ModelClass.DETERMINISTIC)
        EnergyMode.REDUCED -> setOf(ModelClass.DETERMINISTIC, ModelClass.SMALL_LOCAL)
        EnergyMode.NORMAL, EnergyMode.MAINTENANCE_ALLOWED ->
            setOf(ModelClass.DETERMINISTIC, ModelClass.SMALL_LOCAL, ModelClass.LARGE_LOCAL)
    }
}
