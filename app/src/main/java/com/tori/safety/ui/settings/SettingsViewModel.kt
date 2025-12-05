package com.tori.safety.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tori.safety.data.model.DetectionSettings
import com.tori.safety.data.model.Language
import kotlinx.coroutines.launch

/**
 * ViewModel for SettingsActivity
 */
class SettingsViewModel : ViewModel() {
    
    private val _settings = MutableLiveData<DetectionSettings>()
    val settings: LiveData<DetectionSettings> = _settings
    
    private var currentSettings = DetectionSettings()
    
    init {
        _settings.value = currentSettings
    }
    
    fun loadSettings(context: Context) {
        viewModelScope.launch {
            try {
                val sharedPreferences = context.getSharedPreferences("tori_settings", Context.MODE_PRIVATE)
                
                val earThreshold = sharedPreferences.getFloat("ear_threshold", 0.25f).toDouble()
                val consecutiveFrames = sharedPreferences.getInt("consecutive_frames", 10)
                val alertVolume = sharedPreferences.getInt("alert_volume", 80)
                val vibrationEnabled = sharedPreferences.getBoolean("vibration_enabled", true)
                val ttsEnabled = sharedPreferences.getBoolean("tts_enabled", true)
                val languageString = sharedPreferences.getString("language", "ENGLISH") ?: "ENGLISH"
                val language = try {
                    Language.valueOf(languageString)
                } catch (e: Exception) {
                    Language.ENGLISH
                }
                val lowPowerMode = sharedPreferences.getBoolean("low_power_mode", false)
                val backgroundMonitoring = sharedPreferences.getBoolean("background_monitoring", false)
                val autoStart = sharedPreferences.getBoolean("auto_start", false)
                
                currentSettings = DetectionSettings(
                    earThreshold = earThreshold,
                    consecutiveFrames = consecutiveFrames,
                    alertVolume = alertVolume,
                    vibrationEnabled = vibrationEnabled,
                    ttsEnabled = ttsEnabled,
                    language = language,
                    lowPowerMode = lowPowerMode,
                    backgroundMonitoring = backgroundMonitoring,
                    autoStart = autoStart
                )
                
                _settings.value = currentSettings
            } catch (e: Exception) {
                // Handle error - use default settings
                _settings.value = DetectionSettings()
            }
        }
    }
    
    fun updateEarThreshold(threshold: Double) {
        currentSettings = currentSettings.copy(earThreshold = threshold)
        _settings.value = currentSettings
    }
    
    fun updateConsecutiveFrames(frames: Int) {
        currentSettings = currentSettings.copy(consecutiveFrames = frames)
        _settings.value = currentSettings
    }
    
    fun updateAlertVolume(volume: Int) {
        currentSettings = currentSettings.copy(alertVolume = volume)
        _settings.value = currentSettings
    }
    
    fun updateVibrationEnabled(enabled: Boolean) {
        currentSettings = currentSettings.copy(vibrationEnabled = enabled)
        _settings.value = currentSettings
    }
    
    fun updateTtsEnabled(enabled: Boolean) {
        currentSettings = currentSettings.copy(ttsEnabled = enabled)
        _settings.value = currentSettings
    }
    
    fun updateLanguage(language: Language) {
        currentSettings = currentSettings.copy(language = language)
        _settings.value = currentSettings
    }
    
    fun updateLowPowerMode(enabled: Boolean) {
        currentSettings = currentSettings.copy(lowPowerMode = enabled)
        _settings.value = currentSettings
    }
    
    fun updateBackgroundMonitoring(enabled: Boolean) {
        currentSettings = currentSettings.copy(backgroundMonitoring = enabled)
        _settings.value = currentSettings
    }
    
    fun updateAutoStart(enabled: Boolean) {
        currentSettings = currentSettings.copy(autoStart = enabled)
        _settings.value = currentSettings
    }
    
    fun saveSettings(context: Context) {
        viewModelScope.launch {
            try {
                val sharedPreferences = context.getSharedPreferences("tori_settings", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                
                editor.putFloat("ear_threshold", currentSettings.earThreshold.toFloat())
                editor.putInt("consecutive_frames", currentSettings.consecutiveFrames)
                editor.putInt("alert_volume", currentSettings.alertVolume)
                editor.putBoolean("vibration_enabled", currentSettings.vibrationEnabled)
                editor.putBoolean("tts_enabled", currentSettings.ttsEnabled)
                editor.putString("language", currentSettings.language.name)
                editor.putBoolean("low_power_mode", currentSettings.lowPowerMode)
                editor.putBoolean("background_monitoring", currentSettings.backgroundMonitoring)
                editor.putBoolean("auto_start", currentSettings.autoStart)
                
                editor.apply()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun loadSettings() {
        // This method should be called with context from the activity
        // For now, we'll use the default settings
        _settings.value = DetectionSettings()
    }
    
    fun saveSettings() {
        // This method should be called with context from the activity
        // For now, we'll do nothing
    }
}
