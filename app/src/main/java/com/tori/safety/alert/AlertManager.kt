package com.tori.safety.alert

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.tori.safety.data.model.Language
import com.tori.safety.ml.AlertType
import kotlinx.coroutines.*
import java.util.*

/**
 * Alert manager for TOR-I app - handles audio, vibration, and TTS alerts
 */
class AlertManager(private val context: Context) {
    
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val toneGenerator: ToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    
    private var currentLanguage = Language.ENGLISH
    private var alertVolume = 80
    private var vibrationEnabled = true
    private var ttsEnabled = true
    
    private var alertJob: Job? = null
    
    suspend fun initialize() {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            isTtsInitialized = status == TextToSpeech.SUCCESS
            if (isTtsInitialized) {
                setupTTS()
            }
        }
    }
    
    private fun setupTTS() {
        tts?.let { tts ->
            val locale = when (currentLanguage) {
                Language.ENGLISH -> Locale.ENGLISH
                Language.TAMIL -> Locale("ta", "IN")
            }
            
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English if Tamil is not supported
                tts.setLanguage(Locale.ENGLISH)
                currentLanguage = Language.ENGLISH
            }
            
            tts.setSpeechRate(0.8f)
            tts.setPitch(1.0f)
            
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                
                override fun onDone(utteranceId: String?) {
                    onAlertComplete()
                }
                
                override fun onError(utteranceId: String?) {
                    onAlertComplete()
                }
            })
        }
    }
    
    fun updateSettings(
        language: Language,
        volume: Int,
        vibrationEnabled: Boolean,
        ttsEnabled: Boolean
    ) {
        this.currentLanguage = language
        this.alertVolume = volume.coerceIn(0, 100)
        this.vibrationEnabled = vibrationEnabled
        this.ttsEnabled = ttsEnabled
        
        if (isTtsInitialized) {
            setupTTS()
        }
    }
    
    fun triggerAlert(type: AlertType) {
        if (alertJob?.isActive == true) return 

        alertJob = CoroutineScope(Dispatchers.Main).launch {
            when (type) {
                AlertType.DROWSINESS -> playAlert(
                    tone = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500),
                    messageEn = "Alert! You look sleepy. Please stop and rest.",
                    messageTa = "அலர்ட்! நீங்கள் தூங்கப்போகிறீர்கள். தயவுசெய்து ஓய்வெடுக்கவும்."
                )
                AlertType.DISTRACTION -> playAlert(
                    tone = ToneGenerator.TONE_CDMA_PIP,
                    vibrationPattern = longArrayOf(0, 200, 100, 200),
                    messageEn = "Keep your eyes on the road.",
                    messageTa = "சாலையை கவனிக்கவும்."
                )
                AlertType.FAINT -> playAlert(
                    tone = ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK,
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000),
                    messageEn = "Are you okay? Faint detected.",
                    messageTa = "நீங்கள் நலமாக இருக்கிறீர்களா?"
                )
                AlertType.SOS -> playAlert(
                    tone = ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK,
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000),
                    messageEn = "Emergency SOS activated. Help is being contacted.",
                    messageTa = "அவசர SOS செயல்படுத்தப்பட்டது. உதவி தொடர்பு கொள்ளப்படுகிறது.",
                    isEmergency = true
                )
            }
        }
    }
    
    // Deprecated methods for backward compatibility if needed, but better to use triggerAlert
    fun triggerDrowsinessAlert() = triggerAlert(AlertType.DROWSINESS)
    fun triggerSOSAlert() = triggerAlert(AlertType.SOS)
    
    private fun playAlert(
        tone: Int,
        vibrationPattern: LongArray,
        messageEn: String,
        messageTa: String,
        isEmergency: Boolean = false
    ) {
        // Audio
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val volume = if (isEmergency) maxVolume else (maxVolume * alertVolume / 100.0).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
            toneGenerator.startTone(tone, if (isEmergency) 2000 else 1000)
        } catch (e: Exception) { /* Ignore */ }

        // Vibration
        if (vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1)) // -1 for no repeat
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(vibrationPattern, -1)
                }
            } catch (e: Exception) { /* Ignore */ }
        }

        // TTS
        if (ttsEnabled && isTtsInitialized) {
            val message = if (currentLanguage == Language.TAMIL) messageTa else messageEn
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "alert")
        } else {
            onAlertComplete()
        }
    }
    
    fun stopCurrentAlert() {
        alertJob?.cancel()
        alertJob = null
        vibrator.cancel()
        tts?.stop()
    }
    
    fun release() {
        stopCurrentAlert()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false
        toneGenerator.release()
    }
    
    private fun onAlertComplete() {
        alertJob = null
    }
    
    companion object {
        private const val TAG = "AlertManager"
    }
}