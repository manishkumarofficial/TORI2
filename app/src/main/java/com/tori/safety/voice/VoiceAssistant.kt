package com.tori.safety.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Main Voice Assistant class - Tori
 * Integrates wake word detection, speech recognition, and Gemini AI
 */
class VoiceAssistant(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val wakeWordDetector = WakeWordDetector(context)
    private val speechRecognizer = ToriSpeechRecognizer(context)
    private val geminiProcessor = GeminiProcessor(context)
    private val textToSpeech = ToriTextToSpeech(context)
    private val contextManager = ConversationContextManager()
    
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    private val _response = MutableSharedFlow<VoiceResponse>()
    val response: SharedFlow<VoiceResponse> = _response.asSharedFlow()
    
    private var isInitialized = false
    private var isListening = false
    
    suspend fun initialize() {
        try {
            Log.d(TAG, "Initializing Tori Voice Assistant...")
            
            // Initialize all components
            wakeWordDetector.initialize()
            speechRecognizer.initialize()
            geminiProcessor.initialize()
            textToSpeech.initialize()
            
            // Set up wake word detection
            wakeWordDetector.wakeWordDetected
                .onEach { confidence ->
                    Log.d(TAG, "Wake word detected with confidence: $confidence")
                    onWakeWordDetected()
                }
                .launchIn(scope)
            
            // Set up speech recognition results
            speechRecognizer.speechResult
                .onEach { result ->
                    Log.d(TAG, "Speech recognized: ${result.text}")
                    onSpeechRecognized(result)
                }
                .launchIn(scope)
            
            isInitialized = true
            _voiceState.value = VoiceState.IDLE
            
            Log.d(TAG, "Tori Voice Assistant initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Voice Assistant", e)
            throw e
        }
    }
    
    fun startListening() {
        if (!isInitialized) {
            Log.w(TAG, "Voice Assistant not initialized")
            return
        }
        
        if (isListening) {
            Log.d(TAG, "Already listening")
            return
        }
        
        Log.d(TAG, "Starting wake word detection...")
        wakeWordDetector.startListening()
        isListening = true
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
    }
    
    fun stopListening() {
        Log.d(TAG, "Stopping voice assistant...")
        wakeWordDetector.stopListening()
        speechRecognizer.stopListening()
        isListening = false
        _voiceState.value = VoiceState.IDLE
    }
    
    private suspend fun onWakeWordDetected() {
        _voiceState.value = VoiceState.WAKE_WORD_DETECTED
        
        // Provide audio feedback
        textToSpeech.speak("Yes, I'm listening", priority = TTSPriority.HIGH)
        
        // Start listening for command
        delay(1500) // Wait for TTS to finish
        _voiceState.value = VoiceState.LISTENING_FOR_COMMAND
        speechRecognizer.startListening()
        
        // Emit response for UI
        _response.emit(VoiceResponse(
            type = ResponseType.WAKE_WORD_ACKNOWLEDGED,
            message = "Listening for your command...",
            shouldSpeak = false
        ))
    }
    
    private suspend fun onSpeechRecognized(result: SpeechResult) {
        if (result.text.isBlank()) {
            Log.w(TAG, "Empty speech result")
            handleError("I didn't hear anything. Could you try again?")
            return
        }
        
        Log.d(TAG, "Processing speech: ${result.text}")
        _voiceState.value = VoiceState.PROCESSING
        
        try {
            // Add to conversation context
            contextManager.addUserInput(result.text)
            
            // Process with Gemini AI
            val geminiResponse = geminiProcessor.processCommand(
                userInput = result.text,
                context = contextManager.getContext()
            )
            
            Log.d(TAG, "Gemini response: ${geminiResponse.message}")
            
            // Add AI response to context
            contextManager.addAssistantResponse(geminiResponse.message)
            
            // Set speaking state
            _voiceState.value = VoiceState.SPEAKING
            
            // Speak the response
            textToSpeech.speak(geminiResponse.message, priority = TTSPriority.NORMAL)
            
            // Emit response for UI
            _response.emit(VoiceResponse(
                type = ResponseType.COMMAND_RESPONSE,
                message = geminiResponse.message,
                shouldSpeak = true,
                data = geminiResponse.data
            ))
            
            // Wait for TTS to complete, then continue listening
            delay(3000) // Give time for TTS to complete
            
            // Continue listening for follow-up
            _voiceState.value = VoiceState.LISTENING_FOR_COMMAND
            speechRecognizer.startListening()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing speech", e)
            handleError("Sorry, I'm having trouble processing that. Could you try again?")
        }
    }
    
    private suspend fun handleError(message: String) {
        textToSpeech.speak(message, priority = TTSPriority.HIGH)
        _response.emit(VoiceResponse(
            type = ResponseType.ERROR,
            message = message,
            shouldSpeak = true
        ))
        
        // Return to listening for wake word
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
    }
    
    fun release() {
        Log.d(TAG, "Releasing Voice Assistant...")
        scope.cancel()
        wakeWordDetector.release()
        speechRecognizer.release()
        textToSpeech.release()
        isInitialized = false
    }
    
    companion object {
        private const val TAG = "VoiceAssistant"
    }
}

enum class VoiceState {
    IDLE,
    LISTENING_FOR_WAKE_WORD,
    WAKE_WORD_DETECTED,
    LISTENING_FOR_COMMAND,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class VoiceResponse(
    val type: ResponseType,
    val message: String,
    val shouldSpeak: Boolean = false,
    val data: Map<String, Any>? = null
)

enum class ResponseType {
    WAKE_WORD_ACKNOWLEDGED,
    COMMAND_RESPONSE,
    ERROR,
    SYSTEM_MESSAGE
}