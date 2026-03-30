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
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Speech recognizer for Tori voice assistant
 * Uses Android's built-in speech recognition
 *
 * FIX v4 NOTES:
 * - Added suppressErrors flag to ignore ERROR_CLIENT from intentional cancel() calls.
 *   On Android, calling SpeechRecognizer.cancel() triggers onError(ERROR_CLIENT),
 *   which was being mishandled as a real error and causing hotword restarts
 *   that interfered with the command mode transition.
 * - Added cancelSilently() method for use before mode transitions.
 * - Used named Runnable for hotword restart so it can be cancelled during mode switches.
 * - Explicit "en-IN" locale to fix Hindi-locale devices returning empty results.
 * - Retry logic for both onError AND empty onResults in command mode.
 */
class ToriSpeechRecognizer(private val context: Context) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var mode: Mode = Mode.COMMAND

    // Retry tracking for command mode (covers both onError AND empty onResults)
    private var commandRetryCount = 0
    private val maxCommandRetries = 3

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * When true, errors from onError() are suppressed.
     * This is set before intentional cancel() calls to prevent ERROR_CLIENT
     * from being treated as a real error and triggering unwanted restarts.
     */
    private var suppressErrors = false

    private val _speechResult = MutableSharedFlow<SpeechResult>(extraBufferCapacity = 5)
    val speechResult: SharedFlow<SpeechResult> = _speechResult.asSharedFlow()

    private val _partialText = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val partialText: SharedFlow<String> = _partialText.asSharedFlow()

    private val _speechError = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val speechError: SharedFlow<String> = _speechError.asSharedFlow()

    /**
     * Named runnable for hotword restart so we can cancel it
     * when switching to command mode.
     */
    private val hotwordRestartRunnable = Runnable {
        if (mode == Mode.HOTWORD && !isListening && !suppressErrors) {
            Log.d(TAG, "Restarting hotword listening...")
            startHotwordListening()
        } else {
            Log.d(TAG, "Skipping hotword restart (mode=$mode, listening=$isListening, suppressed=$suppressErrors)")
        }
    }

    fun initialize() {
        Log.d(TAG, "Initializing speech recognizer...")

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw IllegalStateException("Speech recognition not available on this device")
        }

        createRecognizer()
        Log.d(TAG, "Speech recognizer initialized")
    }

    /**
     * Creates (or recreates) the underlying SpeechRecognizer instance.
     * Must be called on the main thread.
     */
    private fun createRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@ToriSpeechRecognizer)
        }
        Log.d(TAG, "SpeechRecognizer instance created/recreated")
    }

    fun startHotwordListening() {
        mode = Mode.HOTWORD
        commandRetryCount = 0
        suppressErrors = false
        startListeningInternal(
            partialResults = true,
            completeSilenceMs = 10_000,
            possiblyCompleteSilenceMs = 10_000
        )
    }

    /**
     * Cancel current listening silently — suppresses the ERROR_CLIENT
     * that Android fires when SpeechRecognizer.cancel() is called.
     *
     * Use this before mode transitions (hotword → command) to prevent
     * the error from triggering unwanted hotword restarts.
     */
    fun cancelSilently() {
        Log.d(TAG, "Silent cancel — suppressing errors from this cancel")
        suppressErrors = true
        // Remove any pending hotword restart that might interfere
        mainHandler.removeCallbacks(hotwordRestartRunnable)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false
    }

    /**
     * Start command listening.
     * Cancel + delay + start. Suppress errors from the cancel.
     * Recreate only used as fallback on retry #2+.
     */
    fun startCommandListening() {
        // Suppress errors from any pending callbacks or the cancel below
        suppressErrors = true
        mode = Mode.COMMAND
        commandRetryCount = 0

        // Remove any pending hotword restart callbacks
        mainHandler.removeCallbacks(hotwordRestartRunnable)

        Log.d(TAG, "Starting command listening (cancel + restart)")

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false

        // Delay to let mic/audio hardware settle, then start
        mainHandler.postDelayed({
            suppressErrors = false  // Accept errors from the NEW session
            Log.d(TAG, "Post-cancel delay complete, starting command recognition")
            startListeningInternal(
                partialResults = true,
                completeSilenceMs = 8000,
                possiblyCompleteSilenceMs = 8000
            )
        }, 350)
    }

    /**
     * Retry command listening with optional recognizer recreate.
     * First retry: simple restart. Second+ retry: full recreate.
     */
    private fun retryCommandListening() {
        commandRetryCount++
        Log.d(TAG, "Retrying command listening (attempt $commandRetryCount/$maxCommandRetries)")

        // Remove any pending callbacks
        mainHandler.removeCallbacks(hotwordRestartRunnable)

        if (commandRetryCount >= 2) {
            // On second+ retry, do a full recreate
            Log.d(TAG, "Full recreate on retry #$commandRetryCount")
            mainHandler.post {
                createRecognizer()
                mainHandler.postDelayed({
                    startListeningInternal(
                        partialResults = true,
                        completeSilenceMs = 8000,
                        possiblyCompleteSilenceMs = 8000
                    )
                }, 400)
            }
        } else {
            // First retry: just cancel and restart (lightweight)
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            isListening = false

            mainHandler.postDelayed({
                startListeningInternal(
                    partialResults = true,
                    completeSilenceMs = 8000,
                    possiblyCompleteSilenceMs = 8000
                )
            }, 400)
        }
    }

    private fun startListeningInternal(
        partialResults: Boolean,
        completeSilenceMs: Int,
        possiblyCompleteSilenceMs: Int
    ) {
        // If already listening, cancel first and then restart
        if (isListening) {
            Log.d(TAG, "Already listening, cancelling before restart")
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            isListening = false
        }

        Log.d(TAG, "Starting speech recognition (mode=$mode, retry=$commandRetryCount)...")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            // Use explicit English locale for speech recognition.
            // On Indian devices, Locale.getDefault() may return Hindi,
            // causing the recognizer to fail to match English speech.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en")

            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possiblyCompleteSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000)

            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(TAG, "SpeechRecognizer.startListening() called successfully (mode=$mode)")
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

    fun cancel() {
        Log.d(TAG, "Cancel speech recognition...")
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        isListening = false
    }

    fun reset() {
        Log.d(TAG, "Resetting speech recognizer...")
        cancel()
        mainHandler.post {
            createRecognizer()
        }
    }

    // RecognitionListener implementation
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Ready for speech (mode=$mode)")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Beginning of speech detected (mode=$mode)")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Audio level changed
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Audio buffer received
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "End of speech detected (mode=$mode)")
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

        // CRITICAL FIX: Suppress errors during intentional cancellations.
        // Android fires ERROR_CLIENT when cancel() is called, and this was
        // being mishandled as a real error, causing hotword restarts that
        // interfered with command mode transitions.
        if (suppressErrors) {
            Log.d(TAG, "Suppressing error during mode transition: $errorMessage (code=$error)")
            return
        }

        Log.e(TAG, "Speech recognition error: $errorMessage (code=$error, mode=$mode, retry=$commandRetryCount)")

        // For BUSY or CLIENT errors, recreate the recognizer
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
            reset()
        }

        if (mode == Mode.HOTWORD) {
            // Hotword mode: silently restart
            scheduleHotwordRestart()
        } else {
            // Command mode: retry for transient errors before giving up
            val isRetryableError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_SERVER ||
                    error == SpeechRecognizer.ERROR_AUDIO

            if (isRetryableError && commandRetryCount < maxCommandRetries) {
                retryCommandListening()
            } else {
                // Exhausted retries or non-retryable error — emit to VoiceAssistant
                Log.d(TAG, "Command mode error not retryable or retries exhausted, emitting error")
                _speechError.tryEmit(errorMessage)
            }
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        if (!matches.isNullOrEmpty()) {
            val recognizedText = matches[0]
            val confidenceScore = confidence?.get(0) ?: 0.5f

            Log.d(TAG, "Speech recognized (mode=$mode): '$recognizedText' (confidence: $confidenceScore)")

            // Reset retry counter on successful recognition
            commandRetryCount = 0

            if (mode == Mode.HOTWORD) {
                _partialText.tryEmit(recognizedText)
                scheduleHotwordRestart()
                return
            }

            // COMMAND mode — emit the result for VoiceAssistant to process
            val emitted = _speechResult.tryEmit(SpeechResult(
                text = recognizedText,
                confidence = confidenceScore
            ))
            Log.d(TAG, "Command result emitted to flow: $emitted (text='$recognizedText')")

            if (!emitted) {
                Log.e(TAG, "CRITICAL: Failed to emit speech result — flow buffer full!")
            }
        } else {
            Log.w(TAG, "No speech results (mode=$mode, retry=$commandRetryCount)")

            if (mode == Mode.HOTWORD) {
                scheduleHotwordRestart()
            } else {
                // Retry for empty results in command mode
                if (commandRetryCount < maxCommandRetries) {
                    Log.d(TAG, "Empty results — retrying command listening")
                    retryCommandListening()
                } else {
                    Log.d(TAG, "Empty results — retries exhausted, emitting error")
                    _speechError.tryEmit("I couldn't hear you clearly. Please try again.")
                }
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            Log.d(TAG, "Partial result (mode=$mode): ${matches[0]}")
            _partialText.tryEmit(matches[0])
        }
    }

    private fun scheduleHotwordRestart() {
        mainHandler.removeCallbacks(hotwordRestartRunnable)
        mainHandler.postDelayed(hotwordRestartRunnable, 350)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Handle speech recognition events
    }

    fun release() {
        Log.d(TAG, "Releasing speech recognizer...")
        mainHandler.removeCallbacks(hotwordRestartRunnable)
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    companion object {
        private const val TAG = "ToriSpeechRecognizer"
    }

    enum class Mode {
        HOTWORD,
        COMMAND
    }
}

data class SpeechResult(
    val text: String,
    val confidence: Float
)