package com.devson.vedinsta.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat

class CarouselDotsIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dotCount = 0
    private var selectedPosition = 0

    // Enhanced Colors
    private val activeColor = 0xFFE91E63.toInt() // Pink
    private val inactiveColor = 0xFFE0E0E0.toInt() // Light gray
    private val transitionalColor = 0xFFFFB6C1.toInt() // Light pink

    // Enhanced dot properties
    private val inactiveDotRadius = dpToPx(5f)
    private val activeDotRadius = dpToPx(8f)
    private val dotSpacing = dpToPx(18f)
    private val animationDuration = 400L
    private val scaleAnimationDuration = 300L

    // Paint objects
    private val activePaint = Paint().apply {
        isAntiAlias = true
        color = activeColor
        style = Paint.Style.FILL
    }

    private val inactivePaint = Paint().apply {
        isAntiAlias = true
        color = inactiveColor
        style = Paint.Style.FILL
    }

    private val transitionalPaint = Paint().apply {
        isAntiAlias = true
        color = transitionalColor
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = 0x20000000
        style = Paint.Style.FILL
    }

    // Animation properties
    private var animatedPosition = 0f
    private var animatedRadius = inactiveDotRadius
    private var colorProgress = 0f

    // Animators
    private var positionAnimator: ValueAnimator? = null
    private var radiusAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null

    // Dynamic properties
    private val dotRadii = mutableListOf<Float>()
    private val dotColors = mutableListOf<Int>()

    init {
        // Initialize with better drawing properties
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setDotCount(count: Int) {
        if (dotCount != count) {
            dotCount = count
            selectedPosition = 0
            animatedPosition = 0f

            // Initialize dynamic arrays
            dotRadii.clear()
            dotColors.clear()

            repeat(count) {
                dotRadii.add(inactiveDotRadius)
                dotColors.add(inactiveColor)
            }

            if (count > 0) {
                dotRadii[0] = activeDotRadius
                dotColors[0] = activeColor
            }

            requestLayout()
            invalidate()
        }
    }

    fun setSelectedPosition(position: Int, animate: Boolean = true) {
        if (position != selectedPosition && position in 0 until dotCount) {
            val oldPosition = selectedPosition
            selectedPosition = position

            if (animate) {
                animateToPosition(oldPosition, position)
            } else {
                animatedPosition = position.toFloat()
                updateDotStates(position)
                invalidate()
            }
        }
    }

    private fun animateToPosition(fromPosition: Int, toPosition: Int) {
        // Cancel existing animations
        positionAnimator?.cancel()
        radiusAnimator?.cancel()
        colorAnimator?.cancel()

        // Position animation with overshoot
        positionAnimator = ValueAnimator.ofFloat(fromPosition.toFloat(), toPosition.toFloat()).apply {
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                animatedPosition = animator.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Radius animation with bounce effect
        radiusAnimator = ValueAnimator.ofFloat(activeDotRadius, activeDotRadius * 1.2f, activeDotRadius).apply {
            duration = scaleAnimationDuration
            interpolator = OvershootInterpolator(1.5f)
            addUpdateListener { animator ->
                animatedRadius = animator.animatedValue as Float

                // Update dot states during animation
                updateDotStates(toPosition)
                invalidate()
            }
            start()
        }

        // Color transition animation
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            addUpdateListener { animator ->
                colorProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updateDotStates(activePosition: Int) {
        for (i in 0 until dotCount) {
            if (i == activePosition) {
                dotRadii[i] = animatedRadius
                dotColors[i] = activeColor
            } else {
                dotRadii[i] = inactiveDotRadius
                dotColors[i] = inactiveColor
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = if (dotCount > 0) {
            (dotCount * activeDotRadius * 2 + (dotCount - 1) * dotSpacing + dpToPx(16f)).toInt()
        } else {
            0
        }

        val desiredHeight = (activeDotRadius * 2.5f).toInt() // Extra height for shadows

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dotCount <= 0) return

        val centerY = height / 2f
        val totalWidth = (dotCount * activeDotRadius * 2 + (dotCount - 1) * dotSpacing)
        val startX = (width - totalWidth) / 2f + activeDotRadius

        // Draw connection line (subtle)
        drawConnectionLine(canvas, centerY, startX)

        // Draw inactive dots with shadows
        for (i in 0 until dotCount) {
            if (i != selectedPosition) {
                val x = startX + i * (activeDotRadius * 2 + dotSpacing)

                // Draw shadow
                canvas.drawCircle(x, centerY + dpToPx(1f), dotRadii[i], shadowPaint)

                // Draw dot
                inactivePaint.color = dotColors[i]
                canvas.drawCircle(x, centerY, dotRadii[i], inactivePaint)
            }
        }

        // Draw active dot with enhanced effects
        drawActiveDot(canvas, centerY, startX)

        // Draw progress indicator
        drawProgressIndicator(canvas, centerY, startX)
    }

    private fun drawConnectionLine(canvas: Canvas, centerY: Float, startX: Float) {
        if (dotCount <= 1) return

        val lineY = centerY
        val lineStartX = startX - activeDotRadius * 0.5f
        val lineEndX = startX + (dotCount - 1) * (activeDotRadius * 2 + dotSpacing) + activeDotRadius * 0.5f

        val linePaint = Paint().apply {
            isAntiAlias = true
            color = inactiveColor
            strokeWidth = dpToPx(1f)
            alpha = 60
        }

        canvas.drawLine(lineStartX, lineY, lineEndX, lineY, linePaint)
    }

    private fun drawActiveDot(canvas: Canvas, centerY: Float, startX: Float) {
        val activeX = startX + animatedPosition * (activeDotRadius * 2 + dotSpacing)

        // Draw enhanced shadow for active dot
        val shadowOffset = dpToPx(2f)
        val enhancedShadowPaint = Paint().apply {
            isAntiAlias = true
            color = 0x40000000
            style = Paint.Style.FILL
        }
        canvas.drawCircle(activeX, centerY + shadowOffset, animatedRadius, enhancedShadowPaint)

        // Draw outer glow effect
        val glowRadius = animatedRadius * 1.3f
        val glowPaint = Paint().apply {
            isAntiAlias = true
            color = activeColor
            alpha = 60
            style = Paint.Style.FILL
        }
        canvas.drawCircle(activeX, centerY, glowRadius, glowPaint)

        // Draw main active dot
        activePaint.color = activeColor
        canvas.drawCircle(activeX, centerY, animatedRadius, activePaint)

        // Draw inner highlight
        val highlightPaint = Paint().apply {
            isAntiAlias = true
            color = 0x40FFFFFF
            style = Paint.Style.FILL
        }
        canvas.drawCircle(activeX, centerY - dpToPx(1f), animatedRadius * 0.6f, highlightPaint)
    }

    private fun drawProgressIndicator(canvas: Canvas, centerY: Float, startX: Float) {
        if (dotCount <= 1) return

        // Draw progress line from start to current position
        val progressEndX = startX + animatedPosition * (activeDotRadius * 2 + dotSpacing)

        val progressPaint = Paint().apply {
            isAntiAlias = true
            color = activeColor
            strokeWidth = dpToPx(3f)
            alpha = 100
            strokeCap = Paint.Cap.ROUND
        }

        canvas.drawLine(startX - activeDotRadius, centerY, progressEndX, centerY, progressPaint)

        // Draw animated trail effect
        drawAnimatedTrail(canvas, centerY, startX, progressEndX)
    }

    private fun drawAnimatedTrail(canvas: Canvas, centerY: Float, startX: Float, progressEndX: Float) {
        val trailLength = dpToPx(20f)
        val trailStartX = (progressEndX - trailLength).coerceAtLeast(startX - activeDotRadius)

        // Create gradient effect for trail
        val trailPaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = dpToPx(2f)
            strokeCap = Paint.Cap.ROUND
        }

        // Draw multiple trail segments with decreasing opacity
        val segments = 5
        for (i in 0 until segments) {
            val segmentProgress = i.toFloat() / segments
            val segmentStartX = trailStartX + (progressEndX - trailStartX) * segmentProgress
            val segmentEndX = trailStartX + (progressEndX - trailStartX) * ((i + 1).toFloat() / segments)

            trailPaint.color = activeColor
            trailPaint.alpha = (255 * (segmentProgress * 0.8f + 0.2f)).toInt()

            canvas.drawLine(segmentStartX, centerY, segmentEndX, centerY, trailPaint)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        positionAnimator?.cancel()
        radiusAnimator?.cancel()
        colorAnimator?.cancel()
    }

    // Additional method for smooth manual position updates
    fun updatePositionSmooth(newPosition: Float) {
        animatedPosition = newPosition
        invalidate()
    }

    // Method to get current animation progress for external use
    fun getAnimationProgress(): Float {
        return colorProgress
    }
}
