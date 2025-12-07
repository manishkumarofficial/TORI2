package com.tori.safety.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class VoiceManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isWakeWordMode = true // If true, we are just listening for "Hey Tori"

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState

    var onCommandRecognized: ((String) -> Unit)? = null

    sealed class VoiceState {
        object Idle : VoiceState()
        object Listening : VoiceState()
        object Processing : VoiceState()
        object Speaking : VoiceState()
        data class Error(val message: String) : VoiceState()
    }

    fun startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        }

        if (!isListening) {
            startRecognition()
        }
    }

    private fun startRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // If in wake word mode, we want to be continuous and not beep
            // But standard SpeechRecognizer beeps. We'll manage this by restarting.
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            if (!isWakeWordMode) {
                _voiceState.value = VoiceState.Listening
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error starting recognition", e)
            _voiceState.value = VoiceState.Error(e.message ?: "Unknown error")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        _voiceState.value = VoiceState.Idle
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {
            if (!isWakeWordMode) {
                _voiceState.value = VoiceState.Listening
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Can be used for visualization
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (!isWakeWordMode) {
                _voiceState.value = VoiceState.Processing
            }
        }

        override fun onError(error: Int) {
            isListening = false
            // Restart listening if it was a timeout or no match, to keep "Wake Word" active
            if (isWakeWordMode || error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                startRecognition()
            } else {
                _voiceState.value = VoiceState.Error("Error code: $error")
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase() ?: ""

            Log.d("VoiceManager", "Recognized: $text")

            if (isWakeWordMode) {
                if (text.contains("hey tori") || text.contains("tori")) {
                    Log.d("VoiceManager", "Wake word detected!")
                    isWakeWordMode = false
                    _voiceState.value = VoiceState.Listening
                    // Play a sound or give feedback?
                    // Immediately start listening for the actual command
                    startRecognition()
                } else {
                    // Not the wake word, keep listening
                    startRecognition()
                }
            } else {
                // We have a command
                onCommandRecognized?.invoke(text)
                // Go back to wake word mode after processing? 
                // Or wait for CommandProcessor to tell us when to resume?
                // For now, let's go back to Idle/WakeWord after a short delay or manual trigger
                // But usually the CommandProcessor will handle the flow.
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun setWakeWordMode(enabled: Boolean) {
        isWakeWordMode = enabled
        if (enabled) {
            _voiceState.value = VoiceState.Idle
        }
    }
}
