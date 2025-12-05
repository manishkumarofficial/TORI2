package com.tori.safety.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Manager for battery optimization and power management
 */
class BatteryOptimizationManager(private val context: Context) {
    
    private val powerManager: PowerManager = 
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    /**
     * Check if battery optimization is disabled for this app
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Battery optimization not available on older versions
        }
    }
    
    /**
     * Request to disable battery optimization for this app
     */
    fun requestDisableBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimizationDisabled()) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
    
    /**
     * Open battery optimization settings
     */
    fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        }
    }
    
    /**
     * Check if device is in Doze mode
     */
    fun isDeviceInDozeMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            false
        }
    }
    
    /**
     * Check if device is in App Standby mode
     */
    fun isAppInStandbyMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
    }
    
    /**
     * Get battery optimization status message
     */
    fun getBatteryOptimizationStatus(): String {
        return when {
            !isBatteryOptimizationDisabled() -> {
                "Battery optimization is enabled. This may affect monitoring performance."
            }
            isDeviceInDozeMode() -> {
                "Device is in Doze mode. Monitoring may be limited."
            }
            isAppInStandbyMode() -> {
                "Device is in power save mode. Some features may be limited."
            }
            else -> {
                "Battery optimization is properly configured."
            }
        }
    }
    
    /**
     * Check if background app refresh is available
     */
    fun isBackgroundAppRefreshAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
    
    /**
     * Get power management recommendations
     */
    fun getPowerManagementRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!isBatteryOptimizationDisabled()) {
            recommendations.add("Disable battery optimization for TOR-I")
        }
        
        if (isDeviceInDozeMode()) {
            recommendations.add("Exit Doze mode for better performance")
        }
        
        if (isAppInStandbyMode()) {
            recommendations.add("Disable power save mode")
        }
        
        recommendations.add("Keep the device charged during long trips")
        recommendations.add("Close unnecessary apps to free up resources")
        
        return recommendations
    }
}
