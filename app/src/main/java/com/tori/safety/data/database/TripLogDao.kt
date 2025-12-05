package com.tori.safety.data.database

import androidx.room.*
import com.tori.safety.data.model.TripLog
import kotlinx.coroutines.flow.Flow

/**
 * DAO for trip logs
 */
@Dao
interface TripLogDao {
    
    @Query("SELECT * FROM trip_logs ORDER BY startTime DESC")
    fun getAllTripLogs(): Flow<List<TripLog>>
    
    @Query("SELECT * FROM trip_logs WHERE id = :id")
    suspend fun getTripLogById(id: Long): TripLog?
    
    @Query("SELECT * FROM trip_logs WHERE isCompleted = 0 LIMIT 1")
    suspend fun getCurrentTrip(): TripLog?
    
    @Query("SELECT * FROM trip_logs WHERE startTime >= :startDate AND startTime <= :endDate ORDER BY startTime DESC")
    fun getTripLogsByDateRange(startDate: Long, endDate: Long): Flow<List<TripLog>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripLog(tripLog: TripLog): Long
    
    @Update
    suspend fun updateTripLog(tripLog: TripLog)
    
    @Delete
    suspend fun deleteTripLog(tripLog: TripLog)
    
    @Query("DELETE FROM trip_logs WHERE id = :id")
    suspend fun deleteTripLogById(id: Long)
    
    @Query("DELETE FROM trip_logs WHERE startTime < :cutoffTime")
    suspend fun deleteOldTripLogs(cutoffTime: Long)
    
    @Query("SELECT COUNT(*) FROM trip_logs")
    suspend fun getTripLogCount(): Int
    
    @Query("SELECT SUM(duration) FROM trip_logs WHERE isCompleted = 1")
    suspend fun getTotalDrivingTime(): Long?
    
    @Query("SELECT SUM(alertCount) FROM trip_logs")
    suspend fun getTotalAlertCount(): Int?
    
    @Query("SELECT SUM(sosCount) FROM trip_logs")
    suspend fun getTotalSosCount(): Int?
}
