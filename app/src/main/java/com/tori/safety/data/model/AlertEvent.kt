package com.tori.safety.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Alert event data model
 */
@Entity(tableName = "alert_events")
data class AlertEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripLogId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val earValue: Double,
    val alertType: AlertType,
    val responseType: ResponseType? = null,
    val responseTime: Long? = null,
    val location: String? = null,
    val notes: String? = null
)

enum class AlertType {
    DROWSINESS_DETECTED,
    MANUAL_SOS,
    AUTO_SOS,
    BREAK_REMINDER,
    SYSTEM_ERROR
}

enum class ResponseType {
    ACKNOWLEDGED,
    IGNORED,
    TOOK_BREAK,
    SENT_SOS,
    MANUAL_STOP
}
