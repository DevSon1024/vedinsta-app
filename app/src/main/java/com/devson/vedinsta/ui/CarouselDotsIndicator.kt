package com.devson.vedinsta.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.devson.vedinsta.R

class CarouselDotsIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dotCount = 0
    private var selectedPosition = 0

    // Colors
    private val activeColor = ContextCompat.getColor(context, android.R.color.white)
    private val inactiveColor = ContextCompat.getColor(context, android.R.color.white)

    // Dot properties
    private val dotRadius = dpToPx(4f)
    private val activeDotRadius = dpToPx(6f)
    private val dotSpacing = dpToPx(12f)
    private val animationDuration = 300L

    // Paint objects
    private val activePaint = Paint().apply {
        isAntiAlias = true
        color = activeColor
        alpha = 255
    }

    private val inactivePaint = Paint().apply {
        isAntiAlias = true
        color = inactiveColor
        alpha = 120
    }

    // Animation properties
    private var currentRadius = dotRadius
    private var animatedPosition = 0f

    // Animators
    private var positionAnimator: ValueAnimator? = null
    private var radiusAnimator: ValueAnimator? = null

    init {
        // Make view clickable for better touch feedback
        isClickable = false
    }

    fun setDotCount(count: Int) {
        if (dotCount != count) {
            dotCount = count
            selectedPosition = 0
            animatedPosition = 0f
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
                invalidate()
            }
        }
    }

    private fun animateToPosition(fromPosition: Int, toPosition: Int) {
        // Cancel any running animations
        positionAnimator?.cancel()
        radiusAnimator?.cancel()

        // Animate position
        positionAnimator = ValueAnimator.ofFloat(fromPosition.toFloat(), toPosition.toFloat()).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                animatedPosition = animator.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Animate active dot scaling
        radiusAnimator = ValueAnimator.ofFloat(dotRadius, activeDotRadius, dotRadius).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                currentRadius = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = if (dotCount > 0) {
            (dotCount * activeDotRadius * 2 + (dotCount - 1) * dotSpacing).toInt()
        } else {
            0
        }

        val desiredHeight = (activeDotRadius * 2).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dotCount <= 1) return

        val centerY = height / 2f
        val totalWidth = (dotCount * activeDotRadius * 2 + (dotCount - 1) * dotSpacing)
        val startX = (width - totalWidth) / 2f + activeDotRadius

        // Draw inactive dots
        for (i in 0 until dotCount) {
            val x = startX + i * (activeDotRadius * 2 + dotSpacing)

            if (i != selectedPosition) {
                canvas.drawCircle(x, centerY, dotRadius, inactivePaint)
            }
        }

        // Draw active dot with animation
        val activeX = startX + animatedPosition * (activeDotRadius * 2 + dotSpacing)

        // Draw larger active dot
        canvas.drawCircle(activeX, centerY, currentRadius, activePaint)

        // Draw progress trail effect
        drawProgressTrail(canvas, centerY, startX)
    }

    private fun drawProgressTrail(canvas: Canvas, centerY: Float, startX: Float) {
        if (dotCount <= 1) return

        val progress = animatedPosition / (dotCount - 1).coerceAtLeast(1)
        val trailLength = activeDotRadius * 2

        // Create gradient effect for trail
        val trailPaint = Paint().apply {
            isAntiAlias = true
            color = activeColor
            alpha = 60
        }

        // Draw subtle connecting line between dots
        if (selectedPosition > 0) {
            val startTrailX = startX
            val endTrailX = startX + animatedPosition * (activeDotRadius * 2 + dotSpacing)
            canvas.drawLine(startTrailX, centerY, endTrailX, centerY, trailPaint.apply {
                strokeWidth = dpToPx(2f)
                alpha = 40
            })
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        positionAnimator?.cancel()
        radiusAnimator?.cancel()
    }
}