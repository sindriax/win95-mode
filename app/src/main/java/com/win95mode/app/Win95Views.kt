package com.win95mode.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.res.ResourcesCompat
import kotlin.math.roundToInt

/** The segmented blue Win95 progress bar in a sunken gray track. */
class BlockProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val block = Paint().apply { color = context.getColor(R.color.win95_navy) }
    private val light = Paint().apply { color = context.getColor(R.color.white) }
    private val dark = Paint().apply { color = context.getColor(R.color.win95_dark_gray) }
    private val fill = Paint().apply { color = context.getColor(R.color.win95_gray) }
    private var animator: ValueAnimator? = null

    /** 0..1 */
    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            contentDescription = "${(field * 100).roundToInt()}%"
            invalidate()
        }

    /** Fills from empty to [to] over [ms], then calls [done]. */
    fun animateTo(to: Float, ms: Long, done: () -> Unit = {}) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, to).apply {
            duration = ms
            addUpdateListener { progress = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = done()
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val edge = density
        canvas.drawRect(0f, 0f, w, h, fill)
        canvas.drawRect(0f, 0f, w, edge, dark)
        canvas.drawRect(0f, 0f, edge, h, dark)
        canvas.drawRect(0f, h - edge, w, h, light)
        canvas.drawRect(w - edge, 0f, w, h, light)

        val pad = 3 * density
        val blockW = 10 * density
        val gap = 2 * density
        val inner = w - 2 * pad
        val filled = inner * progress
        var x = pad
        while (x + blockW <= pad + filled + 0.5f && x + blockW <= w - pad + 0.5f) {
            canvas.drawRect(x, pad, x + blockW, h - pad, block)
            x += blockW + gap
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.ProgressBar::class.java.name
    }
}

/** The dark vertical strip on the left of the Start menu, with the name written upwards. */
class StartBanner @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val background = Paint().apply { color = context.getColor(R.color.win95_dark_gray) }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.win95_gray)
        typeface = ResourcesCompat.getFont(context, R.font.ms_sans_serif_bold) ?: Typeface.DEFAULT_BOLD
        textSize = 20 * resources.displayMetrics.scaledDensity
    }
    private val white = Paint(bold).apply { color = context.getColor(R.color.white) }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        canvas.save()
        canvas.rotate(-90f)
        val baseline = width * 0.72f
        val first = "Win95"
        canvas.drawText(first, -height + 10 * resources.displayMetrics.density, baseline, white)
        canvas.drawText(" Mode", -height + 10 * resources.displayMetrics.density + white.measureText(first), baseline, bold)
        canvas.restore()
    }
}
