package com.ashmit.kasturimusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.View

/**
 * Liquid glass: peeche ka content (source) is view mein aata hai aur dhundhla hota hai.
 * - compat = true (default): chhota capture + smooth box-blur + bada karke dikhana. Har phone par chalta hai.
 * - compat = false (Android 12+): GPU RenderEffect blur (zyada smooth, par kuch phones par theek na chale).
 * Redraw sirf tab hota hai jab content badle (scroll/animation): touch() se kuch der har frame update hota hai.
 */
class GlassBlurView(
    ctx: Context,
    private val source: View,
    private val tint: Int,
    compat: Boolean
) : View(ctx) {
    private var gpu = Build.VERSION.SDK_INT >= 31 && !compat
    private val shine = Paint()
    private var shineH = -1
    private val loc = IntArray(2)
    private val srcLoc = IntArray(2)
    private var activeUntil = 0L
    private var looping = false

    private var bmp: Bitmap? = null
    private val cv = Canvas()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()
    private var lastCapture = 0L
    private val step = 8

    private val loop = object : Runnable {
        override fun run() {
            invalidate()
            if (SystemClock.uptimeMillis() < activeUntil) {
                postOnAnimation(this)
            } else {
                looping = false
            }
        }
    }

    init {
        val sat = ColorMatrix()
        sat.setSaturation(1.7f)
        paint.colorFilter = ColorMatrixColorFilter(sat)
        if (Build.VERSION.SDK_INT >= 31 && !compat) {
            val r = Ui.dp(ctx, 18).toFloat()
            val blur = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
            setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(sat), blur))
        }
    }

    fun touch(durationMs: Long = 350L) {
        activeUntil = maxOf(activeUntil, SystemClock.uptimeMillis() + durationMs)
        if (!looping) {
            looping = true
            postOnAnimation(loop)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        touch(600L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(loop)
        looping = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        getLocationInWindow(loc)
        source.getLocationInWindow(srcLoc)
        val dx = (srcLoc[0] - loc[0]).toFloat()
        val dy = (srcLoc[1] - loc[1]).toFloat()
        var drawn = false
        if (gpu && canvas.isHardwareAccelerated) {
            canvas.save()
            canvas.clipRect(0f, 0f, w.toFloat(), h.toFloat())
            canvas.translate(dx, dy)
            try {
                source.draw(canvas)
                drawn = true
            } catch (e: Exception) {
                gpu = false // GPU tareeka fail: compat par chale jao
            }
            canvas.restore()
        }
        if (!drawn) drawCompat(canvas, w, h, dx, dy)
        canvas.drawColor(tint)
        if (shineH != h) {
            shineH = h
            shine.shader = LinearGradient(
                0f, 0f, 0f, h * 0.7f,
                Color.argb(55, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shine)
    }

    private fun drawCompat(canvas: Canvas, w: Int, h: Int, dx: Float, dy: Float) {
        val bw = maxOf(4, w / step)
        val bh = maxOf(4, h / step)
        val cur = bmp
        val b: Bitmap = if (cur != null && cur.width == bw && cur.height == bh) {
            cur
        } else {
            val nb = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            bmp = nb
            lastCapture = 0L
            nb
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastCapture > 50L) {
            cv.setBitmap(b)
            b.eraseColor(0)
            cv.save()
            cv.scale(bw.toFloat() / w, bh.toFloat() / h)
            cv.translate(dx, dy)
            try {
                source.draw(cv)
            } catch (e: Exception) {
                // koi view software canvas par draw nahi hua
            }
            cv.restore()
            boxBlur(b, 2)
            boxBlur(b, 2)
            lastCapture = now
        }
        dst.set(0, 0, w, h)
        canvas.drawBitmap(b, null, dst, paint)
    }

    /** Chhote bitmap par saaf box blur (do pass): upscale karne par blocks nahi dikhte. */
    private fun boxBlur(b: Bitmap, r: Int) {
        val w = b.width
        val h = b.height
        val a = IntArray(w * h)
        val t = IntArray(w * h)
        b.getPixels(a, 0, w, 0, 0, w, h)
        pass(a, t, w, h, r, true)
        pass(t, a, w, h, r, false)
        b.setPixels(a, 0, w, 0, 0, w, h)
    }

    private fun pass(src: IntArray, out: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (l in 0 until lines) {
            for (i in 0 until len) {
                var sa = 0
                var sr = 0
                var sg = 0
                var sb = 0
                var n = 0
                for (k in -r..r) {
                    val j = i + k
                    if (j < 0 || j >= len) continue
                    val c = if (horizontal) src[l * w + j] else src[j * w + l]
                    sa += c ushr 24
                    sr += (c shr 16) and 255
                    sg += (c shr 8) and 255
                    sb += c and 255
                    n++
                }
                val v = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                if (horizontal) out[l * w + i] = v else out[i * w + l] = v
            }
        }
    }
}
