package com.tori.safety.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/**
 * Location manager for TOR-I app - handles GPS location retrieval
 */
class LocationManager(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Get the current location of the device
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationResult {
        return try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()
            
            LocationResult.Success(location)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current location", e)
            LocationResult.Error("Failed to get location: ${e.message}")
        }
    }
    
    /**
     * Get the last known location of the device
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): LocationResult {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                LocationResult.Success(location)
            } else {
                LocationResult.Error("No last known location available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last known location", e)
            LocationResult.Error("Failed to get last known location: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "LocationManager"
    }
}

sealed class LocationResult {
    data class Success(val location: Location) : LocationResult()
    data class Error(val message: String) : LocationResult()
}

// Extension function to get a formatted location string
fun LocationResult.getFormattedLocation(): String {
    return when (this) {
        is LocationResult.Success -> {
            "${this.location.latitude}, ${this.location.longitude}"
        }
        is LocationResult.Error -> {
            "Location unavailable: ${this.message}"
        }
    }
}

/**
 * Location status data class
 */
data class LocationStatus(
    val hasPermission: Boolean,
    val isEnabled: Boolean,
    val isGpsEnabled: Boolean,
    val isNetworkEnabled: Boolean
)
