package com.tori.safety.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Trip log entry data model
 */
@Entity(tableName = "trip_logs")
data class TripLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Long = 0, // in milliseconds
    val alertCount: Int = 0,
    val sosCount: Int = 0,
    val maxEarValue: Double = 0.0,
    val minEarValue: Double = 0.0,
    val averageEarValue: Double = 0.0,
    val startLocation: String? = null,
    val endLocation: String? = null,
    val isCompleted: Boolean = false,
    val safetyScore: Int = 100,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
