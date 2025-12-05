package com.tori.safety.ui.triplog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tori.safety.TorIApplication
import com.tori.safety.data.model.TripLog
import com.tori.safety.data.repository.TripLogRepository
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

/**
 * ViewModel for TripLogActivity
 */
class TripLogViewModel : ViewModel() {
    
    private val application = TorIApplication.instance
    private val tripLogRepository: TripLogRepository = application.tripLogRepository
    
    private val _tripLogs = MutableLiveData<List<TripLog>>()
    val tripLogs: LiveData<List<TripLog>> = _tripLogs
    
    private val _tripStats = MutableLiveData<TripStats>()
    val tripStats: LiveData<TripStats> = _tripStats
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _successMessage = MutableLiveData<String>()
    val successMessage: LiveData<String> = _successMessage
    
    init {
        loadTripLogs()
        loadTripStats()
    }
    
    fun loadTripLogs() {
        viewModelScope.launch {
            try {
                tripLogRepository.getAllTripLogs().collect { tripLogList ->
                    _tripLogs.value = tripLogList
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load trip logs: ${e.message}"
            }
        }
    }
    
    private fun loadTripStats() {
        viewModelScope.launch {
            try {
                val totalTrips = tripLogRepository.getTripLogCount()
                val totalDrivingTime = tripLogRepository.getTotalDrivingTime()
                val totalAlerts = tripLogRepository.getTotalAlertCount()
                val totalSos = tripLogRepository.getTotalSosCount()
                
                _tripStats.value = TripStats(
                    totalTrips = totalTrips,
                    totalDrivingTime = totalDrivingTime,
                    totalAlerts = totalAlerts,
                    totalSos = totalSos
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load trip statistics: ${e.message}"
            }
        }
    }
    
    fun exportTripLogs() {
        viewModelScope.launch {
            try {
                val tripLogs = _tripLogs.value ?: return@launch
                
                // Create CSV content
                val csvContent = buildString {
                    // Header
                    appendLine("Date,Start Time,Duration,Alerts,SOS Events,Start Location,End Location")
                    
                    // Data rows
                    tripLogs.forEach { tripLog ->
                        val date = formatDate(tripLog.startTime)
                        val startTime = formatTime(tripLog.startTime)
                        val duration = formatDuration(tripLog.duration)
                        val alerts = tripLog.alertCount
                        val sos = tripLog.sosCount
                        val startLocation = tripLog.startLocation ?: ""
                        val endLocation = tripLog.endLocation ?: ""
                        
                        appendLine("$date,$startTime,$duration,$alerts,$sos,$startLocation,$endLocation")
                    }
                }
                
                // Write to file (in a real app, you'd want to save to external storage)
                val fileName = "tori_trip_logs_${System.currentTimeMillis()}.csv"
                val file = File(application.filesDir, fileName)
                
                FileWriter(file).use { writer ->
                    writer.write(csvContent)
                }
                
                _successMessage.value = "Trip logs exported to: $fileName"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to export trip logs: ${e.message}"
            }
        }
    }
    
    fun clearMessages() {
        _errorMessage.value = ""
        _successMessage.value = ""
    }
    
    private fun formatDate(timestamp: Long): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }
    
    private fun formatTime(timestamp: Long): String {
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return timeFormat.format(java.util.Date(timestamp))
    }
    
    private fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        return "${hours}:${minutes.toString().padStart(2, '0')}"
    }
}

/**
 * Trip statistics data class
 */
data class TripStats(
    val totalTrips: Int = 0,
    val totalDrivingTime: Long = 0L,
    val totalAlerts: Int = 0,
    val totalSos: Int = 0
)
