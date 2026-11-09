package com.nova.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ColorWheelView(ctx: Context) : View(ctx) {
    var hue = 0f
    var sat = 1f
    var onPick: ((Float, Float) -> Unit)? = null
    var onDone: (() -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    private var cx = 0f
    private var cy = 0f
    private var r = 1f
    private var sweep: Shader? = null
    private var radial: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f; cy = h / 2f
        r = minOf(w, h) / 2f - 10 * d
        val colors = IntArray(361) { Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f)) }
        sweep = SweepGradient(cx, cy, colors, null)
        radial = RadialGradient(cx, cy, r, Color.WHITE, Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.FILL
        p.shader = sweep
        c.drawCircle(cx, cy, r, p)
        p.shader = radial
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2 * d
        p.color = Color.argb(90, 255, 255, 255)
        c.drawCircle(cx, cy, r, p)
        val a = Math.toRadians(hue.toDouble())
        val sx = cx + (cos(a) * sat * r).toFloat()
        val sy = cy + (sin(a) * sat * r).toFloat()
        p.style = Paint.Style.FILL
        p.color = Color.HSVToColor(floatArrayOf(hue, sat, 1f))
        c.drawCircle(sx, sy, 10 * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3 * d
        p.color = Color.WHITE
        c.drawCircle(sx, sy, 10 * d, p)
        p.strokeWidth = 1.5f * d
        p.color = Color.argb(160, 0, 0, 0)
        c.drawCircle(sx, sy, 12 * d, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val dx = e.x - cx
                val dy = e.y - cy
                sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                var ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (ang < 0) ang += 360f
                hue = ang
                invalidate()
                onPick?.invoke(hue, sat)
            }
            MotionEvent.ACTION_UP -> onDone?.invoke()
        }
        return true
    }
}

class ValueBarView(ctx: Context) : View(ctx) {
    var value = 0.8f
    var baseHue = 0f
    var baseSat = 0f
    var onPick: ((Float) -> Unit)? = null
    var onDone: (() -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    private val pad get() = 14 * d

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bh = minOf(h * 0.5f, 26 * d)
        val rect = RectF(pad, h / 2 - bh / 2, w - pad, h / 2 + bh / 2)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(rect.left, 0f, rect.right, 0f, Color.BLACK,
            Color.HSVToColor(floatArrayOf(baseHue, baseSat, 1f)), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, bh / 2, bh / 2, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * d
        p.color = Color.argb(110, 255, 255, 255)
        c.drawRoundRect(rect, bh / 2, bh / 2, p)
        val x = rect.left + (value - 0.2f) / 0.8f * rect.width()
        p.style = Paint.Style.FILL
        p.color = Color.HSVToColor(floatArrayOf(baseHue, baseSat, value))
        c.drawCircle(x, h / 2, bh * 0.72f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3 * d
        p.color = Color.WHITE
        c.drawCircle(x, h / 2, bh * 0.72f, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val w = width - 2 * pad
                value = (0.2f + ((e.x - pad) / w).coerceIn(0f, 1f) * 0.8f)
                invalidate()
                onPick?.invoke(value)
            }
            MotionEvent.ACTION_UP -> onDone?.invoke()
        }
        return true
    }
}

/** Lightweight key background: no allocations when recolored (used by the rainbow animation). */
class KeyDrawable(
    private val radius: Float,
    private val inset: Float,
    private val strokeW: Float,
    private val strokeColor: Int,
    private val glowW: Float
) : android.graphics.drawable.Drawable() {
    var face = 0
    var shadow = 0
    var glow = 0f
    var glowColor = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun setColors(f: Int, s: Int) {
        if (f != face || s != shadow) { face = f; shadow = s; invalidateSelf() }
    }

    fun setGlow(g: Float, col: Int) {
        if (kotlin.math.abs(g - glow) > 0.01f || (g > 0f && col != glowColor)) {
            glow = g; glowColor = col; invalidateSelf()
        }
    }

    override fun draw(c: Canvas) {
        val b = bounds
        r.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        p.style = Paint.Style.FILL
        p.color = shadow
        c.drawRoundRect(r, radius, radius, p)
        r.bottom -= inset
        p.color = face
        c.drawRoundRect(r, radius, radius, p)
        if (glow > 0.03f) {
            val a = (glow * 150f).toInt().coerceIn(0, 255)
            p.color = (glowColor and 0x00FFFFFF) or (a shl 24)
            val ix = r.width() * 0.12f
            val iy = r.height() * 0.16f
            r.inset(ix, iy)
            c.drawRoundRect(r, radius, radius, p)
            r.inset(-ix, -iy)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = strokeW
        p.color = strokeColor
        r.inset(strokeW / 2f, strokeW / 2f)
        c.drawRoundRect(r, radius, radius, p)
        if (glow > 0.05f) {
            val a = (glow * 255f).toInt().coerceIn(0, 255)
            p.strokeWidth = glowW
            p.color = (glowColor and 0x00FFFFFF) or (a shl 24)
            r.inset(glowW / 2f, glowW / 2f)
            c.drawRoundRect(r, radius, radius, p)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
