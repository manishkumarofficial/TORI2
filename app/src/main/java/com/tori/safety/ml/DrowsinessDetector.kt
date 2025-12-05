package com.tori.safety.ml

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.tori.safety.data.model.DetectionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

class DrowsinessDetector(private val context: Context) : ImageAnalysis.Analyzer {

    private val _detectionFlow = MutableSharedFlow<DetectionResult>()
    val detectionFlow: SharedFlow<DetectionResult> = _detectionFlow

    private var faceDetector: FaceDetector? = null
    private var isInitialized = false
    private var settings = DetectionSettings()
    
    // Detection state tracking
    private var eyeClosedStartTime: Long = 0
    private var headNodStartTime: Long = 0
    private var faceNotVisibleStartTime: Long = 0
    private var yawnStartTime: Long = 0
    private var distractionStartTime: Long = 0
    private var lastFaceDetectedTime: Long = 0
    
    // Constants for detection timing
    private val EYE_CLOSED_THRESHOLD_MS = 2000L // 2 seconds
    private val YAWN_THRESHOLD_MS = 1500L // 1.5 seconds
    private val DISTRACTION_THRESHOLD_MS = 2000L // 2 seconds
    private val HEAD_NOD_THRESHOLD_MS = 2000L 
    private val FACE_NOT_VISIBLE_THRESHOLD_MS = 5000L 
    
    // Thresholds
    private val MAR_THRESHOLD = 0.5f // Mouth Aspect Ratio threshold for yawning
    private val HEAD_NOD_ANGLE_THRESHOLD = 20.0f 
    private val DISTRACTION_ANGLE_THRESHOLD = 30.0f // Head yaw/pitch threshold

    init {
        initialize()
    }

    private fun initialize(): Boolean {
        return try {
            // Configure ML Kit Face Detection with Contours for Yawn Detection
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL) // Enable contours
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            
            faceDetector = FaceDetection.getClient(options)
            isInitialized = true
            Log.d(TAG, "DrowsinessDetector initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DrowsinessDetector", e)
            false
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!isInitialized) {
            imageProxy.close()
            return
        }
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            faceDetector?.process(image)
                ?.addOnSuccessListener { faces ->
                    CoroutineScope(Dispatchers.Main).launch {
                        processFaces(faces, System.currentTimeMillis())
                    }
                }
                ?.addOnFailureListener { exception ->
                    Log.e(TAG, "Face detection failed", exception)
                }
                ?.addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private suspend fun processFaces(faces: List<Face>, currentTime: Long) {
        lastFaceDetectedTime = currentTime
        
        if (faces.isEmpty()) {
            handleNoFaceDetected(currentTime)
            return
        }
        
        // Process the first detected face
        val face = faces[0]
        
        // 1. Eye State (Drowsiness)
        val leftEye = face.leftEyeOpenProbability ?: 1.0f
        val rightEye = face.rightEyeOpenProbability ?: 1.0f
        val threshold = settings.earThreshold.toFloat()
        val eyesClosed = leftEye < threshold && rightEye < threshold
        
        // 2. Head Pose (Nodding & Distraction)
        val pitch = face.headEulerAngleX // Up/Down
        val yaw = face.headEulerAngleY   // Left/Right
        val headNodding = abs(pitch) > HEAD_NOD_ANGLE_THRESHOLD
        val isDistracted = abs(yaw) > DISTRACTION_ANGLE_THRESHOLD || abs(pitch) > DISTRACTION_ANGLE_THRESHOLD
        
        // 3. Mouth State (Yawning)
        val mar = calculateMAR(face)
        val isYawning = mar > MAR_THRESHOLD
        
        // Emit detection result for UI updates
        _detectionFlow.emit(DetectionResult(
            faceDetected = true,
            leftEyeOpenProbability = leftEye,
            rightEyeOpenProbability = rightEye,
            headAngle = pitch,
            eyesClosed = eyesClosed,
            headNodding = headNodding,
            isYawning = isYawning,
            isDistracted = isDistracted,
            marValue = mar
        ))
        
        // --- Alert Logic ---
        
        // Drowsiness (Eyes Closed)
        if (eyesClosed) {
            if (eyeClosedStartTime == 0L) eyeClosedStartTime = currentTime
            else if (currentTime - eyeClosedStartTime > EYE_CLOSED_THRESHOLD_MS) {
                emitAlert(AlertType.DROWSINESS, "Drowsiness Detected!")
                eyeClosedStartTime = currentTime // Reset to avoid spam
            }
        } else {
            eyeClosedStartTime = 0L
        }
        
        // Yawning
        if (isYawning) {
            if (yawnStartTime == 0L) yawnStartTime = currentTime
            else if (currentTime - yawnStartTime > YAWN_THRESHOLD_MS) {
                emitAlert(AlertType.DROWSINESS, "Yawning Detected - Take a break!")
                yawnStartTime = currentTime
            }
        } else {
            yawnStartTime = 0L
        }
        
        // Distraction
        if (isDistracted) {
            if (distractionStartTime == 0L) distractionStartTime = currentTime
            else if (currentTime - distractionStartTime > DISTRACTION_THRESHOLD_MS) {
                emitAlert(AlertType.DISTRACTION, "Keep eyes on the road!")
                distractionStartTime = currentTime
            }
        } else {
            distractionStartTime = 0L
        }
        
        // Faint (Head Nodding)
        if (headNodding && !isDistracted) { // Distraction might trigger nod, so prioritize distraction
            if (headNodStartTime == 0L) headNodStartTime = currentTime
            else if (currentTime - headNodStartTime > HEAD_NOD_THRESHOLD_MS) {
                emitAlert(AlertType.FAINT, "Possible Faint Detected")
                headNodStartTime = currentTime
            }
        } else {
            headNodStartTime = 0L
        }
    }
    
    private suspend fun emitAlert(type: AlertType, message: String) {
        _detectionFlow.emit(DetectionResult(
            faceDetected = true,
            alertType = type,
            message = message
        ))
    }
    
    private fun handleNoFaceDetected(currentTime: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            _detectionFlow.emit(DetectionResult(
                faceDetected = false,
                message = "Face not visible"
            ))
        }
        
        if (faceNotVisibleStartTime == 0L) {
            faceNotVisibleStartTime = currentTime
        } else if (currentTime - faceNotVisibleStartTime > FACE_NOT_VISIBLE_THRESHOLD_MS) {
            CoroutineScope(Dispatchers.Main).launch {
                emitAlert(AlertType.SOS, "Driver not visible - Emergency?")
            }
            faceNotVisibleStartTime = currentTime
        }
    }
    
    private fun calculateMAR(face: Face): Float {
        val upperLip = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLip = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
        
        if (upperLip.isNullOrEmpty() || lowerLip.isNullOrEmpty()) return 0f
        
        // Simple MAR: Height / Width
        // Height = Distance between center of upper and lower lip
        // Width = Distance between mouth corners (FaceLandmark)
        
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        
        if (mouthLeft != null && mouthRight != null) {
            val width = hypot((mouthLeft.x - mouthRight.x).toDouble(), (mouthLeft.y - mouthRight.y).toDouble()).toFloat()
            
            // Average height from a few points in the middle
            val centerIndex = upperLip.size / 2
            val p1 = upperLip[centerIndex]
            val p2 = lowerLip[centerIndex] // Assuming same size/indexing roughly
            
            val height = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
            
            return if (width > 0) height / width else 0f
        }
        return 0f
    }
    
    fun updateSettings(settings: DetectionSettings) {
        this.settings = settings
    }

    fun release() {
        faceDetector?.close()
        faceDetector = null
        isInitialized = false
        Log.d(TAG, "DrowsinessDetector released")
    }

    companion object {
        private const val TAG = "DrowsinessDetector"
    }
}

// Data classes for detection results
data class DetectionResult(
    val faceDetected: Boolean = false,
    val leftEyeOpenProbability: Float = 1.0f,
    val rightEyeOpenProbability: Float = 1.0f,
    val headAngle: Float = 0f,
    val eyesClosed: Boolean = false,
    val headNodding: Boolean = false,
    val isYawning: Boolean = false,
    val isDistracted: Boolean = false,
    val marValue: Float = 0f,
    val alertType: AlertType? = null,
    val message: String = ""
)

enum class AlertType {
    DROWSINESS,
    FAINT,
    SOS,
    DISTRACTION
}