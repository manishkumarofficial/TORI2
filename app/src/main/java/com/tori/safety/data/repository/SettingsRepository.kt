package com.tori.safety.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.tori.safety.data.model.DetectionSettings
import com.tori.safety.data.model.Language

/**
 * Repository for managing application settings
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): DetectionSettings {
        return DetectionSettings(
            earThreshold = prefs.getFloat(KEY_EAR_THRESHOLD, 0.25f).toDouble(),
            consecutiveFrames = prefs.getInt(KEY_CONSECUTIVE_FRAMES, 10),
            alertVolume = prefs.getInt(KEY_ALERT_VOLUME, 80),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
            ttsEnabled = prefs.getBoolean(KEY_TTS_ENABLED, true),
            language = Language.valueOf(prefs.getString(KEY_LANGUAGE, Language.ENGLISH.name) ?: Language.ENGLISH.name),
            lowPowerMode = prefs.getBoolean(KEY_LOW_POWER_MODE, false),
            backgroundMonitoring = prefs.getBoolean(KEY_BACKGROUND_MONITORING, false),
            autoStart = prefs.getBoolean(KEY_AUTO_START, false)
        )
    }

    fun saveSettings(settings: DetectionSettings) {
        prefs.edit().apply {
            putFloat(KEY_EAR_THRESHOLD, settings.earThreshold.toFloat())
            putInt(KEY_CONSECUTIVE_FRAMES, settings.consecutiveFrames)
            putInt(KEY_ALERT_VOLUME, settings.alertVolume)
            putBoolean(KEY_VIBRATION_ENABLED, settings.vibrationEnabled)
            putBoolean(KEY_TTS_ENABLED, settings.ttsEnabled)
            putString(KEY_LANGUAGE, settings.language.name)
            putBoolean(KEY_LOW_POWER_MODE, settings.lowPowerMode)
            putBoolean(KEY_BACKGROUND_MONITORING, settings.backgroundMonitoring)
            putBoolean(KEY_AUTO_START, settings.autoStart)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "tori_settings"
        private const val KEY_EAR_THRESHOLD = "ear_threshold"
        private const val KEY_CONSECUTIVE_FRAMES = "consecutive_frames"
        private const val KEY_ALERT_VOLUME = "alert_volume"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LOW_POWER_MODE = "low_power_mode"
        private const val KEY_BACKGROUND_MONITORING = "background_monitoring"
        private const val KEY_AUTO_START = "auto_start"
    }
}
