package id.friss.assist

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

class AnimatedBlueBackground : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 14000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidateSelf() }
    }
    init { animator.start() }
    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(Color.rgb(241, 247, 255))
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        glow(canvas, w * (0.16f + 0.13f * kotlin.math.sin(phase * 6.283f)), h * 0.13f, w * 0.68f, 0x553B91FF, 0x003B91FF)
        glow(canvas, w * (0.86f - 0.12f * kotlin.math.sin(phase * 6.283f + 1.2f)), h * 0.55f, w * 0.74f, 0x333A7BFF, 0x003A7BFF)
        glow(canvas, w * 0.25f, h * (0.88f - 0.05f * kotlin.math.sin(phase * 6.283f)), w * 0.55f, 0x223DAEFF, 0x003DAEFF)
    }
    private fun glow(c: Canvas, x: Float, y: Float, radius: Float, center: Int, edge: Int) {
        paint.shader = RadialGradient(x, y, radius, center, edge, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, radius, paint)
        paint.shader = null
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
