package com.ashmit.kasturimusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File

/** User ki apni photo ko background banata hai (blur ke saath). Photo app ke andar copy hoti hai, koi permission nahi chahiye. */
object PhotoBg {
    private const val FILE = "bg_photo.jpg"
    private var cached: Bitmap? = null
    private var cachedBlur = -1

    private fun file(ctx: Context): File = File(ctx.filesDir, FILE)

    fun has(ctx: Context): Boolean = file(ctx).exists()

    fun scrimColor(): Int {
        val a = (SettingsManager.bgDim.coerceIn(0, 100) * 255) / 100
        return if (Theme.isLight) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
    }

    /** Photo chuni hui photo ko max 1280px mein chhota karke save karta hai. */
    fun save(ctx: Context, uri: Uri): Boolean {
        try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1280) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?: return false
            val m = maxOf(bmp.width, bmp.height)
            val out = if (m > 1280) Bitmap.createScaledBitmap(bmp, bmp.width * 1280 / m, bmp.height * 1280 / m, true) else bmp
            file(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            cached = null
            cachedBlur = -1
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun remove(ctx: Context) {
        try { file(ctx).delete() } catch (e: Exception) { }
        cached = null
        cachedBlur = -1
    }

    /** blur 0..100. Sasta blur: bitmap ko chhota karke bada dikhate hain (RAM aur GPU kam). */
    fun bitmap(ctx: Context, blur: Int): Bitmap? {
        if (!has(ctx)) return null
        val level = blur.coerceIn(0, 100)
        val c = cached
        if (c != null && cachedBlur == level) return c
        try {
            var b = BitmapFactory.decodeFile(file(ctx).absolutePath) ?: return null
            val t = level / 100f
            val target = (720f * (1f - t) + 20f * t).toInt().coerceAtLeast(20)
            while (b.width / 2 >= target && b.width > 40) {
                b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
            }
            if (b.width > target) {
                b = Bitmap.createScaledBitmap(b, target, maxOf(1, target * b.height / b.width), true)
            }
            cached = b
            cachedBlur = level
            return b
        } catch (e: Exception) {
            return null
        }
    }
}

/** Background photo + dimming ke saath content ko lapetne wala frame. */
class PhotoFrame(ctx: Context, content: View) : FrameLayout(ctx) {
    private val img = ImageView(ctx)
    private val scrim = View(ctx)

    init {
        setBackgroundColor(Theme.bg)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(img, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(scrim, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        refresh()
    }

    fun refresh() {
        val b = PhotoBg.bitmap(context, SettingsManager.bgBlur)
        if (b == null) {
            img.visibility = View.GONE
            scrim.visibility = View.GONE
        } else {
            img.setImageBitmap(b)
            img.visibility = View.VISIBLE
            scrim.setBackgroundColor(PhotoBg.scrimColor())
            scrim.visibility = View.VISIBLE
        }
    }

    fun updateScrim() {
        scrim.setBackgroundColor(PhotoBg.scrimColor())
    }
}
