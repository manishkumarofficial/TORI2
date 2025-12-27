package com.tori.safety.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.tori.safety.R
import kotlin.math.sin
import kotlin.math.cos

/**
 * Custom view for Tori voice assistant visualization
 * Shows different animations based on voice state
 */
class VoiceAssistantView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Colors
    private val primaryColor = Color.parseColor("#00E5FF") // Neon blue
    private val backgroundColor = Color.parseColor("#1A1A1A") // Dark background
    private val shadowColor = Color.parseColor("#004D5C") // Dark blue shadow
    
    // Animation properties
    private var animationProgress = 0f
    private var pulseAnimator: ValueAnimator? = null
    private var currentState = VoiceState.IDLE
    
    // Drawing properties
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    
    init {
        setupPaint()
    }
    
    private fun setupPaint() {
        paint.apply {
            style = Paint.Style.FILL
            color = primaryColor
        }
        
        shadowPaint.apply {
            style = Paint.Style.FILL
            color = shadowColor
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = minOf(w, h) / 4f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        when (currentState) {
            VoiceState.IDLE -> drawIdleState(canvas)
            VoiceState.LISTENING_FOR_WAKE_WORD -> drawListeningState(canvas)
            VoiceState.WAKE_WORD_DETECTED -> drawActivatedState(canvas)
            VoiceState.LISTENING_FOR_COMMAND -> drawListeningState(canvas)
            VoiceState.PROCESSING -> drawProcessingState(canvas)
            VoiceState.SPEAKING -> drawSpeakingState(canvas)
            VoiceState.ERROR -> drawErrorState(canvas)
        }
    }
    
    private fun drawIdleState(canvas: Canvas) {
        // Soft heartbeat glow
        val alpha = (128 + 127 * sin(animationProgress * Math.PI * 2)).toInt()
        paint.alpha = alpha
        shadowPaint.alpha = alpha / 2
        
        // Draw shadow (neumorphic effect)
        canvas.drawCircle(centerX + 8, centerY + 8, baseRadius * 0.8f, shadowPaint)
        
        // Draw main circle
        canvas.drawCircle(centerX, centerY, baseRadius * 0.8f, paint)
        
        // Draw inner highlight
        paint.alpha = alpha / 3
        canvas.drawCircle(centerX - 4, centerY - 4, baseRadius * 0.6f, paint)
    }
    
    private fun drawListeningState(canvas: Canvas) {
        // Bright pulsating glow
        val pulseScale = 1f + 0.3f * sin(animationProgress * Math.PI * 4).toFloat()
        val alpha = (200 + 55 * sin(animationProgress * Math.PI * 4)).toInt()
        
        paint.alpha = alpha
        shadowPaint.alpha = alpha / 2
        
        // Draw outer glow
        paint.alpha = alpha / 4
        canvas.drawCircle(centerX, centerY, baseRadius * pulseScale * 1.2f, paint)
        
        // Draw shadow
        canvas.drawCircle(centerX + 6, centerY + 6, baseRadius * pulseScale, shadowPaint)
        
        // Draw main circle
        paint.alpha = alpha
        canvas.drawCircle(centerX, centerY, baseRadius * pulseScale, paint)
        
        // Draw inner highlight
        paint.alpha = alpha / 2
        canvas.drawCircle(centerX - 3, centerY - 3, baseRadius * pulseScale * 0.7f, paint)
    }
    
    private fun drawActivatedState(canvas: Canvas) {
        // Quick bright flash
        val alpha = 255
        paint.alpha = alpha
        shadowPaint.alpha = alpha / 2
        
        // Draw bright outer ring
        paint.alpha = alpha / 3
        canvas.drawCircle(centerX, centerY, baseRadius * 1.5f, paint)
        
        // Draw shadow
        canvas.drawCircle(centerX + 4, centerY + 4, baseRadius, shadowPaint)
        
        // Draw main circle
        paint.alpha = alpha
        canvas.drawCircle(centerX, centerY, baseRadius, paint)
    }
    
    private fun drawProcessingState(canvas: Canvas) {
        // Rotating ripple effect
        val rotationAngle = animationProgress * 360f
        
        canvas.save()
        canvas.rotate(rotationAngle, centerX, centerY)
        
        // Draw multiple ripples
        for (i in 0..2) {
            val rippleRadius = baseRadius * (0.5f + i * 0.3f)
            val alpha = (100 - i * 30).coerceAtLeast(20)
            
            paint.alpha = alpha
            canvas.drawCircle(centerX, centerY, rippleRadius, paint)
        }
        
        canvas.restore()
        
        // Draw center circle
        paint.alpha = 200
        canvas.drawCircle(centerX, centerY, baseRadius * 0.4f, paint)
    }
    
    private fun drawSpeakingState(canvas: Canvas) {
        // Waveform-like animation
        val waveAmplitude = baseRadius * 0.2f
        val alpha = 200
        
        paint.alpha = alpha
        shadowPaint.alpha = alpha / 2
        
        // Draw base circle
        canvas.drawCircle(centerX + 4, centerY + 4, baseRadius * 0.8f, shadowPaint)
        canvas.drawCircle(centerX, centerY, baseRadius * 0.8f, paint)
        
        // Draw animated wave bars around the circle
        val barCount = 8
        for (i in 0 until barCount) {
            val angle = (i * 360f / barCount) + animationProgress * 180f
            val barHeight = waveAmplitude * (1f + sin((animationProgress * 4 + i) * Math.PI).toFloat())
            
            val startX = centerX + (baseRadius + 10) * cos(Math.toRadians(angle.toDouble())).toFloat()
            val startY = centerY + (baseRadius + 10) * sin(Math.toRadians(angle.toDouble())).toFloat()
            val endX = centerX + (baseRadius + 10 + barHeight) * cos(Math.toRadians(angle.toDouble())).toFloat()
            val endY = centerY + (baseRadius + 10 + barHeight) * sin(Math.toRadians(angle.toDouble())).toFloat()
            
            paint.strokeWidth = 8f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawErrorState(canvas: Canvas) {
        // Red pulsing
        val errorColor = Color.parseColor("#FF5252")
        paint.color = errorColor
        
        val alpha = (150 + 105 * sin(animationProgress * Math.PI * 6)).toInt()
        paint.alpha = alpha
        
        canvas.drawCircle(centerX, centerY, baseRadius * 0.9f, paint)
        
        // Reset color
        paint.color = primaryColor
    }
    
    fun setState(state: VoiceState) {
        if (currentState != state) {
            currentState = state
            startAnimation()
        }
    }
    
    private fun startAnimation() {
        pulseAnimator?.cancel()
        
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (currentState) {
                VoiceState.IDLE -> 3000L
                VoiceState.LISTENING_FOR_WAKE_WORD, VoiceState.LISTENING_FOR_COMMAND -> 2000L
                VoiceState.WAKE_WORD_DETECTED -> 500L
                VoiceState.PROCESSING -> 1500L
                VoiceState.SPEAKING -> 1000L
                VoiceState.ERROR -> 1000L
            }
            
            repeatCount = if (currentState == VoiceState.WAKE_WORD_DETECTED) 0 else ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                animationProgress = animator.animatedValue as Float
                invalidate()
            }
            
            start()
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}