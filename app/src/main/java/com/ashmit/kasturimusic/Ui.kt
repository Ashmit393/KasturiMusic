package com.ashmit.kasturimusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView

object Ui {
    val BLACK: Int = Color.BLACK
    val SILVER: Int = Color.parseColor("#C0C0C0")
    val GRAPHITE: Int = Color.parseColor("#1C1C1E")
    val GRAPHITE2: Int = Color.parseColor("#2C2C2E")
    val DIM: Int = Color.parseColor("#8E8E93")

    /** Koi artwork/blur load hua: glass views ko redraw karne ka signal. */
    var contentChanged: (() -> Unit)? = null

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    fun roundRect(color: Int, radius: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius
        return g
    }

    fun round(v: View, radius: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        v.clipToOutline = true
    }

    fun iconButton(ctx: Context, res: Int, tint: Int, padDp: Int = 10): ImageButton {
        val b = ImageButton(ctx)
        b.setImageResource(res)
        b.setColorFilter(tint)
        b.background = null
        b.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val p = dp(ctx, padDp)
        b.setPadding(p, p, p, p)
        return b
    }

    /** Album art laga deta hai (cache + background thread). px = bitmap size. */
    fun bindArt(iv: ImageView, song: Song?, px: Int, padDp: Int = 12, loadIfMissing: Boolean = true) {
        val key = if (song == null) "none" else ArtworkLoader.key(song, px)
        iv.tag = key
        val p = dp(iv.context, padDp)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        iv.setPadding(p, p, p, p)
        iv.setImageResource(R.drawable.ic_note)
        iv.setColorFilter(DIM)
        if (song == null) return
        if (!loadIfMissing && ArtworkLoader.cached(song, px) == null) return
        ArtworkLoader.load(iv.context, song, px) { bmp ->
            if (bmp != null && iv.tag == key) {
                iv.setPadding(0, 0, 0, 0)
                iv.clearColorFilter()
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setImageBitmap(bmp)
                contentChanged?.invoke()
            }
        }
    }

    /** Dabane par halka sa dab jaata hai aur chhodne par bounce karta hai. Click ko nahi rokta. */
    fun pressable(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90)
                        .setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(OvershootInterpolator(2.2f)).start()
            }
            false
        }
    }

    /**
     * Album art ka dhundhla version background mein, crossfade ke saath.
     * Android 12+: GPU blur (smooth, achha dikhta hai). Purane Android: chhota bitmap upscale.
     */
    fun bindBlur(iv: ImageView, song: Song?) {
        val key = "blur" + (song?.id ?: -1L)
        iv.tag = key
        if (song == null) {
            iv.setImageDrawable(null)
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val r = 30f
            val blur = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
            val sat = ColorMatrix()
            sat.setSaturation(1.4f)
            iv.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(sat), blur))
        }
        val done: (Bitmap?) -> Unit = { bmp ->
            if (iv.tag == key) {
                if (bmp == null) {
                    iv.setImageDrawable(null)
                } else {
                    var old: Drawable? = iv.drawable
                    if (old is TransitionDrawable) old = old.getDrawable(1)
                    val td = TransitionDrawable(
                        arrayOf(old ?: ColorDrawable(Color.TRANSPARENT), BitmapDrawable(iv.resources, bmp))
                    )
                    td.isCrossFadeEnabled = true
                    iv.setImageDrawable(td)
                    td.startTransition(500)
                }
                contentChanged?.invoke()
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            ArtworkLoader.load(iv.context, song, 160, done)
        } else {
            ArtworkLoader.loadBlur(iv.context, song, done)
        }
    }
}

/** Radius badalne wala rounded outline (animation ke liye). */
class RoundOutline(var radius: Float) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}
