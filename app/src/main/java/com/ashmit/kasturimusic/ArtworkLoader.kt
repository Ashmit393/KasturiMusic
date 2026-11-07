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

    private fun decode(ctx: Context, song: Song, px: Int): Bitmap? {
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
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(data, 0, data.size, o)
            var s = 1
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            o2.inPreferredConfig = Bitmap.Config.RGB_565
            return BitmapFactory.decodeByteArray(data, 0, data.size, o2)
        } catch (e: Exception) {
            return null
        } finally {
            try { mmr?.release() } catch (e: Exception) { }
        }
    }
}
