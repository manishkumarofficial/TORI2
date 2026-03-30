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

/**
 * Speech recognizer for Tori voice assistant — COMMAND MODE ONLY.
 *
 * This recognizer is ONLY used for capturing user commands after activation.
 * It is NOT used for wake word / hotword detection (that uses WakeWordDetector).
 *
 * SpeechRecognizer plays a system beep when it starts, so it must ONLY be started
 * when the user has explicitly triggered voice input (button press or wake word).
 */
class ToriSpeechRecognizer(private val context: Context) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Retry tracking for empty results / transient errors
    private var commandRetryCount = 0
    private val maxCommandRetries = 2

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * When true, errors from onError() are suppressed.
     * Set before intentional cancel() calls to prevent ERROR_CLIENT
     * from being treated as a real error.
     */
    private var suppressErrors = false

    private val _speechResult = MutableSharedFlow<SpeechResult>(extraBufferCapacity = 5)
    val speechResult: SharedFlow<SpeechResult> = _speechResult.asSharedFlow()

    private val _partialText = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val partialText: SharedFlow<String> = _partialText.asSharedFlow()

    private val _speechError = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val speechError: SharedFlow<String> = _speechError.asSharedFlow()

    fun initialize() {
        Log.d(TAG, "Initializing speech recognizer...")
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw IllegalStateException("Speech recognition not available on this device")
        }
        createRecognizer()
        Log.d(TAG, "Speech recognizer initialized")
    }

    private fun createRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@ToriSpeechRecognizer)
        }
        Log.d(TAG, "SpeechRecognizer instance created")
    }

    /**
     * Cancel and suppress the resulting ERROR_CLIENT callback.
     * Use before mode transitions to prevent error side effects.
     */
    fun cancelSilently() {
        Log.d(TAG, "Silent cancel — suppressing errors")
        suppressErrors = true
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false
    }

    /**
     * Start listening for user commands.
     * This will play a system beep (normal SpeechRecognizer behavior).
     */
    fun startCommandListening() {
        suppressErrors = false
        commandRetryCount = 0

        Log.d(TAG, "Starting command listening...")

        // Cancel any prior session without error cascade
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false

        // Short delay for audio hardware to settle, then start
        mainHandler.postDelayed({
            suppressErrors = false
            startListeningInternal()
        }, 200)
    }

    private fun retryCommandListening() {
        commandRetryCount++
        Log.d(TAG, "Retrying command listening (attempt $commandRetryCount/$maxCommandRetries)")

        if (commandRetryCount >= 2) {
            // Full recreate on 2nd retry
            Log.d(TAG, "Full recreate on retry #$commandRetryCount")
            mainHandler.post {
                createRecognizer()
                mainHandler.postDelayed({ startListeningInternal() }, 400)
            }
        } else {
            // Simple restart
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            isListening = false
            mainHandler.postDelayed({ startListeningInternal() }, 300)
        }
    }

    private fun startListeningInternal() {
        if (isListening) {
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
            isListening = false
        }

        Log.d(TAG, "Starting speech recognition (retry=$commandRetryCount)...")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Use standard English — most reliable across all devices
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(TAG, "SpeechRecognizer.startListening() called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            _speechError.tryEmit("Failed to start listening: ${e.message}")
        }
    }

    fun cancel() {
        Log.d(TAG, "Cancel speech recognition...")
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        isListening = false
    }

    // ---- RecognitionListener callbacks ----

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "✓ Ready for speech — speak now")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "✓ Beginning of speech detected")
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "✓ End of speech detected")
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
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Unknown error: $error"
        }

        if (suppressErrors) {
            Log.d(TAG, "Suppressing error during cancel: $errorMessage (code=$error)")
            return
        }

        Log.e(TAG, "Speech error: $errorMessage (code=$error, retry=$commandRetryCount)")

        // Recreate on BUSY or CLIENT errors
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
            mainHandler.post { createRecognizer() }
        }

        // Retry transient errors
        val isRetryable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_SERVER ||
                error == SpeechRecognizer.ERROR_AUDIO

        if (isRetryable && commandRetryCount < maxCommandRetries) {
            retryCommandListening()
        } else {
            _speechError.tryEmit(errorMessage)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            val conf = confidence?.get(0) ?: 0.5f
            Log.d(TAG, "✓ Speech recognized: '$text' (confidence: $conf)")
            commandRetryCount = 0

            _speechResult.tryEmit(SpeechResult(text = text, confidence = conf))
        } else {
            Log.w(TAG, "Empty speech results (retry=$commandRetryCount)")

            if (commandRetryCount < maxCommandRetries) {
                retryCommandListening()
            } else {
                _speechError.tryEmit("I couldn't hear you clearly. Please try again.")
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            Log.d(TAG, "Partial: ${matches[0]}")
            _partialText.tryEmit(matches[0])
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun release() {
        Log.d(TAG, "Releasing speech recognizer...")
        cancel()
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