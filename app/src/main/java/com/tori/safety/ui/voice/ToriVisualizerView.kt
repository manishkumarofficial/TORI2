package com.tori.safety.ui.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class ToriVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // Neon Blue
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000E5FF") // Transparent Blue
        style = Paint.Style.FILL
    }

    private var phase = 0f
    private var amplitude = 0f
    private var state = State.IDLE

    enum class State {
        IDLE, LISTENING, THINKING, SPEAKING
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { 
            phase += 0.1f
            invalidate()
        }
    }

    init {
        animator.start()
    }

    fun setState(newState: State) {
        state = newState
        when (state) {
            State.IDLE -> amplitude = 10f
            State.LISTENING -> amplitude = 50f
            State.THINKING -> amplitude = 20f
            State.SPEAKING -> amplitude = 40f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height / 2f
        val radius = width / 4f

        // Draw Glow
        if (state == State.LISTENING || state == State.SPEAKING) {
            val pulse = 10f * sin(phase)
            canvas.drawCircle(cx, cy, radius + pulse + amplitude, glowPaint)
        }

        // Draw Central Circle
        canvas.drawCircle(cx, cy, radius, paint)

        // Draw Waveform if active
        if (state == State.LISTENING || state == State.SPEAKING) {
            // Simple waveform simulation
            for (i in 0 until 5) {
                val offset = i * 20f
                val waveRadius = radius + (amplitude * sin(phase + i))
                paint.alpha = (255 / (i + 1))
                canvas.drawCircle(cx, cy, waveRadius, paint)
            }
            paint.alpha = 255
        }
    }
}
