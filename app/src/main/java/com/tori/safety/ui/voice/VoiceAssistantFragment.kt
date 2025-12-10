package com.tori.safety.ui.voice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tori.safety.R
import com.tori.safety.voice.CommandProcessor
import com.tori.safety.voice.GeminiHelper
import com.tori.safety.voice.ToriTTS
import com.tori.safety.voice.VoiceManager
import kotlinx.coroutines.launch

class VoiceAssistantFragment : Fragment() {

    private lateinit var visualizer: ToriVisualizerView
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    
    // Dependencies (Should be injected, but creating here for simplicity)
    private lateinit var tts: ToriTTS
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var commandProcessor: CommandProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize dependencies
        val context = requireContext()
        // API Key should be securely stored. Using a placeholder or retrieving from BuildConfig
        val apiKey = "AIzaSyAG9VdNAUhmY3b-qmrCQ-hCfXcoXjsHrtE" // TODO: Replace with actual key or mechanism
        
        // VoiceManager is now a Singleton, ensure init
        VoiceManager.init(context)
        
        tts = ToriTTS(context)
        geminiHelper = GeminiHelper(apiKey)
        commandProcessor = CommandProcessor(context, tts, geminiHelper)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Programmatically creating view for simplicity, or inflate layout
        return inflater.inflate(R.layout.fragment_voice_assistant, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        visualizer = view.findViewById(R.id.visualizer)
        statusText = view.findViewById(R.id.tvStatus)
        transcriptText = view.findViewById(R.id.tvTranscript)

        setupVoiceListeners()
        
        // Start listening for Wake Word
        VoiceManager.startWakeWordDetection()
    }

    private fun setupVoiceListeners() {
        // Observe Voice State
        lifecycleScope.launch {
            VoiceManager.voiceState.collect { state ->
                when (state) {
                    is VoiceManager.VoiceState.Idle -> {
                        visualizer.visibility = View.GONE
                        visualizer.setState(ToriVisualizerView.State.IDLE)
                        statusText.visibility = View.GONE
                    }
                    is VoiceManager.VoiceState.Listening -> {
                        visualizer.visibility = View.VISIBLE
                        statusText.visibility = View.VISIBLE
                        statusText.text = "Listening..."
                        visualizer.setState(ToriVisualizerView.State.LISTENING)
                    }
                    is VoiceManager.VoiceState.Processing -> {
                        visualizer.visibility = View.VISIBLE
                        statusText.text = "Thinking..."
                        visualizer.setState(ToriVisualizerView.State.THINKING)
                    }
                    is VoiceManager.VoiceState.Speaking -> {
                        visualizer.visibility = View.VISIBLE
                        statusText.text = "Speaking..."
                        visualizer.setState(ToriVisualizerView.State.SPEAKING)
                    }
                    is VoiceManager.VoiceState.Error -> {
                        // Error is transient, maybe flash then hide?
                        statusText.text = state.message
                        visualizer.setState(ToriVisualizerView.State.IDLE)
                        // delayed hide?
                    }
                }
            }
        }

        // Handle recognized commands
        // VoiceManager callbacks are now handled by CommandProcessor (which is created here)
        // We just need to ensure CommandProcessor is initialized.
        
        // Observe Processor State for UI updates
        lifecycleScope.launch {
            commandProcessor.processorState.collect { state ->
                when (state) {
                    is CommandProcessor.ProcessorState.Result -> {
                        transcriptText.text = state.reply
                    }
                    is CommandProcessor.ProcessorState.Processing -> {
                         transcriptText.text = state.text
                    }
                    is CommandProcessor.ProcessorState.Error -> {
                         transcriptText.text = state.error
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't fully destroy VoiceManager if we want it global, 
        // but this Fragment is part of the overlay which might be Destroyed.
        // Actually, for BaseActivity, the Fragment might be persistent or recreated.
        tts.shutdown()
    }
}
