package com.ashmit.kasturimusic

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Suggestion(val title: String, val artist: String, val album: String)

/** Gaane ka naam / artist / album sahi karne ke suggestions (MusicBrainz, free). User confirm karta hai. */
object AutoFix {
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** cb main thread par: null = error, khali list = kuch nahi mila. */
    fun suggest(song: Song, cb: (List<Suggestion>?) -> Unit) {
        Thread {
            var out: List<Suggestion>? = null
            try {
                var title = song.title.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").trim()
                if (title.isEmpty()) title = song.title
                var artist = if (song.artist == "Unknown artist") "" else song.artist
                artist = artist.split(",", "&", " feat", " ft.")[0].trim()
                var q = "recording:\"" + title.replace("\"", "") + "\""
                if (artist.isNotEmpty()) q += " AND artist:\"" + artist.replace("\"", "") + "\""
                out = query(q)
                if (out != null && out.isEmpty() && artist.isNotEmpty()) {
                    out = query("recording:\"" + title.replace("\"", "") + "\"")
                }
            } catch (e: Exception) {
                out = null
            }
            val res = out
            Handler(Looper.getMainLooper()).post { cb(res) }
        }.start()
    }

    private fun query(q: String): List<Suggestion>? {
        val url = "https://musicbrainz.org/ws/2/recording?query=" + enc(q) + "&fmt=json&limit=8"
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "KasturiMusicPlayer/1.7 (https://github.com/Ashmit393/KasturiMusic)")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) return null
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(text).optJSONArray("recordings") ?: return ArrayList()
            val seen = HashSet<String>()
            val out = ArrayList<Suggestion>()
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val t = r.optString("title")
                if (t.isBlank()) continue
                val ac = r.optJSONArray("artist-credit")
                val sb = StringBuilder()
                if (ac != null) {
                    for (j in 0 until ac.length()) {
                        val a = ac.optJSONObject(j) ?: continue
                        sb.append(a.optString("name")).append(a.optString("joinphrase"))
                    }
                }
                val artist = sb.toString().trim()
                var album = ""
                val rel = r.optJSONArray("releases")
                if (rel != null && rel.length() > 0) album = rel.optJSONObject(0)?.optString("title") ?: ""
                val key = t.lowercase() + "|" + artist.lowercase()
                if (artist.isNotEmpty() && seen.add(key)) out.add(Suggestion(t, artist, album))
                if (out.size >= 5) break
            }
            return out
        } finally {
            c.disconnect()
        }
    }
}
