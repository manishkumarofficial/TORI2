package com.tori.safety.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale


object VoiceManager {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var context: Context? = null

    // Mode: Are we waiting for 'Hey Tor' or a Command?
    private var mode = Mode.WAKE_WORD
    
    enum class Mode {
        WAKE_WORD,
        COMMAND
    }

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState

    // Callbacks
    var onWakeWordDetected: (() -> Unit)? = null
    var onCommandReceived: ((String) -> Unit)? = null
    var onCompanionTrigger: (() -> Unit)? = null

    // Companion Logic
    private var isCompanionModeEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private val INACTIVITY_THRESHOLD = 60 * 1000L 
    private val inactivityRunnable = Runnable { triggerCompanionCheck() }

    sealed class VoiceState {
        object Idle : VoiceState()
        object Listening : VoiceState()
        object Processing : VoiceState()
        object Speaking : VoiceState()
        data class Error(val message: String) : VoiceState()
    }

    fun init(ctx: Context) {
        if (context == ctx) return
        context = ctx
        resetRecognizer()
    }

    private fun resetRecognizer() {
        if (context == null) return
        Handler(Looper.getMainLooper()).post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        }
    }

    fun startWakeWordDetection() {
        if (speechRecognizer == null) {
            Log.e("VoiceManager", "Not initialized")
            return
        }
        mode = Mode.WAKE_WORD
        _voiceState.value = VoiceState.Idle // Visualizer hidden in idle
        if (!isListening) startRecognition()
    }

    fun startCommandListening() {
        if (speechRecognizer == null) return
        
        // Stop any current listening to switch modes cleanly
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
        
        mode = Mode.COMMAND
        _voiceState.value = VoiceState.Listening // Visualizer visible
        
        // Short delay to allow mic to reset
        handler.postDelayed({
            startRecognition()
        }, 100)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        _voiceState.value = VoiceState.Idle
    }
    
    // Companion Mode Setters...
    fun setCompanionMode(enabled: Boolean) {
        isCompanionModeEnabled = enabled
        if (enabled) resetInactivityTimer() else handler.removeCallbacks(inactivityRunnable)
    }

    private fun resetInactivityTimer() {
        if (!isCompanionModeEnabled) return
        handler.removeCallbacks(inactivityRunnable)
        handler.postDelayed(inactivityRunnable, INACTIVITY_THRESHOLD)
    }

    private fun triggerCompanionCheck() {
        if (!isCompanionModeEnabled || isListening) return // Don't interrupt if busy
        onCompanionTrigger?.invoke()
    }

    private fun startRecognition() {
        if (context == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Wake word needs continuous listening feel, command needs accurate end detection
            if (mode == Mode.WAKE_WORD) {
                // We want to process stream
            }
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            Handler(Looper.getMainLooper()).post {
                speechRecognizer?.startListening(intent)
                isListening = true
                if (mode == Mode.COMMAND) _voiceState.value = VoiceState.Listening
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error starting recognition", e)
             // Retry?
             handler.postDelayed({ startRecognition() }, 1000)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {
            if (mode == Mode.COMMAND) {
                _voiceState.value = VoiceState.Listening
            }
            resetInactivityTimer()
        }

        override fun onRmsChanged(rmsdB: Float) {
             // Can pass this to visualizer if we want direct audio feedback
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (mode == Mode.COMMAND) {
                _voiceState.value = VoiceState.Processing
            }
        }

        override fun onError(error: Int) {
            isListening = false
            
            // Auto-restart for Wake Word, but maybe fail for Command?
            // User requested robust "always listening" for wake word.
            if (mode == Mode.WAKE_WORD) {
                 // Ignore errors and restart
                 handler.postDelayed({ if (!isListening) startRecognition() }, 500)
                 return
            }
            
            // For Command Mode
             if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                 // Maybe ask "I didn't catch that"? Or just go back to wake word?
                 // Let's go back to wake word to be safe and not annoying.
                 // notify error?
                 _voiceState.value = VoiceState.Error("Didn't catch that")
                 handler.postDelayed({ startWakeWordDetection() }, 2000)
             } else {
                 // Other errors
                 _voiceState.value = VoiceState.Error("Error: $error")
                 handler.postDelayed({ startWakeWordDetection() }, 2000)
             }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            resetInactivityTimer()
            
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase() ?: ""

            Log.d("VoiceManager", "Recognized ($mode): $text")

            if (mode == Mode.WAKE_WORD) {
                 // Strict checks
                 val wakeWords = listOf("hey tori", "hey tor", "hey tour", "hey thor", "hator", "hi tor")
                 if (wakeWords.any { text.contains(it) }) {
                     Log.d("VoiceManager", "Wake Word Triggered")
                     onWakeWordDetected?.invoke()
                 } else {
                     // Restart listening
                     startRecognition()
                 }
            } else {
                // Command Mode
                if (text.isNotBlank()) {
                    onCommandReceived?.invoke(text)
                } else {
                    // Empty? retry or go back to idle
                    startWakeWordDetection()
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Optional: aggressive wake word detection on partials for speed
             if (mode == Mode.WAKE_WORD) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.lowercase() ?: ""
                val wakeWords = listOf("hey tori", "hey tor", "hey tour", "hey thor")
                 if (wakeWords.any { text.contains(it) }) {
                     speechRecognizer?.stopListening() // Trigger results immediately
                 }
             }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
