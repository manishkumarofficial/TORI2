package com.tori.safety.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tori.safety.ui.base.ToriBaseActivity
import com.tori.safety.R
import com.tori.safety.databinding.ActivityMainBinding
import com.tori.safety.ui.contacts.ContactsActivity
import com.tori.safety.ui.monitoring.MonitoringActivity
import com.tori.safety.ui.settings.SettingsActivity
import com.tori.safety.ui.triplog.TripLogActivity
import com.tori.safety.voice.VoiceAssistant
import com.tori.safety.voice.VoiceState
import kotlinx.coroutines.launch

/**
 * Main activity - Dashboard for TOR-I app with Tori voice assistant
 */
class MainActivity : ToriBaseActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceAssistant: VoiceAssistant
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_AUDIO_PERMISSION = 200
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        setupVoiceAssistant()
    }
    
    private fun setupClickListeners() {
        binding.btnStartMonitoring.setOnClickListener {
            startActivity(Intent(this, MonitoringActivity::class.java))
        }

        binding.btnEmergencySos.setOnClickListener {
            // TODO: Show SOS dialog
            Toast.makeText(this, "SOS Feature Coming Soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnTripLog.setOnClickListener {
            startActivity(Intent(this, TripLogActivity::class.java))
        }

        binding.btnContacts.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        
        binding.btnHudMode.setOnClickListener {
            startActivity(Intent(this, com.tori.safety.ui.hud.HudActivity::class.java))
        }
        
        // Voice assistant click listener
        binding.voiceAssistantContainer.setOnClickListener {
            toggleVoiceAssistant()
        }
    }
    
    private fun setupVoiceAssistant() {
        voiceAssistant = VoiceAssistant(this)
        
        // Observe voice state changes
        lifecycleScope.launch {
            voiceAssistant.voiceState.collect { state ->
                updateVoiceUI(state)
            }
        }
        
        // Observe voice responses
        lifecycleScope.launch {
            voiceAssistant.response.collect { response ->
                handleVoiceResponse(response)
            }
        }
        
        // Check permissions and initialize
        if (hasAudioPermission()) {
            initializeVoiceAssistant()
        } else {
            requestAudioPermission()
        }
    }
    
    private fun initializeVoiceAssistant() {
        lifecycleScope.launch {
            try {
                voiceAssistant.initialize()
                voiceAssistant.startListening()
                Log.d(TAG, "Voice assistant initialized and listening")
                
                // Update UI to show Tori is ready
                binding.tvVoiceStatus.text = "Say 'Hey Tor'"
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize voice assistant", e)
                Toast.makeText(this@MainActivity, "Voice assistant initialization failed", Toast.LENGTH_SHORT).show()
                binding.tvVoiceStatus.text = "Voice Error"
            }
        }
    }
    
    private fun toggleVoiceAssistant() {
        if (!hasAudioPermission()) {
            requestAudioPermission()
            return
        }
        
        // For now, just show a message that Tori is listening
        Toast.makeText(this, "Tori is listening... Say 'Hey Tor' to activate", Toast.LENGTH_LONG).show()
    }
    
    private fun updateVoiceUI(state: VoiceState) {
        binding.voiceAssistantView.setState(state)
        
        val statusText = when (state) {
            VoiceState.IDLE -> "Tori"
            VoiceState.LISTENING_FOR_WAKE_WORD -> "Listening..."
            VoiceState.WAKE_WORD_DETECTED -> "Activated!"
            VoiceState.LISTENING_FOR_COMMAND -> "Speak now"
            VoiceState.PROCESSING -> "Thinking..."
            VoiceState.SPEAKING -> "Speaking"
            VoiceState.ERROR -> "Error"
        }
        
        binding.tvVoiceStatus.text = statusText
    }
    
    private fun handleVoiceResponse(response: com.tori.safety.voice.VoiceResponse) {
        Log.d(TAG, "Voice response: ${response.message}")
        
        // Show response in a toast for now
        Toast.makeText(this, response.message, Toast.LENGTH_SHORT).show()
        
        // Handle specific response types
        when (response.type) {
            com.tori.safety.voice.ResponseType.WAKE_WORD_ACKNOWLEDGED -> {
                // Tori is now listening for commands
            }
            com.tori.safety.voice.ResponseType.COMMAND_RESPONSE -> {
                // Handle command response
                response.data?.let { data ->
                    handleCommandData(data)
                }
            }
            com.tori.safety.voice.ResponseType.ERROR -> {
                // Handle error
            }
            com.tori.safety.voice.ResponseType.SYSTEM_MESSAGE -> {
                // Handle system message
            }
        }
    }
    
    private fun handleCommandData(data: Map<String, Any>) {
        // Handle specific command actions based on data
        when {
            data["needsLocationSearch"] == true -> {
                // TODO: Implement location search
                Log.d(TAG, "Location search requested")
            }
            data["needsRestAreaSearch"] == true -> {
                // TODO: Implement rest area search
                Log.d(TAG, "Rest area search requested")
            }
            data["needsFoodSearch"] == true -> {
                // TODO: Implement food search
                Log.d(TAG, "Food search requested")
            }
            data["needsSOSActivation"] == true -> {
                // TODO: Activate SOS
                Log.d(TAG, "SOS activation requested")
            }
        }
    }
    
    private fun hasAudioPermission(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_AUDIO_PERMISSION)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeVoiceAssistant()
            } else {
                Toast.makeText(this, "Audio permission required for voice assistant", Toast.LENGTH_LONG).show()
                binding.tvVoiceStatus.text = "No Permission"
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::voiceAssistant.isInitialized) {
            voiceAssistant.release()
        }
    }
}
