package com.tori.safety.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Main Voice Assistant class - Tori
 * Integrates wake word detection, speech recognition, and Gemini AI
 *
 * ARCHITECTURE NOTE:
 * WakeWordDetector uses AudioRecord (raw mic) for energy-based detection.
 * ToriSpeechRecognizer uses Android SpeechRecognizer (also grabs the mic).
 * Both CANNOT run simultaneously — they fight for the microphone.
 *
 * Strategy:
 * - Hotword detection: Use ONLY SpeechRecognizer in hotword mode (long silence timeout,
 *   partial results checked against regex). WakeWordDetector is NOT started.
 * - Command mode: Cancel SpeechRecognizer hotword, start command mode with shorter timeout.
 * - After command processed: Return to hotword SpeechRecognizer.
 *
 * FIX NOTES:
 * - Added delay after cancel() to let mic hardware release before restarting
 * - Always reset isStartingCommand in finally blocks to prevent deadlocks
 * - Improved error handling with proper state transitions
 * - Added isProcessingCommand guard to prevent double-processing
 * - Added comprehensive logging for debugging on-device
 */
class VoiceAssistant(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // We do NOT use WakeWordDetector anymore — it creates a mic conflict
    // with SpeechRecognizer. Instead, hotword detection is done via
    // SpeechRecognizer partial results matched against a regex.
    private val wakeWordDetector = WakeWordDetector(context) // kept for reference, not started
    private val speechRecognizer = ToriSpeechRecognizer(context)
    private val geminiProcessor = GeminiProcessor(context)
    private val textToSpeech = ToriTextToSpeech(context)
    private val contextManager = ConversationContextManager()
    
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    private val _response = MutableSharedFlow<VoiceResponse>(extraBufferCapacity = 5)
    val response: SharedFlow<VoiceResponse> = _response.asSharedFlow()
    
    private var isInitialized = false
    private var isListening = false
    private var isInCommandMode = false
    private var isStartingCommand = false
    private var isProcessingCommand = false // NEW: prevents double-processing
    private var startRequested = false

    private val hotwordRegex = Regex("\\b(h(i|ey)|hai)\\s*(to(r|ri|ry|re)|tar)\\b", RegexOption.IGNORE_CASE)

    fun isActive(): Boolean = isListening
    
    suspend fun initialize() {
        try {
            Log.d(TAG, "Initializing Tori Voice Assistant...")
            
            // Initialize components — DO NOT initialize WakeWordDetector (mic conflict)
            speechRecognizer.initialize()
            geminiProcessor.initialize()
            textToSpeech.initialize()
            
            // Hotword detection via SpeechRecognizer partial results
            speechRecognizer.partialText
                .onEach { partial ->
                    if (isInCommandMode || isStartingCommand || isProcessingCommand) return@onEach
                    if (isHotword(partial)) {
                        Log.d(TAG, "Hotword detected from partial: $partial")
                        onWakeWordDetected()
                    }
                }
                .launchIn(scope)
            
            // Set up speech recognition results
            // CRITICAL: Only process results when we are actually in command mode.
            // Without this guard, hotword-mode results that leak to speechResult
            // would be processed as commands.
            speechRecognizer.speechResult
                .onEach { result ->
                    Log.d(TAG, "Speech result received: '${result.text}' (confidence: ${result.confidence})")
                    Log.d(TAG, "State: commandMode=$isInCommandMode, startingCommand=$isStartingCommand, processing=$isProcessingCommand")
                    if (!isInCommandMode && !isStartingCommand) {
                        Log.w(TAG, "Ignoring speech result — not in command mode")
                        return@onEach
                    }
                    onSpeechRecognized(result)
                }
                .launchIn(scope)
            
            // Set up speech recognition errors
            speechRecognizer.speechError
                .onEach { error ->
                    Log.e(TAG, "Speech recognition error: $error (commandMode=$isInCommandMode, startingCommand=$isStartingCommand)")
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
            Log.w(TAG, "Voice Assistant not initialized — deferring start")
            startRequested = true
            return
        }
        
        if (isListening) {
            Log.d(TAG, "Already listening")
            return
        }
        
        Log.d(TAG, "Starting hotword listening...")
        isInCommandMode = false
        isStartingCommand = false
        isProcessingCommand = false
        speechRecognizer.startHotwordListening()
        isListening = true
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
    }
    
    fun stopListening() {
        Log.d(TAG, "Stopping voice assistant...")
        speechRecognizer.cancel()
        isListening = false
        isInCommandMode = false
        isStartingCommand = false
        isProcessingCommand = false
        startRequested = false
        _voiceState.value = VoiceState.IDLE
    }

    fun startCommandListening() {

        if (!isInitialized) {
            Log.w(TAG, "Voice Assistant not initialized")
            return
        }

        if (isInCommandMode || isStartingCommand || isProcessingCommand) {
            Log.d(TAG, "Already in command/processing mode — ignoring duplicate trigger")
            return
        }

        if (!isListening) {
            isListening = true
        }

        scope.launch {
            isStartingCommand = true
            _voiceState.value = VoiceState.WAKE_WORD_DETECTED

            try {
                // Cancel hotword SILENTLY — suppress the ERROR_CLIENT that Android
                // fires when cancel() is called, preventing it from triggering
                // unwanted hotword restarts that interfere with command mode.
                speechRecognizer.cancelSilently()

                isInCommandMode = true
                isProcessingCommand = false

                // Provide audio feedback
                textToSpeech.speak("Yes, I'm listening", priority = TTSPriority.HIGH)

                // Wait for TTS to finish speaking before starting command listening
                // This prevents the mic from capturing TTS output as user speech
                waitForTTSCompletion(maxWaitMs = 3000)

                // Small extra delay to let audio hardware fully release
                delay(300)

                _voiceState.value = VoiceState.LISTENING_FOR_COMMAND
                Log.d(TAG, "Starting SpeechRecognizer in command mode (manual trigger)...")
                speechRecognizer.startCommandListening()

                _response.emit(
                    VoiceResponse(
                        type = ResponseType.WAKE_WORD_ACKNOWLEDGED,
                        message = "Listening for your command...",
                        shouldSpeak = false
                    )
                )
            } finally {
                isStartingCommand = false
            }
        }
    }
    
    private suspend fun onWakeWordDetected() {
        if (isStartingCommand || isInCommandMode || isProcessingCommand) {
            Log.d(TAG, "Ignoring wake word — already in command/processing mode")
            return
        }
        isStartingCommand = true
        _voiceState.value = VoiceState.WAKE_WORD_DETECTED

        try {
            // Cancel hotword SILENTLY before starting command capture
            speechRecognizer.cancelSilently()

            isInCommandMode = true

            // Provide audio feedback
            textToSpeech.speak("Hello Buddy, How can I help you?", priority = TTSPriority.HIGH)

            // Wait for TTS to finish before starting command listening
            waitForTTSCompletion(maxWaitMs = 4000)

            // Extra delay to let audio hardware release after TTS
            delay(300)

            _voiceState.value = VoiceState.LISTENING_FOR_COMMAND
            Log.d(TAG, "Starting SpeechRecognizer in command mode (wake word)...")
            speechRecognizer.startCommandListening()

            // Emit response for UI
            _response.emit(VoiceResponse(
                type = ResponseType.WAKE_WORD_ACKNOWLEDGED,
                message = "Listening for your command...",
                shouldSpeak = false
            ))
        } finally {
            // CRITICAL: Always reset isStartingCommand to prevent deadlocks
            isStartingCommand = false
        }
    }
    
    private suspend fun onSpeechRecognized(result: SpeechResult) {
        if (result.text.isBlank()) {
            Log.w(TAG, "Empty speech result")
            handleError("I didn't hear anything. Could you try again?")
            return
        }

        // Guard against double-processing
        if (isProcessingCommand) {
            Log.w(TAG, "Already processing a command — ignoring duplicate result: '${result.text}'")
            return
        }

        val userText = result.text.trim()
        
        Log.d(TAG, "Processing speech: '$userText'")
        isProcessingCommand = true
        _voiceState.value = VoiceState.PROCESSING

        try {
            // Fast local command handling before Gemini
            val localAction = parseLocalCommand(userText)
            if (localAction != null) {
                val localMessage = localAction["message"] as? String ?: "Okay"
                Log.d(TAG, "Local command matched: $localMessage")
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

                // Wait for TTS to finish before returning to hotword listening
                waitForTTSCompletion(maxWaitMs = 5000)

                returnToHotwordListening()
                return
            }

            // Add to conversation context
            contextManager.addUserInput(userText)

            // Process with Gemini AI
            Log.d(TAG, "Sending to Gemini: '$userText'")
            val geminiResponse = geminiProcessor.processCommand(
                userInput = userText,
                context = contextManager.getContext()
            )
            
            Log.d(TAG, "Gemini response: '${geminiResponse.message}'")
            
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

            // Wait for TTS to finish before returning to wake word listening
            waitForTTSCompletion(maxWaitMs = 15000)

            returnToHotwordListening()

        } catch (e: Exception) {
            Log.e(TAG, "Error processing speech", e)
            handleError("Sorry, I'm having trouble processing that. Could you try again?")
        }
    }

    /**
     * Cleanly returns to hotword listening mode, resetting all flags.
     */
    private fun returnToHotwordListening() {
        Log.d(TAG, "Returning to hotword listening mode")
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
        isInCommandMode = false
        isStartingCommand = false
        isProcessingCommand = false
        speechRecognizer.startHotwordListening()
    }
    
    private suspend fun handleError(message: String) {
        isProcessingCommand = false
        textToSpeech.speak(message, priority = TTSPriority.HIGH)
        
        _response.emit(VoiceResponse(
            type = ResponseType.ERROR,
            message = message,
            shouldSpeak = true
        ))
        
        // Wait for error TTS to finish
        waitForTTSCompletion(maxWaitMs = 5000)

        // Reset flags and return to listening for wake word
        isInCommandMode = false
        isStartingCommand = false
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
        speechRecognizer.startHotwordListening()
    }


    /**
     * Waits until TTS is no longer speaking, up to maxWaitMs.
     * Uses a simple reliable polling approach with a generous initial delay
     * to let the TTS engine actually start before checking isSpeaking().
     */
    private suspend fun waitForTTSCompletion(maxWaitMs: Long) {
        val startTime = System.currentTimeMillis()
        // Initial delay to let TTS engine start — isSpeaking() returns false
        // until the utterance actually begins playback
        delay(800)
        
        // Poll speakingState (backed by UtteranceProgressListener) + isSpeaking()
        while ((System.currentTimeMillis() - startTime) < maxWaitMs) {
            val stateFlowSpeaking = textToSpeech.speakingState.value
            val apiSpeaking = textToSpeech.isSpeaking()
            if (!stateFlowSpeaking && !apiSpeaking) {
                break
            }
            delay(150)
        }
        
        // Extra buffer to let audio hardware fully release speaker/mic
        delay(400)
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