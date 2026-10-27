package com.equisteg.mydaycompanion.data.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.equisteg.mydaycompanion.domain.ai.DeviceState
import com.equisteg.mydaycompanion.domain.ai.DeviceStateProvider
import com.equisteg.mydaycompanion.domain.ai.ThermalState

/** Reads battery, charging, power-save, thermal and RAM state on demand (no background polling). */
class AndroidDeviceStateProvider(private val context: Context) : DeviceStateProvider {
    override fun current(): DeviceState {
        // Sticky broadcast read with a null receiver: nothing stays registered.
        val battery = ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 50
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (power.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.NORMAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.ELEVATED
                else -> ThermalState.SEVERE
            }
        } else {
            ThermalState.NORMAL
        }
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        return DeviceState(
            batteryPercent = percent,
            isCharging = plugged,
            isIdle = power.isDeviceIdleMode,
            thermal = thermal,
            totalRamMb = memory.totalMem / (1024 * 1024),
            isPowerSaveMode = power.isPowerSaveMode,
        )
    }
}
