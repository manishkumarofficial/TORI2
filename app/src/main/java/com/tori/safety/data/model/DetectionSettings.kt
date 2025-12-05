package com.tori.safety.data.model

/**
 * Detection settings data class
 */
data class DetectionSettings(
    val earThreshold: Double = 0.25, // Default EAR threshold
    val consecutiveFrames: Int = 10, // Frames needed to trigger alert
    val smoothingFactor: Double = 0.7, // Moving average smoothing
    val alertVolume: Int = 80, // Alert volume percentage (0-100)
    val vibrationEnabled: Boolean = true,
    val ttsEnabled: Boolean = true,
    val language: Language = Language.ENGLISH,
    val lowPowerMode: Boolean = false,
    val backgroundMonitoring: Boolean = false,
    val autoStart: Boolean = false,
    val breakReminderEnabled: Boolean = true,
    val coachingTipsEnabled: Boolean = true
)

enum class Language {
    ENGLISH,
    TAMIL
}

/**
 * Current detection state
 */
data class DetectionState(
    val isMonitoring: Boolean = false,
    val isPaused: Boolean = false,
    val currentEarValue: Double = 0.0,
    val averageEarValue: Double = 0.0,
    val consecutiveLowEarFrames: Int = 0,
    val faceDetected: Boolean = false,
    val lastAlertTime: Long = 0,
    val currentTripId: Long? = null,
    val alertCount: Int = 0,
    val sosCount: Int = 0
)
