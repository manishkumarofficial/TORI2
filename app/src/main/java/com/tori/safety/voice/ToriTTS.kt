package com.tori.safety.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import android.os.Bundle

class ToriTTS(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingMessages = mutableListOf<String>()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("ToriTTS", "Language not supported")
            } else {
                isInitialized = true
                // Speak any pending messages
                pendingMessages.forEach { speak(it) }
                pendingMessages.clear()
            }
        } else {
            Log.e("ToriTTS", "Initialization failed")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (isInitialized) {
            val params = Bundle()
            val utteranceId = "ID_" + System.currentTimeMillis()
            
            if (onDone != null) {
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        // Back to main thread for safety
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
                    }
                    override fun onError(utteranceId: String?) {}
                })
            }
            
            tts?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
        } else {
            pendingMessages.add(text)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
