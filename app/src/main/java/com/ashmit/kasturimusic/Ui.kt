package com.ashmit.kasturimusic

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView

object Ui {
    val BLACK: Int = Color.BLACK
    val SILVER: Int = Color.parseColor("#C0C0C0")
    val GRAPHITE: Int = Color.parseColor("#1C1C1E")
    val GRAPHITE2: Int = Color.parseColor("#2C2C2E")
    val DIM: Int = Color.parseColor("#8E8E93")

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
    fun bindArt(iv: ImageView, song: Song?, px: Int, padDp: Int = 12) {
        val key = if (song == null) "none" else ArtworkLoader.key(song, px)
        iv.tag = key
        val p = dp(iv.context, padDp)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        iv.setPadding(p, p, p, p)
        iv.setImageResource(R.drawable.ic_note)
        iv.setColorFilter(DIM)
        if (song == null) return
        ArtworkLoader.load(iv.context, song, px) { bmp ->
            if (bmp != null && iv.tag == key) {
                iv.setPadding(0, 0, 0, 0)
                iv.clearColorFilter()
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setImageBitmap(bmp)
            }
        }
    }
}
