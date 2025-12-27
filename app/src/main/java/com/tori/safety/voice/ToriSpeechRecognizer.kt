package com.tori.safety.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Speech recognizer for Tori voice assistant
 * Uses Android's built-in speech recognition
 */
class ToriSpeechRecognizer(private val context: Context) : RecognitionListener {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    private val _speechResult = MutableSharedFlow<SpeechResult>()
    val speechResult: SharedFlow<SpeechResult> = _speechResult.asSharedFlow()
    
    private val _speechError = MutableSharedFlow<String>()
    val speechError: SharedFlow<String> = _speechError.asSharedFlow()
    
    fun initialize() {
        Log.d(TAG, "Initializing speech recognizer...")
        
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw IllegalStateException("Speech recognition not available on this device")
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@ToriSpeechRecognizer)
        }
        
        Log.d(TAG, "Speech recognizer initialized")
    }
    
    fun startListening() {
        if (isListening) {
            Log.d(TAG, "Already listening")
            return
        }
        
        Log.d(TAG, "Starting speech recognition...")
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            _speechError.tryEmit("Failed to start listening: ${e.message}")
        }
    }
    
    fun stopListening() {
        if (!isListening) return
        
        Log.d(TAG, "Stopping speech recognition...")
        speechRecognizer?.stopListening()
        isListening = false
    }
    
    // RecognitionListener implementation
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Ready for speech")
    }
    
    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Beginning of speech detected")
    }
    
    override fun onRmsChanged(rmsdB: Float) {
        // Audio level changed - could be used for UI feedback
    }
    
    override fun onBufferReceived(buffer: ByteArray?) {
        // Audio buffer received
    }
    
    override fun onEndOfSpeech() {
        Log.d(TAG, "End of speech detected")
        isListening = false
    }
    
    override fun onError(error: Int) {
        isListening = false
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error: $error"
        }
        
        Log.e(TAG, "Speech recognition error: $errorMessage")
        _speechError.tryEmit(errorMessage)
    }
    
    override fun onResults(results: Bundle?) {
        isListening = false
        
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        
        if (!matches.isNullOrEmpty()) {
            val recognizedText = matches[0]
            val confidenceScore = confidence?.get(0) ?: 0.5f
            
            Log.d(TAG, "Speech recognized: '$recognizedText' (confidence: $confidenceScore)")
            
            _speechResult.tryEmit(SpeechResult(
                text = recognizedText,
                confidence = confidenceScore
            ))
        } else {
            Log.w(TAG, "No speech results")
            _speechError.tryEmit("No speech detected")
        }
    }
    
    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            Log.d(TAG, "Partial result: ${matches[0]}")
        }
    }
    
    override fun onEvent(eventType: Int, params: Bundle?) {
        // Handle speech recognition events
    }
    
    fun release() {
        Log.d(TAG, "Releasing speech recognizer...")
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    companion object {
        private const val TAG = "ToriSpeechRecognizer"
    }
}

data class SpeechResult(
    val text: String,
    val confidence: Float
)