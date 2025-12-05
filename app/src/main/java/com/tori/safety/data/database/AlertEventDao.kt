package com.tori.safety.data.database

import androidx.room.*
import com.tori.safety.data.model.AlertEvent
import com.tori.safety.data.model.AlertType
import kotlinx.coroutines.flow.Flow

/**
 * DAO for alert events
 */
@Dao
interface AlertEventDao {
    
    @Query("SELECT * FROM alert_events ORDER BY timestamp DESC")
    fun getAllAlertEvents(): Flow<List<AlertEvent>>
    
    @Query("SELECT * FROM alert_events WHERE tripLogId = :tripLogId ORDER BY timestamp DESC")
    fun getAlertEventsByTripId(tripLogId: Long): Flow<List<AlertEvent>>
    
    @Query("SELECT * FROM alert_events WHERE id = :id")
    suspend fun getAlertEventById(id: Long): AlertEvent?
    
    @Query("SELECT * FROM alert_events WHERE alertType = :alertType ORDER BY timestamp DESC")
    fun getAlertEventsByType(alertType: AlertType): Flow<List<AlertEvent>>
    
    @Query("SELECT * FROM alert_events WHERE timestamp >= :startDate AND timestamp <= :endDate ORDER BY timestamp DESC")
    fun getAlertEventsByDateRange(startDate: Long, endDate: Long): Flow<List<AlertEvent>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlertEvent(alertEvent: AlertEvent): Long
    
    @Update
    suspend fun updateAlertEvent(alertEvent: AlertEvent)
    
    @Delete
    suspend fun deleteAlertEvent(alertEvent: AlertEvent)
    
    @Query("DELETE FROM alert_events WHERE id = :id")
    suspend fun deleteAlertEventById(id: Long)
    
    @Query("DELETE FROM alert_events WHERE tripLogId = :tripLogId")
    suspend fun deleteAlertEventsByTripId(tripLogId: Long)
    
    @Query("DELETE FROM alert_events WHERE timestamp < :cutoffTime")
    suspend fun deleteOldAlertEvents(cutoffTime: Long)
    
    @Query("SELECT COUNT(*) FROM alert_events WHERE tripLogId = :tripLogId")
    suspend fun getAlertCountByTripId(tripLogId: Long): Int
    
    @Query("SELECT COUNT(*) FROM alert_events WHERE tripLogId = :tripLogId AND alertType IN ('MANUAL_SOS', 'AUTO_SOS')")
    suspend fun getSosCountByTripId(tripLogId: Long): Int
}
