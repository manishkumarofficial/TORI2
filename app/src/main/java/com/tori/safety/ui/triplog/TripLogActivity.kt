package com.tori.safety.ui.triplog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tori.safety.databinding.ActivityTripLogBinding

/**
 * Activity for viewing trip logs
 */
class TripLogActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTripLogBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
    }
    
    private fun setupUI() {
        // Set up the UI components
        supportActionBar?.title = "Trip Log"
    }
}