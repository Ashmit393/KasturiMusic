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
 * Liquid glass: peeche ka content (source) is view ke andar draw hota hai aur GPU blur lagta hai.
 * - Android 12+ (aapka phone): RenderEffect = smooth asli blur, CPU kam.
 * - Purane Android: bahut chhota capture (fallback).
 * Ye sirf tab redraw hota hai jab content badle (scroll / animation): touch() call karne par
 * kuch der har frame update hota hai, phir ruk jaata hai (battery aur lag bachane ke liye).
 */
class GlassBlurView(ctx: Context, private val source: View, private val tint: Int) : View(ctx) {
    private val gpu = Build.VERSION.SDK_INT >= 31
    private val shine = Paint()
    private var shineH = -1
    private val loc = IntArray(2)
    private val srcLoc = IntArray(2)
    private var activeUntil = 0L
    private var looping = false

    // fallback (Android < 12)
    private var bmp: Bitmap? = null
    private val cv = Canvas()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()
    private var lastCapture = 0L
    private val step = 6

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
        if (Build.VERSION.SDK_INT >= 31) {
            val r = Ui.dp(ctx, 14).toFloat()
            val blur = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
            val sat = ColorMatrix()
            sat.setSaturation(1.6f)
            setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(sat), blur))
        }
    }

    /** Content badla hai: kuch der har frame redraw karo. */
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
        if (gpu && canvas.isHardwareAccelerated) {
            canvas.save()
            canvas.clipRect(0f, 0f, w.toFloat(), h.toFloat())
            canvas.translate(dx, dy)
            try {
                source.draw(canvas)
            } catch (e: Exception) {
                // is baar ka frame chhod do
            }
            canvas.restore()
        } else {
            drawFallback(canvas, w, h, dx, dy)
        }
        canvas.drawColor(tint)
        if (shineH != h) {
            shineH = h
            shine.shader = LinearGradient(
                0f, 0f, 0f, h * 0.6f,
                Color.argb(45, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shine)
    }

    private fun drawFallback(canvas: Canvas, w: Int, h: Int, dx: Float, dy: Float) {
        val bw = maxOf(2, w / step)
        val bh = maxOf(2, h / step)
        val cur = bmp
        val b: Bitmap = if (cur != null && cur.width == bw && cur.height == bh) {
            cur
        } else {
            val nb = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            bmp = nb
            nb
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastCapture > 60L) {
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
            lastCapture = now
        }
        dst.set(0, 0, w, h)
        canvas.drawBitmap(b, null, dst, paint)
    }
}
