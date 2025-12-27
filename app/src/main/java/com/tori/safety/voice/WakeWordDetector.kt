package com.tori.safety.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Improved wake word detector for "Hey Tor"
 * Uses advanced audio analysis to reduce false positives from background music
 */
class WakeWordDetector(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    
    private val _wakeWordDetected = MutableSharedFlow<Float>()
    val wakeWordDetected: SharedFlow<Float> = _wakeWordDetected.asSharedFlow()
    
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    // Advanced detection parameters
    private var lastDetectionTime = 0L
    private val detectionCooldown = 4000L // 4 seconds cooldown
    private val energyHistory = mutableListOf<Double>()
    private val maxHistorySize = 50 // Keep last 50 energy readings
    
    // Thresholds for better detection
    private val baseEnergyThreshold = 2000.0 // Base energy threshold
    private val speechPatternThreshold = 1.5 // Speech vs music pattern detection
    private val consecutiveFramesRequired = 8 // Require more consecutive frames
    private val silenceFramesRequired = 3 // Require silence before speech
    
    // Pattern detection variables
    private var consecutiveHighEnergy = 0
    private var consecutiveSilence = 0
    private var speechLikePattern = false
    
    fun initialize() {
        Log.d(TAG, "Initializing advanced wake word detector...")
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 4 // Larger buffer for better analysis
            )
            Log.d(TAG, "Advanced wake word detector initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake word detector", e)
            throw e
        }
    }
    
    fun startListening() {
        if (isListening) return
        
        Log.d(TAG, "Starting advanced wake word detection...")
        isListening = true
        
        scope.launch {
            try {
                audioRecord?.startRecording()
                detectWakeWordAdvanced()
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection", e)
                isListening = false
            }
        }
    }
    
    fun stopListening() {
        Log.d(TAG, "Stopping wake word detection...")
        isListening = false
        audioRecord?.stop()
        energyHistory.clear()
        resetDetectionState()
    }
    
    private suspend fun detectWakeWordAdvanced() {
        val buffer = ShortArray(bufferSize)
        
        while (isListening) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (bytesRead > 0) {
                    val energy = calculateEnergy(buffer, bytesRead)
                    val spectralCentroid = calculateSpectralCentroid(buffer, bytesRead)
                    val zeroCrossingRate = calculateZeroCrossingRate(buffer, bytesRead)
                    
                    // Update energy history for adaptive thresholding
                    updateEnergyHistory(energy)
                    
                    // Calculate adaptive threshold based on background noise
                    val adaptiveThreshold = calculateAdaptiveThreshold()
                    
                    // Analyze if this sounds like speech vs music
                    val isSpeechLike = analyzeSpeechPattern(energy, spectralCentroid, zeroCrossingRate)
                    
                    if (energy > adaptiveThreshold && isSpeechLike) {
                        consecutiveHighEnergy++
                        consecutiveSilence = 0
                        
                        if (consecutiveHighEnergy >= consecutiveFramesRequired) {
                            val currentTime = System.currentTimeMillis()
                            
                            // Check cooldown and speech pattern
                            if (currentTime - lastDetectionTime > detectionCooldown && speechLikePattern) {
                                Log.d(TAG, "Wake word detected! Energy: $energy, Threshold: $adaptiveThreshold")
                                lastDetectionTime = currentTime
                                
                                // Calculate confidence based on multiple factors
                                val confidence = calculateConfidence(energy, adaptiveThreshold, spectralCentroid, zeroCrossingRate)
                                
                                _wakeWordDetected.emit(confidence)
                                
                                resetDetectionState()
                                delay(2000) // Longer pause after detection
                            }
                        }
                    } else if (energy < adaptiveThreshold * 0.3) {
                        // Low energy (silence)
                        consecutiveSilence++
                        if (consecutiveSilence >= silenceFramesRequired) {
                            consecutiveHighEnergy = 0
                            speechLikePattern = false
                        }
                    } else {
                        // Medium energy, reset high energy counter but not silence
                        consecutiveHighEnergy = 0
                    }
                    
                    // Update speech pattern detection
                    updateSpeechPattern(isSpeechLike)
                }
                
                delay(30) // Check every 30ms for better responsiveness
                
            } catch (e: Exception) {
                Log.e(TAG, "Error reading audio", e)
                break
            }
        }
    }
    
    private fun calculateEnergy(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        return sqrt(sum / length)
    }
    
    private fun calculateSpectralCentroid(buffer: ShortArray, length: Int): Double {
        // Simplified spectral centroid calculation
        var weightedSum = 0.0
        var magnitudeSum = 0.0
        
        for (i in 0 until length) {
            val magnitude = abs(buffer[i].toDouble())
            weightedSum += i * magnitude
            magnitudeSum += magnitude
        }
        
        return if (magnitudeSum > 0) weightedSum / magnitudeSum else 0.0
    }
    
    private fun calculateZeroCrossingRate(buffer: ShortArray, length: Int): Double {
        var crossings = 0
        for (i in 1 until length) {
            if ((buffer[i] >= 0) != (buffer[i-1] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / length
    }
    
    private fun updateEnergyHistory(energy: Double) {
        energyHistory.add(energy)
        if (energyHistory.size > maxHistorySize) {
            energyHistory.removeAt(0)
        }
    }
    
    private fun calculateAdaptiveThreshold(): Double {
        if (energyHistory.size < 10) return baseEnergyThreshold
        
        // Calculate background noise level
        val sortedEnergy = energyHistory.sorted()
        val backgroundNoise = sortedEnergy.take(sortedEnergy.size / 3).average() // Bottom third
        
        // Adaptive threshold is background noise + margin
        return maxOf(baseEnergyThreshold, backgroundNoise * 3.0)
    }
    
    private fun analyzeSpeechPattern(energy: Double, spectralCentroid: Double, zeroCrossingRate: Double): Boolean {
        // Speech typically has:
        // - Moderate spectral centroid (not too high like music)
        // - Moderate zero crossing rate (not too high like noise)
        // - Energy in speech range
        
        val spectralCentroidNormalized = spectralCentroid / 1000.0 // Normalize
        val isSpeechSpectrum = spectralCentroidNormalized in 0.1..0.8 // Speech range
        val isSpeechZCR = zeroCrossingRate in 0.01..0.15 // Speech ZCR range
        val isSpeechEnergy = energy > baseEnergyThreshold * 0.5
        
        return isSpeechSpectrum && isSpeechZCR && isSpeechEnergy
    }
    
    private fun updateSpeechPattern(isSpeechLike: Boolean) {
        if (isSpeechLike && consecutiveHighEnergy >= 3) {
            speechLikePattern = true
        }
    }
    
    private fun calculateConfidence(
        energy: Double, 
        threshold: Double, 
        spectralCentroid: Double, 
        zeroCrossingRate: Double
    ): Float {
        // Calculate confidence based on multiple factors
        val energyRatio = (energy / threshold).coerceIn(0.0, 2.0) / 2.0
        val spectralScore = if (spectralCentroid / 1000.0 in 0.2..0.6) 1.0 else 0.5
        val zcrScore = if (zeroCrossingRate in 0.02..0.12) 1.0 else 0.5
        
        val confidence = (energyRatio * 0.5 + spectralScore * 0.3 + zcrScore * 0.2).toFloat()
        return confidence.coerceIn(0.3f, 0.95f) // Reasonable confidence range
    }
    
    private fun resetDetectionState() {
        consecutiveHighEnergy = 0
        consecutiveSilence = 0
        speechLikePattern = false
    }
    
    fun release() {
        Log.d(TAG, "Releasing wake word detector...")
        stopListening()
        scope.cancel()
        audioRecord?.release()
        audioRecord = null
    }
    
    companion object {
        private const val TAG = "WakeWordDetector"
    }
}