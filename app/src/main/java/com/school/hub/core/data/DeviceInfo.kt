package com.school.hub.core.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceSpecs(val totalRamGb: Double, val availRamGb: Double, val abi: String, val is64Bit: Boolean)

object DeviceInfo {
    fun read(context: Context): DeviceSpecs {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = 1024.0 * 1024.0 * 1024.0
        return DeviceSpecs(
            totalRamGb = mi.totalMem / gb,
            availRamGb = mi.availMem / gb,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
        )
    }
}
