package com.tori.safety.data.trip

import com.tori.safety.data.model.TripLog
import com.tori.safety.ml.AlertType

/**
 * Manages trip data collection and scoring.
 */
class TripManager {

    private var startTime: Long = 0
    private var alertCount: Int = 0
    private var sosCount: Int = 0
    private var distractionCount: Int = 0
    private var yawnCount: Int = 0
    
    private var isTracking = false

    fun startTrip() {
        startTime = System.currentTimeMillis()
        alertCount = 0
        sosCount = 0
        distractionCount = 0
        yawnCount = 0
        isTracking = true
    }

    fun recordAlert(type: AlertType) {
        if (!isTracking) return
        
        when (type) {
            AlertType.DROWSINESS -> alertCount++
            AlertType.SOS -> sosCount++
            AlertType.DISTRACTION -> distractionCount++
            AlertType.FAINT -> sosCount++ // Treat faint as SOS severity
        }
    }
    
    fun recordYawn() {
        if (isTracking) yawnCount++
    }

    fun endTrip(): TripLog? {
        if (!isTracking) return null
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val score = calculateSafetyScore(duration)
        
        isTracking = false
        
        return TripLog(
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            alertCount = alertCount + distractionCount, // Total alerts
            sosCount = sosCount,
            safetyScore = score,
            isCompleted = true
        )
    }
    
    private fun calculateSafetyScore(durationMillis: Long): Int {
        // Base score
        var score = 100
        
        // Penalties
        score -= (alertCount * 10) // Drowsiness is serious
        score -= (distractionCount * 2) // Distraction is less serious but bad
        score -= (sosCount * 50) // SOS is critical
        score -= (yawnCount * 1) // Yawns are warning signs
        
        // Duration bonus (if clean)? Or fatigue penalty?
        // For now, simple penalties.
        
        return score.coerceIn(0, 100)
    }
}
