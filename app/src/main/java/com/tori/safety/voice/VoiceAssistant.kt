package com.tori.safety.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Main Voice Assistant — Tori
 *
 * ARCHITECTURE (v4):
 *
 * ┌──────────────┐  wake word   ┌──────────────────┐  spoken   ┌─────────┐
 * │ WakeWordDet. │ ──────────→  │ SpeechRecognizer │ ───────→  │ Gemini  │
 * │ (AudioRecord)│              │ (command mode)   │           │   AI    │
 * │ silent, no   │              │ plays beep       │           │         │
 * │ beeps        │              │ on start         │           │         │
 * └──────────────┘              └──────────────────┘           └─────────┘
 *       ↑                                                          │
 *       └──────────────── after response TTSed ←───────────────────┘
 *
 * STATES:
 *   IDLE → WAKE_WORD_LISTENING → WAKE_WORD_DETECTED → LISTENING_FOR_COMMAND
 *        → PROCESSING → SPEAKING → WAKE_WORD_LISTENING
 *
 * KEY RULES:
 *   1. App launch: Initialize + start WakeWordDetector (silent, no beeps)
 *   2. WakeWordDetector fires: Stop it → TTS → SpeechRecognizer command mode
 *   3. Button press: Stop WakeWordDetector → TTS → SpeechRecognizer command mode
 *   4. SpeechRecognizer is NEVER used for background/hotword detection
 *   5. After processing: TTS response → restart WakeWordDetector
 */
class VoiceAssistant(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Components
    private val wakeWordDetector = WakeWordDetector(context)
    private val speechRecognizer = ToriSpeechRecognizer(context)
    private val geminiProcessor = GeminiProcessor(context)
    private val textToSpeech = ToriTextToSpeech(context)
    private val contextManager = ConversationContextManager()

    // State
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _response = MutableSharedFlow<VoiceResponse>(extraBufferCapacity = 5)
    val response: SharedFlow<VoiceResponse> = _response.asSharedFlow()

    private var isInitialized = false
    private var isInCommandMode = false
    private var isProcessingCommand = false

    fun isActive(): Boolean = _voiceState.value != VoiceState.IDLE

    // ──────────────────────────────────────────────────────────────
    // INITIALIZATION
    // ──────────────────────────────────────────────────────────────

    suspend fun initialize() {
        try {
            Log.d(TAG, "Initializing Tori Voice Assistant...")

            // Initialize components
            speechRecognizer.initialize()
            geminiProcessor.initialize()
            textToSpeech.initialize()
            wakeWordDetector.initialize()

            // Subscribe to speech results (only fires in command mode)
            speechRecognizer.speechResult
                .onEach { result ->
                    Log.d(TAG, "Speech result: '${result.text}' (confidence: ${result.confidence})")
                    onSpeechRecognized(result)
                }
                .launchIn(scope)

            // Subscribe to speech errors
            speechRecognizer.speechError
                .onEach { error ->
                    Log.e(TAG, "Speech error: $error")
                    if (isInCommandMode) {
                        handleError("I didn't catch that. Tap the Tori button to try again.")
                    }
                }
                .launchIn(scope)

            // Subscribe to wake word detections (from WakeWordDetector/AudioRecord — silent)
            wakeWordDetector.wakeWordDetected
                .onEach { confidence ->
                    Log.d(TAG, "Wake word detected! (confidence: $confidence)")
                    if (!isInCommandMode && !isProcessingCommand) {
                        onWakeWordDetected()
                    }
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

    // ──────────────────────────────────────────────────────────────
    // WAKE WORD DETECTION (silent background listening)
    // ──────────────────────────────────────────────────────────────

    /**
     * Start silent wake word detection using AudioRecord.
     * No beeps, no visible indication to user.
     * This is the default background state.
     */
    fun startWakeWordDetection() {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized — cannot start wake word detection")
            return
        }

        if (isInCommandMode || isProcessingCommand) {
            Log.d(TAG, "In command/processing mode — skipping wake word start")
            return
        }

        Log.d(TAG, "Starting silent wake word detection (AudioRecord)...")
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
        wakeWordDetector.startListening()
    }

    /**
     * Stop wake word detection.
     */
    fun stopWakeWordDetection() {
        Log.d(TAG, "Stopping wake word detection...")
        wakeWordDetector.stopListening()
    }

    // ──────────────────────────────────────────────────────────────
    // COMMAND MODE (triggered by wake word or button press)
    // ──────────────────────────────────────────────────────────────

    /**
     * Called when wake word is detected by WakeWordDetector.
     * Stops wake word detection, provides audio feedback, starts command listening.
     */
    private suspend fun onWakeWordDetected() {
        if (isInCommandMode || isProcessingCommand) {
            Log.d(TAG, "Ignoring wake word — already in command/processing mode")
            return
        }

        Log.d(TAG, "Wake word activated — entering command mode")
        isInCommandMode = true
        _voiceState.value = VoiceState.WAKE_WORD_DETECTED

        // Stop wake word detection (release AudioRecord/mic)
        wakeWordDetector.stopListening()

        // Audio feedback
        textToSpeech.speak("Hello Buddy, How can I help you?", priority = TTSPriority.HIGH)
        waitForTTSCompletion(maxWaitMs = 4000)
        delay(300)

        // Start command listening (SpeechRecognizer — will play system beep)
        _voiceState.value = VoiceState.LISTENING_FOR_COMMAND
        Log.d(TAG, "Starting SpeechRecognizer for command...")
        speechRecognizer.startCommandListening()

        _response.emit(VoiceResponse(
            type = ResponseType.WAKE_WORD_ACKNOWLEDGED,
            message = "Listening for your command...",
            shouldSpeak = false
        ))
    }

    /**
     * Called when user manually triggers voice assistant (button press).
     * Stops wake word detection, provides audio feedback, starts command listening.
     */
    fun startCommandListening() {
        if (!isInitialized) {
            Log.w(TAG, "Voice Assistant not initialized")
            return
        }

        if (isInCommandMode || isProcessingCommand) {
            Log.d(TAG, "Already in command/processing mode — ignoring")
            return
        }

        scope.launch {
            Log.d(TAG, "Manual trigger — entering command mode")
            isInCommandMode = true
            _voiceState.value = VoiceState.WAKE_WORD_DETECTED

            // Stop wake word detection (release AudioRecord/mic)
            wakeWordDetector.stopListening()

            // Audio feedback
            textToSpeech.speak("Yes, I'm listening", priority = TTSPriority.HIGH)
            waitForTTSCompletion(maxWaitMs = 3000)
            delay(300)

            // Start command listening (SpeechRecognizer — will play system beep)
            _voiceState.value = VoiceState.LISTENING_FOR_COMMAND
            Log.d(TAG, "Starting SpeechRecognizer for command (manual trigger)...")
            speechRecognizer.startCommandListening()

            _response.emit(VoiceResponse(
                type = ResponseType.WAKE_WORD_ACKNOWLEDGED,
                message = "Listening for your command...",
                shouldSpeak = false
            ))
        }
    }

    // ──────────────────────────────────────────────────────────────
    // SPEECH PROCESSING
    // ──────────────────────────────────────────────────────────────

    private suspend fun onSpeechRecognized(result: SpeechResult) {
        if (result.text.isBlank()) {
            Log.w(TAG, "Empty speech result")
            handleError("I didn't hear anything. Tap the Tori button to try again.")
            return
        }

        if (isProcessingCommand) {
            Log.w(TAG, "Already processing — ignoring: '${result.text}'")
            return
        }

        val userText = result.text.trim()
        Log.d(TAG, "Processing speech: '$userText'")
        isProcessingCommand = true
        _voiceState.value = VoiceState.PROCESSING

        try {
            // Fast local command handling
            val localAction = parseLocalCommand(userText)
            if (localAction != null) {
                val localMessage = localAction["message"] as? String ?: "Okay"
                Log.d(TAG, "Local command matched: $localMessage")
                _voiceState.value = VoiceState.SPEAKING
                textToSpeech.speak(localMessage, priority = TTSPriority.NORMAL)

                _response.emit(VoiceResponse(
                    type = ResponseType.COMMAND_RESPONSE,
                    message = localMessage,
                    shouldSpeak = true,
                    data = localAction + mapOf("rawCommand" to userText)
                ))

                waitForTTSCompletion(maxWaitMs = 5000)
                returnToIdle()
                return
            }

            // Process with Gemini AI
            contextManager.addUserInput(userText)
            Log.d(TAG, "Sending to Gemini: '$userText'")
            val geminiResponse = geminiProcessor.processCommand(
                userInput = userText,
                context = contextManager.getContext()
            )
            Log.d(TAG, "Gemini response: '${geminiResponse.message}'")
            contextManager.addAssistantResponse(geminiResponse.message)

            // Speak the response
            _voiceState.value = VoiceState.SPEAKING
            textToSpeech.speak(geminiResponse.message, priority = TTSPriority.NORMAL)

            _response.emit(VoiceResponse(
                type = ResponseType.COMMAND_RESPONSE,
                message = geminiResponse.message,
                shouldSpeak = true,
                data = geminiResponse.data + mapOf("rawCommand" to userText)
            ))

            waitForTTSCompletion(maxWaitMs = 15000)
            returnToIdle()

        } catch (e: Exception) {
            Log.e(TAG, "Error processing speech", e)
            handleError("Sorry, I'm having trouble processing that. Please try again.")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // STATE MANAGEMENT
    // ──────────────────────────────────────────────────────────────

    /**
     * Return to idle state and restart silent wake word detection.
     */
    private fun returnToIdle() {
        Log.d(TAG, "Returning to idle — restarting wake word detection")
        speechRecognizer.cancelSilently()
        isInCommandMode = false
        isProcessingCommand = false
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
        wakeWordDetector.startListening()
    }

    private suspend fun handleError(message: String) {
        isProcessingCommand = false
        isInCommandMode = false

        _voiceState.value = VoiceState.ERROR
        textToSpeech.speak(message, priority = TTSPriority.HIGH)

        _response.emit(VoiceResponse(
            type = ResponseType.ERROR,
            message = message,
            shouldSpeak = true
        ))

        waitForTTSCompletion(maxWaitMs = 5000)

        // Return to silent wake word detection
        _voiceState.value = VoiceState.LISTENING_FOR_WAKE_WORD
        speechRecognizer.cancelSilently()
        wakeWordDetector.startListening()
    }

    /**
     * Stop everything — called when activity stops.
     */
    fun stopAll() {
        Log.d(TAG, "Stopping all voice systems...")
        speechRecognizer.cancelSilently()
        wakeWordDetector.stopListening()
        isInCommandMode = false
        isProcessingCommand = false
        _voiceState.value = VoiceState.IDLE
    }

    // ──────────────────────────────────────────────────────────────
    // UTILITIES
    // ──────────────────────────────────────────────────────────────

    /**
     * Waits until TTS is no longer speaking, up to maxWaitMs.
     */
    private suspend fun waitForTTSCompletion(maxWaitMs: Long) {
        val startTime = System.currentTimeMillis()
        // Initial delay to let TTS engine start playing
        delay(800)

        // Poll until TTS finishes
        while ((System.currentTimeMillis() - startTime) < maxWaitMs) {
            val stateFlowSpeaking = textToSpeech.speakingState.value
            val apiSpeaking = textToSpeech.isSpeaking()
            if (!stateFlowSpeaking && !apiSpeaking) break
            delay(150)
        }

        // Buffer for audio hardware to release
        delay(400)
    }

    private fun parseLocalCommand(text: String): Map<String, Any>? {
        val lower = text.lowercase()

        return when {
            lower.contains("settings") -> mapOf(
                "action" to "OPEN_SCREEN", "screen" to "SETTINGS",
                "message" to "Opening settings"
            )
            lower.contains("contacts") -> mapOf(
                "action" to "OPEN_SCREEN", "screen" to "CONTACTS",
                "message" to "Opening contacts"
            )
            lower.contains("trip log") || lower.contains("triplog") || lower.contains("history") -> mapOf(
                "action" to "OPEN_SCREEN", "screen" to "TRIP_LOG",
                "message" to "Opening trip log"
            )
            lower.contains("hud") -> mapOf(
                "action" to "OPEN_SCREEN", "screen" to "HUD",
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
                mapOf("action" to "NAVIGATE", "query" to dest, "message" to "Starting navigation")
            }
            lower.startsWith("take me to ") -> {
                val dest = text.substringAfter("take me to ").trim()
                mapOf("action" to "NAVIGATE", "query" to dest, "message" to "Starting navigation")
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