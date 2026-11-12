package com.ashmit.kasturimusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver

/**
 * Sasta "liquid glass": peeche ka content (source) bahut chhote size (1/8) mein capture hota hai
 * aur bada karke dikhaya jaata hai, to frosted blur jaisa lagta hai. Content badalne par hi
 * (aur max ~10 baar/second) dobara capture hota hai, taaki phone par lag na aaye.
 */
class GlassBlurView(ctx: Context, private val source: View, private val tint: Int) : View(ctx) {
    private var bmp: Bitmap? = null
    private val cv = Canvas()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shine = Paint()
    private var shineH = -1
    private val dst = Rect()
    private val loc = IntArray(2)
    private val srcLoc = IntArray(2)
    private var lastCapture = 0L
    private var selfPending = false
    private var vto: ViewTreeObserver? = null
    private val step = 8

    private val trailing = Runnable { requestCapture() }

    private val drawListener = ViewTreeObserver.OnDrawListener {
        if (!selfPending) {
            val now = SystemClock.uptimeMillis()
            if (now - lastCapture > 90L) {
                requestCapture()
            } else {
                removeCallbacks(trailing)
                postDelayed(trailing, 110L)
            }
        }
    }

    private fun requestCapture() {
        selfPending = true
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val o = source.viewTreeObserver
        vto = o
        if (o.isAlive) o.addOnDrawListener(drawListener)
    }

    override fun onDetachedFromWindow() {
        val o = vto
        if (o != null && o.isAlive) o.removeOnDrawListener(drawListener)
        vto = null
        removeCallbacks(trailing)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        selfPending = false
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        capture(w, h)
        val b = bmp
        if (b != null) {
            dst.set(0, 0, w, h)
            canvas.drawBitmap(b, null, dst, paint)
        }
        canvas.drawColor(tint)
        if (shineH != h) {
            shineH = h
            shine.shader = LinearGradient(
                0f, 0f, 0f, h * 0.7f,
                Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shine)
    }

    private fun capture(w: Int, h: Int) {
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
        getLocationInWindow(loc)
        source.getLocationInWindow(srcLoc)
        cv.setBitmap(b)
        b.eraseColor(0)
        cv.save()
        cv.scale(bw.toFloat() / w, bh.toFloat() / h)
        cv.translate((srcLoc[0] - loc[0]).toFloat(), (srcLoc[1] - loc[1]).toFloat())
        try {
            source.draw(cv)
        } catch (e: Exception) {
            // koi view software canvas par draw nahi hua: is baar ka capture chhod do
        }
        cv.restore()
        lastCapture = SystemClock.uptimeMillis()
    }
}
