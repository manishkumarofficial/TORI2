package com.tori.safety.data.repository

import com.tori.safety.data.database.TripLogDao
import com.tori.safety.data.model.TripLog
import kotlinx.coroutines.flow.Flow
/**
 * Repository for trip logs
 */
class TripLogRepository(
    private val tripLogDao: TripLogDao
) {
    
    fun getAllTripLogs(): Flow<List<TripLog>> {
        return tripLogDao.getAllTripLogs()
    }
    
    suspend fun getTripLogById(id: Long): TripLog? {
        return tripLogDao.getTripLogById(id)
    }
    
    suspend fun getCurrentTrip(): TripLog? {
        return tripLogDao.getCurrentTrip()
    }
    
    fun getTripLogsByDateRange(startDate: Long, endDate: Long): Flow<List<TripLog>> {
        return tripLogDao.getTripLogsByDateRange(startDate, endDate)
    }
    
    suspend fun startNewTrip(): Long {
        val tripLog = TripLog(
            startTime = System.currentTimeMillis(),
            isCompleted = false
        )
        return tripLogDao.insertTripLog(tripLog)
    }
    
    suspend fun updateTripLog(tripLog: TripLog) {
        tripLogDao.updateTripLog(tripLog)
    }
    
    suspend fun completeTrip(tripId: Long, endLocation: String? = null) {
        val tripLog = tripLogDao.getTripLogById(tripId)
        tripLog?.let {
            val duration = System.currentTimeMillis() - it.startTime
            val completedTrip = it.copy(
                endTime = System.currentTimeMillis(),
                duration = duration,
                endLocation = endLocation,
                isCompleted = true
            )
            tripLogDao.updateTripLog(completedTrip)
        }
    }
    
    suspend fun updateTripStats(
        tripId: Long,
        alertCount: Int,
        sosCount: Int,
        maxEarValue: Double,
        minEarValue: Double,
        averageEarValue: Double
    ) {
        val tripLog = tripLogDao.getTripLogById(tripId)
        tripLog?.let {
            val updatedTrip = it.copy(
                alertCount = alertCount,
                sosCount = sosCount,
                maxEarValue = maxEarValue,
                minEarValue = minEarValue,
                averageEarValue = averageEarValue
            )
            tripLogDao.updateTripLog(updatedTrip)
        }
    }
    
    suspend fun deleteTripLog(tripLog: TripLog) {
        tripLogDao.deleteTripLog(tripLog)
    }
    
    suspend fun deleteTripLogById(id: Long) {
        tripLogDao.deleteTripLogById(id)
    }
    
    suspend fun deleteOldTripLogs(cutoffTime: Long) {
        tripLogDao.deleteOldTripLogs(cutoffTime)
    }
    
    suspend fun getTripLogCount(): Int {
        return tripLogDao.getTripLogCount()
    }
    
    suspend fun getTotalDrivingTime(): Long {
        return tripLogDao.getTotalDrivingTime() ?: 0L
    }
    
    suspend fun getTotalAlertCount(): Int {
        return tripLogDao.getTotalAlertCount() ?: 0
    }
    
    suspend fun getTotalSosCount(): Int {
        return tripLogDao.getTotalSosCount() ?: 0
    }
}
