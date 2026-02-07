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
    private var isInCommandMode = false
    private var isStartingCommand = false
    private var startRequested = false

    private val hotwordRegex = Regex("\\b(h(i|ey)|hai)\\s*(to(r|ri|ry|re)|tar)\\b", RegexOption.IGNORE_CASE)

    fun isActive(): Boolean = isListening
    
    suspend fun initialize() {
        try {
            Log.d(TAG, "Initializing Tori Voice Assistant...")
            
            // Initialize all components
            wakeWordDetector.initialize()
            speechRecognizer.initialize()
            geminiProcessor.initialize()
            textToSpeech.initialize()
            
            // Hotword detection using SpeechRecognizer partial results (reliable across devices)
            speechRecognizer.partialText
                .onEach { partial ->
                    if (isInCommandMode || isStartingCommand) return@onEach
                    if (isHotword(partial)) {
                        Log.d(TAG, "Hotword detected from partial: $partial")
                        onWakeWordDetected()
                    }
                }
                .launchIn(scope)
            
            // Set up speech recognition results
            speechRecognizer.speechResult
                .onEach { result ->
                    Log.d(TAG, "Speech recognized: ${result.text}")
                    onSpeechRecognized(result)
                }
                .launchIn(scope)
            
            // Set up speech recognition errors
            speechRecognizer.speechError
                .onEach { error ->
                    Log.e(TAG, "Speech recognition error: $error")
                    if (isInCommandMode || isStartingCommand) {
                        handleError(error)
                    } else {
                        // Hotword mode: don't speak errors; SpeechRecognizer will auto-restart.
                        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
                    }
                }
                .launchIn(scope)
            
            isInitialized = true
            _voiceState.value = VoiceState.IDLE

            if (startRequested) {
                startRequested = false
                startListening()
            }
            
            Log.d(TAG, "Tori Voice Assistant initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Voice Assistant", e)
            throw e
        }
    }
    
    fun startListening() {

        if (!isInitialized) {
            Log.w(TAG, "Voice Assistant not initialized")
            startRequested = true
            return
        }
        
        if (isListening) {
            Log.d(TAG, "Already listening")
            return
        }
        
        Log.d(TAG, "Starting hotword listening...")
        speechRecognizer.startHotwordListening()
        isListening = true
        isInCommandMode = false
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
    }
    
    fun stopListening() {
        Log.d(TAG, "Stopping voice assistant...")
        wakeWordDetector.stopListening()
        speechRecognizer.cancel()
        isListening = false
        isInCommandMode = false
        isStartingCommand = false
        startRequested = false
        _voiceState.value = VoiceState.IDLE
    }

    fun startCommandListening() {

        if (!isInitialized) {
            Log.w(TAG, "Voice Assistant not initialized")
            return
        }

        if (!isListening) {
            isListening = true
        }

        scope.launch {
            _voiceState.value = VoiceState.WAKE_WORD_DETECTED

            // Avoid mic conflicts: stop wake word audio capture before SpeechRecognizer starts
            wakeWordDetector.stopListening()

            speechRecognizer.cancel()

            isInCommandMode = true

            // Provide audio feedback
            textToSpeech.speak("Yes, I'm listening", priority = TTSPriority.HIGH)

            // Start listening for command
            delay(1200)
            _voiceState.value = VoiceState.LISTENING_FOR_COMMAND
            speechRecognizer.startCommandListening()

            _response.emit(
                VoiceResponse(
                    type = ResponseType.WAKE_WORD_ACKNOWLEDGED,
                    message = "Listening for your command...",
                    shouldSpeak = false
                )
            )
        }
    }
    
    private suspend fun onWakeWordDetected() {
        if (isStartingCommand || isInCommandMode) return
        isStartingCommand = true
        _voiceState.value = VoiceState.WAKE_WORD_DETECTED

        // Stop hotword loop before starting command capture
        speechRecognizer.cancel()

        isInCommandMode = true
        
        // Provide audio feedback
        textToSpeech.speak("Hello Buddy, How can i help you", priority = TTSPriority.HIGH)
        
        // Start listening for command
        delay(1500) // Wait for TTS to finish
        _voiceState.value = VoiceState.LISTENING_FOR_COMMAND
        speechRecognizer.startCommandListening()
        isStartingCommand = false

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

        val userText = result.text.trim()
        
        Log.d(TAG, "Processing speech: $userText")
        _voiceState.value = VoiceState.PROCESSING

        try {
            // Fast local command handling before Gemini
            val localAction = parseLocalCommand(userText)
            if (localAction != null) {
                val localMessage = localAction["message"] as? String ?: "Okay"
                _voiceState.value = VoiceState.SPEAKING
                textToSpeech.speak(localMessage, priority = TTSPriority.NORMAL)

                _response.emit(
                    VoiceResponse(
                        type = ResponseType.COMMAND_RESPONSE,
                        message = localMessage,
                        shouldSpeak = true,
                        data = localAction + mapOf("rawCommand" to userText)
                    )
                )

                _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
                isInCommandMode = false
                speechRecognizer.startHotwordListening()
                return
            }

            // Add to conversation context
            contextManager.addUserInput(userText)

            // Process with Gemini AI
            val geminiResponse = geminiProcessor.processCommand(
                userInput = userText,
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
                data = geminiResponse.data + mapOf("rawCommand" to userText)
            ))

            // Return to wake word listening after the command
            delay(1500)
            _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
            isInCommandMode = false
            speechRecognizer.startHotwordListening()

        } catch (e: Exception) {
            Log.e(TAG, "Error processing speech", e)
            handleError("Sorry, I'm having trouble processing that. Could you try again?")
        }
    }
    
    private suspend fun handleError(message: String) {
        isInCommandMode = false
        isStartingCommand = false
        textToSpeech.speak(message, priority = TTSPriority.HIGH)
        
        _response.emit(VoiceResponse(
            type = ResponseType.ERROR,
            message = message,
            shouldSpeak = true
        ))
        
        // Return to listening for wake word
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
        speechRecognizer.startHotwordListening()
    }

    private fun isHotword(text: String): Boolean {
        val normalized = text
            .lowercase()
            .replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return hotwordRegex.containsMatchIn(normalized)
    }

    private fun parseLocalCommand(text: String): Map<String, Any>? {
        val lower = text.lowercase()

        return when {
            lower.contains("settings") -> mapOf(
                "action" to "OPEN_SCREEN",
                "screen" to "SETTINGS",
                "message" to "Opening settings"
            )
            lower.contains("contacts") -> mapOf(
                "action" to "OPEN_SCREEN",
                "screen" to "CONTACTS",
                "message" to "Opening contacts"
            )
            lower.contains("trip log") || lower.contains("triplog") || lower.contains("history") -> mapOf(
                "action" to "OPEN_SCREEN",
                "screen" to "TRIP_LOG",
                "message" to "Opening trip log"
            )
            lower.contains("hud") -> mapOf(
                "action" to "OPEN_SCREEN",
                "screen" to "HUD",
                "message" to "Opening HUD mode"
            )
            lower.contains("start monitoring") || lower.contains("drive mode") || lower.contains("start drive") -> mapOf(
                "action" to "START_MONITORING",
                "message" to "Starting monitoring"
            )
            lower.contains("stop monitoring") || lower.contains("stop drive") -> mapOf(
                "action" to "STOP_MONITORING",
                "message" to "Stopping monitoring"
            )
            lower.contains("sos") || (lower.contains("emergency") && !lower.contains("not emergency")) -> mapOf(
                "action" to "SEND_SOS",
                "message" to "I can send an SOS. Please confirm."
            )
            lower.startsWith("navigate to ") -> {
                val dest = text.substringAfter("navigate to ").trim()
                mapOf(
                    "action" to "NAVIGATE",
                    "query" to dest,
                    "message" to "Starting navigation"
                )
            }
            lower.startsWith("take me to ") -> {
                val dest = text.substringAfter("take me to ").trim()
                mapOf(
                    "action" to "NAVIGATE",
                    "query" to dest,
                    "message" to "Starting navigation"
                )
            }
            else -> null
        }
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