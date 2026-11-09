package com.ashmit.kasturimusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors

object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>(
        maxOf(2 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 16).toInt())
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failed: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun key(song: Song, px: Int): String =
        if (song.albumId > 0) "a${song.albumId}_$px" else "s${song.id}_$px"

    fun cached(song: Song, px: Int): Bitmap? = cache.get(key(song, px))

    fun load(ctx: Context, song: Song, px: Int, cb: (Bitmap?) -> Unit) {
        val k = key(song, px)
        val hit = cache.get(k)
        if (hit != null) { cb(hit); return }
        if (failed.contains(k)) { cb(null); return }
        val app = ctx.applicationContext
        pool.execute {
            val bmp = decode(app, song, px)
            if (bmp != null) cache.put(k, bmp) else failed.add(k)
            main.post { cb(bmp) }
        }
    }

    /** Bahut chhota (16px) halving se dhundhla bitmap: sasta "blur", RAM/GPU nahi khata. */
    fun loadBlur(ctx: Context, song: Song, cb: (Bitmap?) -> Unit) {
        val k = "b" + key(song, 16)
        val hit = cache.get(k)
        if (hit != null) { cb(hit); return }
        if (failed.contains(k)) { cb(null); return }
        val app = ctx.applicationContext
        pool.execute {
            var out: Bitmap? = null
            try {
                val src = decode(app, song, 96)
                if (src != null) {
                    var b: Bitmap = src
                    while (b.width > 16) {
                        b = Bitmap.createScaledBitmap(b, maxOf(16, b.width / 2), maxOf(16, b.height / 2), true)
                    }
                    out = b
                }
            } catch (e: Exception) { }
            if (out != null) cache.put(k, out) else failed.add(k)
            main.post { cb(out) }
        }
    }

    private fun decode(ctx: Context, song: Song, px: Int): Bitmap? {
        if (song.source != 0) return decodeRemote(song, px)
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return ctx.contentResolver.loadThumbnail(song.uri, Size(px, px), null)
            } catch (e: Exception) {
                // thumbnail nahi mila, embedded art try karenge
            }
        }
        var mmr: MediaMetadataRetriever? = null
        try {
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(ctx, song.uri)
            val data = mmr.embeddedPicture ?: return null
            return decodeBytes(data, px)
        } catch (e: Exception) {
            return null
        } finally {
            try { mmr?.release() } catch (e: Exception) { }
        }
    }

    private fun decodeBytes(data: ByteArray, px: Int): Bitmap? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        o2.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeByteArray(data, 0, data.size, o2)
    }

    /** Online gaane ka cover (URL) ya download kiye gaane ka cover (file). */
    private fun decodeRemote(song: Song, px: Int): Bitmap? {
        val u = song.artUrl ?: return null
        try {
            val data: ByteArray = if (u.startsWith("http")) {
                val c = URL(u).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 8000
                    c.readTimeout = 10000
                    if (c.responseCode != 200) return null
                    c.inputStream.use { it.readBytes() }
                } finally {
                    c.disconnect()
                }
            } else {
                File(u).readBytes()
            }
            return decodeBytes(data, px)
        } catch (e: Exception) {
            return null
        }
    }
}
