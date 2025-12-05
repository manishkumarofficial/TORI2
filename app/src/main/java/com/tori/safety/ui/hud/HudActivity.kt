package com.tori.safety.ui.hud

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.tori.safety.databinding.ActivityHudBinding

class HudActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHudBinding
    private var isMirrored = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHudBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.fabClose.setOnClickListener {
            finish()
        }

        binding.fabMirror.setOnClickListener {
            toggleMirrorMode()
        }
    }

    private fun toggleMirrorMode() {
        isMirrored = !isMirrored
        // Mirror the entire root layout
        // We pivot around the center to keep it in place
        binding.layoutHudRoot.pivotX = binding.layoutHudRoot.width / 2f
        binding.layoutHudRoot.scaleX = if (isMirrored) -1f else 1f
        
        // We need to un-mirror the buttons so they are still readable/clickable normally? 
        // Actually, in HUD mode, EVERYTHING should be mirrored because you see it reflection.
        // So mirroring the root is correct.
    }
}
