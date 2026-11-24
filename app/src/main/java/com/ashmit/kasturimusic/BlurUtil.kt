package com.ashmit.kasturimusic

import android.graphics.Bitmap

/** Chhote bitmap par saaf box blur (do pass). Bitmap bahut chhota hona chahiye (<= 64px) taaki tez rahe. */
object BlurUtil {
    fun boxBlur(b: Bitmap, r: Int) {
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
