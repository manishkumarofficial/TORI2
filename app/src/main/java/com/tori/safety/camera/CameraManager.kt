package com.tori.safety.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.tori.safety.ml.DrowsinessDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera manager for TOR-I app using CameraX
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameProcessed: (Bitmap) -> Unit,
    private val onError: (Exception) -> Unit
) {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var isInitialized = false
    private var isRunning = false
    
    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            cameraProvider = ProcessCameraProvider.getInstance(context).get()
            isInitialized = true
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    suspend fun startCamera(
        previewView: androidx.camera.view.PreviewView,
        lowPowerMode: Boolean = false
    ) = withContext(Dispatchers.Main) {
        if (!isInitialized) {
            onError(Exception("Camera not initialized"))
            return@withContext
        }
        
        try {
            val cameraProvider = cameraProvider ?: return@withContext
            
            // Preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            // Image analysis use case
            val frameRate = if (lowPowerMode) 15 else 30
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
                
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer { imageProxy ->
                        processImage(imageProxy)
                    })
                }
            
            // Camera selector (front camera)
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()
            
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            
            this@CameraManager.imageAnalyzer = imageAnalysis
            isRunning = true
            
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    suspend fun stopCamera() = withContext(Dispatchers.Main) {
        try {
            cameraProvider?.unbindAll()
            camera = null
            imageAnalyzer = null
            isRunning = false
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            onFrameProcessed(bitmap)
        } catch (e: Exception) {
            onError(e)
        } finally {
            imageProxy.close()
        }
    }
    
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val pixelStride = imageProxy.planes[0].pixelStride
        val rowStride = imageProxy.planes[0].rowStride
        val rowPadding = rowStride - pixelStride * imageProxy.width
        
        val bitmap = Bitmap.createBitmap(
            imageProxy.width + rowPadding / pixelStride,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        
        bitmap.copyPixelsFromBuffer(buffer)
        
        // Crop the bitmap to remove padding
        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)
        } else {
            bitmap
        }
    }
    
    fun isCameraRunning(): Boolean = isRunning
    
    fun cleanup() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        imageAnalyzer = null
        isInitialized = false
        isRunning = false
    }
    
    companion object {
        private const val TAG = "CameraManager"
    }
}

/**
 * Image analyzer for processing camera frames
 */
private class ImageAnalyzer(
    private val onImageAvailable: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {
    
    override fun analyze(imageProxy: ImageProxy) {
        onImageAvailable(imageProxy)
    }
}