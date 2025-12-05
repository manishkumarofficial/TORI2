package com.tori.safety.intelligence

import java.util.Calendar

/**
 * Manages predictive fatigue analysis based on circadian rhythms and drive duration.
 */
class PredictiveFatigueManager {

    private var driveStartTime: Long = 0
    private var isDriving = false

    fun startDrive() {
        driveStartTime = System.currentTimeMillis()
        isDriving = true
    }

    fun stopDrive() {
        isDriving = false
        driveStartTime = 0
    }

    /**
     * Calculates a fatigue risk score from 0 to 100.
     * 0 = Low Risk, 100 = Critical Risk
     */
    fun calculateFatigueRisk(): Int {
        if (!isDriving) return 0

        val circadianScore = getCircadianRiskScore()
        val durationScore = getDurationRiskScore()

        // Weighted average or max logic?
        // Let's sum them up but cap at 100.
        // Circadian rhythm is a multiplier or base risk.
        // Duration adds to it.
        
        var totalRisk = circadianScore + durationScore
        return totalRisk.coerceIn(0, 100)
    }

    private fun getCircadianRiskScore(): Int {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 2..5 -> 80  // 2 AM - 5 AM: High risk (Deep sleep cycle)
            in 13..15 -> 50 // 1 PM - 3 PM: Moderate risk (Post-lunch dip)
            in 0..1 -> 60  // Midnight - 2 AM: High risk
            in 6..12 -> 10 // Morning: Low risk
            in 16..21 -> 20 // Evening: Low/Moderate risk
            in 22..23 -> 40 // Late night: Moderate/High risk
            else -> 10
        }
    }

    private fun getDurationRiskScore(): Int {
        if (driveStartTime == 0L) return 0

        val durationMillis = System.currentTimeMillis() - driveStartTime
        val durationHours = durationMillis / (1000.0 * 60 * 60)

        return when {
            durationHours < 1.0 -> 0
            durationHours < 2.0 -> 10
            durationHours < 3.0 -> 30
            durationHours < 4.0 -> 60
            else -> 90 // > 4 hours without break is critical
        }
    }
}
