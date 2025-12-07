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
    private lateinit var voiceManager: VoiceManager
    private lateinit var tts: ToriTTS
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var commandProcessor: CommandProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize dependencies
        val context = requireContext()
        // API Key should be securely stored. Using a placeholder or retrieving from BuildConfig
        val apiKey = "AIzaSyAG9VdNAUhmY3b-qmrCQ-hCfXcoXjsHrtE" // TODO: Replace with actual key or mechanism
        
        voiceManager = VoiceManager(context)
        tts = ToriTTS(context)
        geminiHelper = GeminiHelper(apiKey)
        commandProcessor = CommandProcessor(context, tts, geminiHelper)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Programmatically creating view for simplicity, or inflate layout
        // Let's assume we have a layout or create a simple one
        return inflater.inflate(R.layout.fragment_voice_assistant, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        visualizer = view.findViewById(R.id.visualizer)
        statusText = view.findViewById(R.id.tvStatus)
        transcriptText = view.findViewById(R.id.tvTranscript)

        setupVoiceListeners()
        
        // Start listening for Wake Word
        voiceManager.startListening()
    }

    private fun setupVoiceListeners() {
        // Observe Voice State
        lifecycleScope.launch {
            voiceManager.voiceState.collect { state ->
                when (state) {
                    is VoiceManager.VoiceState.Idle -> {
                        visualizer.setState(ToriVisualizerView.State.IDLE)
                        statusText.text = "Say 'Hey Tori'"
                    }
                    is VoiceManager.VoiceState.Listening -> {
                        visualizer.setState(ToriVisualizerView.State.LISTENING)
                        statusText.text = "Listening..."
                    }
                    is VoiceManager.VoiceState.Processing -> {
                        visualizer.setState(ToriVisualizerView.State.THINKING)
                        statusText.text = "Thinking..."
                    }
                    is VoiceManager.VoiceState.Speaking -> {
                        visualizer.setState(ToriVisualizerView.State.SPEAKING)
                        statusText.text = "Speaking..."
                    }
                    is VoiceManager.VoiceState.Error -> {
                        visualizer.setState(ToriVisualizerView.State.IDLE)
                        statusText.text = "Error: ${state.message}"
                    }
                }
            }
        }

        // Handle recognized commands
        voiceManager.onCommandRecognized = { text ->
            transcriptText.text = text
            commandProcessor.process(text)
        }
        
        // Observe Processor State
        lifecycleScope.launch {
            commandProcessor.processorState.collect { state ->
                when (state) {
                    is CommandProcessor.ProcessorState.Result -> {
                        transcriptText.text = state.reply
                        // Resume wake word mode after a delay?
                        voiceManager.setWakeWordMode(true)
                        voiceManager.startListening()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
        tts.shutdown()
    }
}
