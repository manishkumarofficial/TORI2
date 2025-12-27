package com.tori.safety.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Text-to-Speech engine for Tori voice assistant
 */
class ToriTextToSpeech(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    private val _speakingState = MutableStateFlow(false)
    val speakingState: StateFlow<Boolean> = _speakingState.asStateFlow()
    
    private val _speechComplete = MutableSharedFlow<String>()
    val speechComplete: SharedFlow<String> = _speechComplete.asSharedFlow()
    
    suspend fun initialize() = withContext(Dispatchers.Main) {
        Log.d(TAG, "Initializing Text-to-Speech...")
        
        val deferred = CompletableDeferred<Unit>()
        
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupTTS()
                isInitialized = true
                Log.d(TAG, "Text-to-Speech initialized successfully")
                deferred.complete(Unit)
            } else {
                Log.e(TAG, "Text-to-Speech initialization failed")
                deferred.completeExceptionally(Exception("TTS initialization failed"))
            }
        }
        
        deferred.await()
    }
    
    private fun setupTTS() {
        tts?.apply {
            // Set language to English (US)
            val result = setLanguage(Locale.US)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language not supported, using default")
            }
            
            // Configure speech parameters for Tori's personality
            setSpeechRate(0.9f) // Slightly slower for clarity
            setPitch(1.0f) // Normal pitch
            
            // Set up utterance progress listener
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Started speaking: $utteranceId")
                    _speakingState.value = true
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Finished speaking: $utteranceId")
                    _speakingState.value = false
                    utteranceId?.let { _speechComplete.tryEmit(it) }
                }
                
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error for utterance: $utteranceId")
                    _speakingState.value = false
                    utteranceId?.let { _speechComplete.tryEmit(it) }
                }
            })
        }
    }
    
    fun speak(text: String, priority: TTSPriority = TTSPriority.NORMAL) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized")
            return
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Empty text to speak")
            return
        }
        
        Log.d(TAG, "Speaking: $text")
        
        val queueMode = when (priority) {
            TTSPriority.HIGH -> TextToSpeech.QUEUE_FLUSH // Interrupt current speech
            TTSPriority.NORMAL -> TextToSpeech.QUEUE_ADD // Add to queue
        }
        
        val utteranceId = "tori_${System.currentTimeMillis()}"
        
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        
        tts?.speak(text, queueMode, params, utteranceId)
    }
    
    fun stop() {
        Log.d(TAG, "Stopping TTS")
        tts?.stop()
        _speakingState.value = false
    }
    
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }
    
    fun release() {
        Log.d(TAG, "Releasing Text-to-Speech...")
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
    
    companion object {
        private const val TAG = "ToriTextToSpeech"
    }
}

enum class TTSPriority {
    HIGH,    // Interrupt current speech
    NORMAL   // Add to queue
}