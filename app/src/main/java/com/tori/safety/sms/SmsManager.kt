package com.tori.safety.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.tori.safety.data.model.EmergencyContact
import com.tori.safety.location.LocationResult
import com.tori.safety.location.getFormattedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SMS manager for TOR-I app - handles sending SMS alerts to emergency contacts
 */
class SmsManager(private val context: Context) {
    
    private val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    }
    
    private val telephonyManager: TelephonyManager = 
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    
    /**
     * Check if SMS permission is granted
     */
    fun hasSmsPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if device can send SMS
     */
    fun canSendSms(): Boolean {
        return hasSmsPermission() && telephonyManager.simState == TelephonyManager.SIM_STATE_READY
    }
    
    /**
     * Send SOS message to emergency contacts
     */
    suspend fun sendSOSMessage(
        contacts: List<EmergencyContact>,
        locationResult: LocationResult,
        isAutoSOS: Boolean = false
    ): SmsResult = withContext(Dispatchers.IO) {
        
        if (!canSendSms()) {
            return@withContext SmsResult(
                success = false,
                error = "SMS permission not granted or SIM not available",
                sentCount = 0,
                totalCount = contacts.size
            )
        }
        
        val locationString = locationResult.getFormattedLocation()
        val message = createSOSMessage(locationString, isAutoSOS)
        var sentCount = 0
        var errorMessage: String? = null
        
        try {
            for (contact in contacts) {
                try {
                    smsManager.sendTextMessage(
                        contact.phoneNumber,
                        null,
                        message,
                        null,
                        null
                    )
                    sentCount++
                } catch (e: Exception) {
                    errorMessage = e.message
                    // Continue trying to send to other contacts
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message
        }
        
        SmsResult(
            success = sentCount > 0,
            error = errorMessage,
            sentCount = sentCount,
            totalCount = contacts.size
        )
    }
    
    /**
     * Send test message to verify SMS functionality
     */
    suspend fun sendTestMessage(contact: EmergencyContact): SmsResult = withContext(Dispatchers.IO) {
        
        if (!canSendSms()) {
            return@withContext SmsResult(
                success = false,
                error = "SMS permission not granted or SIM not available",
                sentCount = 0,
                totalCount = 1
            )
        }
        
        val message = "TOR-I Test Message: This is a test message from TOR-I Safety App. SMS functionality is working correctly."
        
        return@withContext try {
            smsManager.sendTextMessage(
                contact.phoneNumber,
                null,
                message,
                null,
                null
            )
            SmsResult(
                success = true,
                error = null,
                sentCount = 1,
                totalCount = 1
            )
        } catch (e: Exception) {
            SmsResult(
                success = false,
                error = e.message,
                sentCount = 0,
                totalCount = 1
            )
        }
    }
    
    /**
     * Create SOS message content
     */
    private fun createSOSMessage(location: String, isAutoSOS: Boolean): String {
        val sosType = if (isAutoSOS) "AUTO" else "MANUAL"
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        return """
            🚨 TOR-I EMERGENCY ALERT 🚨
            
            SOS Type: $sosType SOS
            Time: $timestamp
            Location: $location
            
            The driver may be in distress or unconscious.
            Please contact emergency services immediately.
            
            This message was sent automatically by TOR-I Safety App.
        """.trimIndent()
    }
    
    /**
     * Create break reminder message
     */
    suspend fun sendBreakReminderMessage(
        contacts: List<EmergencyContact>,
        locationResult: LocationResult
    ): SmsResult = withContext(Dispatchers.IO) {
        
        if (!canSendSms()) {
            return@withContext SmsResult(
                success = false,
                error = "SMS permission not granted or SIM not available",
                sentCount = 0,
                totalCount = contacts.size
            )
        }
        
        val locationString = locationResult.getFormattedLocation()
        val message = createBreakReminderMessage(locationString)
        var sentCount = 0
        var errorMessage: String? = null
        
        try {
            for (contact in contacts) {
                try {
                    smsManager.sendTextMessage(
                        contact.phoneNumber,
                        null,
                        message,
                        null,
                        null
                    )
                    sentCount++
                } catch (e: Exception) {
                    errorMessage = e.message
                    // Continue trying to send to other contacts
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message
        }
        
        SmsResult(
            success = sentCount > 0,
            error = errorMessage,
            sentCount = sentCount,
            totalCount = contacts.size
        )
    }
    
    /**
     * Create break reminder message content
     */
    private fun createBreakReminderMessage(location: String): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        return """
            ⚠️ TOR-I BREAK REMINDER ⚠️
            
            Time: $timestamp
            Location: $location
            
            The driver has been showing signs of drowsiness and needs to take a break.
            Please check on them if possible.
            
            This message was sent by TOR-I Safety App.
        """.trimIndent()
    }
}