package com.tori.safety.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tori.safety.R
import com.tori.safety.databinding.ActivityMainBinding
import com.tori.safety.ui.contacts.ContactsActivity
import com.tori.safety.ui.monitoring.MonitoringActivity
import com.tori.safety.ui.settings.SettingsActivity
import com.tori.safety.ui.triplog.TripLogActivity

/**
 * Main activity - Dashboard for TOR-I app
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
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
    }
}
