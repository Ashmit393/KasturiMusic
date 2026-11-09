package com.ashmit.kasturimusic

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/** Download kiye hue gaane app ke andar rakhta hai (offline chalte hain). Sirf wahi jinhe artist ne allow kiya ho. */
object DownloadsManager {
    private lateinit var p: SharedPreferences
    private val items = ArrayList<Song>()
    private val inProgress: MutableSet<Long> = Collections.synchronizedSet(HashSet<Long>())

    fun init(ctx: Context) {
        if (::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("kasturi_downloads", Context.MODE_PRIVATE)
        try {
            val arr = JSONArray(p.getString("data", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.getString("path")
                if (!File(path).exists()) continue
                items.add(
                    Song(
                        o.getLong("id"), o.getString("title"), o.getString("artist"), o.getString("album"),
                        0L, o.getLong("dur"), path, 2, null,
                        if (o.isNull("art")) null else o.getString("art"), null
                    )
                )
            }
        } catch (e: Exception) { }
    }

    private fun save() {
        val arr = JSONArray()
        synchronized(items) {
            for (s in items) {
                val o = JSONObject()
                o.put("id", s.id)
                o.put("title", s.title)
                o.put("artist", s.artist)
                o.put("album", s.album)
                o.put("dur", s.durationMs)
                o.put("path", s.path)
                o.put("art", s.artUrl ?: JSONObject.NULL)
                arr.put(o)
            }
        }
        p.edit().putString("data", arr.toString()).apply()
    }

    fun songs(): List<Song> = synchronized(items) { ArrayList(items) }

    fun isDownloaded(id: Long): Boolean = synchronized(items) { items.any { it.id == id } }

    private fun fetchTo(url: String, dest: File) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 25000
            c.setRequestProperty("User-Agent", "KasturiMusicPlayer/1.7 (https://github.com/Ashmit393/KasturiMusic)")
            if (c.responseCode != 200) throw Exception("HTTP " + c.responseCode)
            c.inputStream.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
        } finally {
            c.disconnect()
        }
    }

    /** cb main thread par (success, message). */
    fun download(ctx: Context, s: Song, cb: (Boolean, String) -> Unit) {
        val url = s.downloadUrl
        if (url == null) {
            cb(false, "Is gaane ka download allowed nahi hai")
            return
        }
        if (!inProgress.add(s.id)) {
            cb(false, "Ye download pehle se chal raha hai")
            return
        }
        val app = ctx.applicationContext
        Thread {
            var ok = false
            var msg = ""
            try {
                val dir = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: app.filesDir
                dir.mkdirs()
                val base = "jamendo_" + (-s.id)
                val f = File(dir, "$base.mp3")
                val tmp = File(dir, "$base.part")
                fetchTo(url, tmp)
                if (f.exists()) f.delete()
                tmp.renameTo(f)
                var art: String? = null
                val au = s.artUrl
                if (au != null && au.startsWith("http")) {
                    try {
                        val af = File(dir, "$base.jpg")
                        fetchTo(au, af)
                        art = af.absolutePath
                    } catch (e: Exception) { }
                }
                val ds = s.copy(source = 2, streamUrl = null, downloadUrl = null, path = f.absolutePath, artUrl = art)
                synchronized(items) {
                    items.removeAll { it.id == ds.id }
                    items.add(0, ds)
                }
                save()
                ok = true
                msg = "Download ho gaya: " + s.title
            } catch (e: Exception) {
                msg = "Download fail hua, internet check karo"
            }
            inProgress.remove(s.id)
            val okF = ok
            val msgF = msg
            Handler(Looper.getMainLooper()).post { cb(okF, msgF) }
        }.start()
    }

    fun delete(id: Long) {
        synchronized(items) {
            val s = items.firstOrNull { it.id == id }
            if (s != null) {
                try { File(s.path).delete() } catch (e: Exception) { }
                val art = s.artUrl
                if (art != null && !art.startsWith("http")) {
                    try { File(art).delete() } catch (e: Exception) { }
                }
                items.remove(s)
            }
        }
        save()
    }
}
