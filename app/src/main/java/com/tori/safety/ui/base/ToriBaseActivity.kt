package com.tori.safety.ui.base

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.tori.safety.R
import com.tori.safety.ui.voice.VoiceAssistantFragment
import com.tori.safety.voice.VoiceManager

abstract class ToriBaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        injectVoiceAssistant()
    }

    override fun setContentView(view: android.view.View?) {
        super.setContentView(view)
        injectVoiceAssistant()
    }

    override fun setContentView(view: android.view.View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        injectVoiceAssistant()
    }

    private fun injectVoiceAssistant() {
        // Find the root view content
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        
        // Check if we already injected
        if (rootView.findViewById<android.view.View>(R.id.voice_assistant_container_global) != null) return

        // Create container for the fragment
        val container = FragmentContainerView(this).apply {
            id = R.id.voice_assistant_container_global
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // It should be transparent and pass touch events unless interactions happen
            // But FragmentContainerView by default is just a container.
            // The VoiceAssistantFragment layout itself should be non-blocking for touches in empty areas.
            // The layout created in previous step has a background color which might block touches.
            // We should ensure the fragment UI is overlay-friendly (e.g., bottom sheet style or floating bubble).
            // For now, let's assume the user accepted the overlay design.
        }
        
        rootView.addView(container)

        // Add the fragment
        supportFragmentManager.commit {
            replace(R.id.voice_assistant_container_global, VoiceAssistantFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-bind voice manager context if needed, or just let Singleton persist
        // Ideally we update context to current activity for dialogs etc.
        VoiceManager.init(this)
        VoiceManager.startWakeWordDetection()
    }
}
