package com.ashmit.kasturimusic

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ek gaane ki lyrics. synced = har line ka time (ms) maloom hai. */
class Lyrics(val times: LongArray, val lines: List<String>, val synced: Boolean) {
    /** pos (ms) par kaunsi line chal rahi hai. -1 = pehli line abhi shuru nahi hui. */
    fun indexAt(pos: Long): Int {
        var lo = 0
        var hi = times.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= pos) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

object LyricsManager {
    private val TIME = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val TAG = Regex("\\[[a-zA-Z]+:.*]")

    private fun file(ctx: Context, song: Song): File {
        val dir = File(ctx.filesDir, "lyrics")
        dir.mkdirs()
        return File(dir, song.id.toString() + ".txt")
    }

    fun loadRaw(ctx: Context, song: Song): String? {
        val f = file(ctx, song)
        return try { if (f.exists()) f.readText() else null } catch (e: Exception) { null }
    }

    fun load(ctx: Context, song: Song): Lyrics? {
        val raw = loadRaw(ctx, song) ?: return null
        return parse(raw)
    }

    fun save(ctx: Context, song: Song, text: String) {
        try { file(ctx, song).writeText(text) } catch (e: Exception) { }
    }

    fun delete(ctx: Context, song: Song) {
        try { file(ctx, song).delete() } catch (e: Exception) { }
    }

    /** LRC (time ke saath) ya plain text dono chalte hain. */
    fun parse(raw: String): Lyrics? {
        val entries = ArrayList<Pair<Long, String>>()
        val plain = ArrayList<String>()
        for (line in raw.lines()) {
            val matches = TIME.findAll(line).toList()
            if (matches.isEmpty()) {
                val t = line.trim()
                if (t.isNotEmpty() && !TAG.matches(t)) plain.add(t)
            } else {
                var text = TIME.replace(line, "").trim()
                if (text.isEmpty()) text = "\u2022 \u2022 \u2022"
                for (m in matches) {
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
                    entries.add(Pair(min * 60000L + sec * 1000L + ms, text))
                }
            }
        }
        if (entries.isNotEmpty()) {
            entries.sortBy { it.first }
            return Lyrics(LongArray(entries.size) { entries[it].first }, entries.map { it.second }, true)
        }
        if (plain.isNotEmpty()) return Lyrics(LongArray(0), plain, false)
        return null
    }

    // ---------------- online (LRCLIB, free) ----------------
    /** Background thread mein dhoondta hai, mil jaye to phone mein save karta hai. cb main thread par chalta hai. */
    fun fetchOnline(ctx: Context, song: Song, cb: (String?) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            var raw: String? = null
            try { raw = search(song) } catch (e: Exception) { }
            if (raw != null) save(app, song, raw)
            Handler(Looper.getMainLooper()).post { cb(raw) }
        }.start()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun search(song: Song): String? {
        val dur = song.durationMs / 1000
        var title = song.title.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").trim()
        if (title.isEmpty()) title = song.title
        val artist = if (song.artist == "Unknown artist") "" else song.artist
        var url1 = "https://lrclib.net/api/search?track_name=" + enc(title)
        if (artist.isNotEmpty()) url1 += "&artist_name=" + enc(artist)
        val a = pick(getJson(url1), dur, 6)
        if (a != null) return a
        val url2 = "https://lrclib.net/api/search?q=" + enc((artist + " " + title).trim())
        return pick(getJson(url2), dur, 10)
    }

    private fun getJson(url: String): JSONArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "KasturiMusicPlayer/1.1 (https://github.com/Ashmit393/KasturiMusic)")
            if (c.responseCode != 200) return null
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return JSONArray(text)
        } finally {
            c.disconnect()
        }
    }

    private fun str(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "")

    private fun pick(arr: JSONArray?, dur: Long, tol: Int): String? {
        if (arr == null) return null
        var best: String? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("instrumental", false)) continue
            val synced = str(o, "syncedLyrics")
            val plain = str(o, "plainLyrics")
            val text = if (synced.isNotBlank()) synced else plain
            if (text.isBlank()) continue
            val diff = Math.abs(o.optDouble("duration", -1000.0) - dur)
            if (diff > tol) continue
            val score = diff + (if (synced.isBlank()) 100.0 else 0.0)
            if (score < bestScore) { bestScore = score; best = text }
        }
        return best
    }
}
