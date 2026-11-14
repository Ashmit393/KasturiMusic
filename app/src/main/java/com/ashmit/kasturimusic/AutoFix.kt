package com.ashmit.kasturimusic

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Suggestion(
    val title: String,
    val artist: String,
    val album: String,
    val score: Int = 0,
    val releaseId: String = ""
)

/** Gaane ka naam / artist / album sahi karne ke liye MusicBrainz (free) se suggestions. */
object AutoFix {
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun cleanTitle(t: String): String {
        var title = t.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), " ")
        title = title.replace(Regex("(?i)www\\.[^\\s]+"), " ")
        title = title.replace('_', ' ')
        title = title.replace(Regex("^\\s*\\d{1,3}\\s*[.\\-_]+\\s*"), "")
        title = title.replace(Regex("\\s+"), " ").trim()
        return if (title.isEmpty()) t.trim() else title
    }

    fun firstArtist(a: String): String {
        if (a == "Unknown artist") return ""
        return a.split(",", "&", " feat", " ft.", " x ")[0].trim()
    }

    /** Background thread par chalao (blocking). null = internet/server error, khali = kuch nahi mila. */
    fun lookup(song: Song): List<Suggestion>? {
        try {
            val title = cleanTitle(song.title).replace("\"", "")
            val artist = firstArtist(song.artist).replace("\"", "")
            var q = "recording:\"$title\""
            if (artist.isNotEmpty()) q += " AND artist:\"$artist\""
            var out = query(q) ?: return null
            if (out.isEmpty() && artist.isNotEmpty()) {
                Thread.sleep(1100)
                out = query("recording:\"$title\"") ?: return null
            }
            return out
        } catch (e: Exception) {
            return null
        }
    }

    /** cb main thread par. */
    fun suggest(song: Song, cb: (List<Suggestion>?) -> Unit) {
        Thread {
            val res = lookup(song)
            Handler(Looper.getMainLooper()).post { cb(res) }
        }.start()
    }

    private fun query(q: String): List<Suggestion>? {
        val url = "https://musicbrainz.org/ws/2/recording?query=" + enc(q) + "&fmt=json&limit=8"
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "KasturiMusicPlayer/1.8 (https://github.com/Ashmit393/KasturiMusic)")
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
                var releaseId = ""
                val rel = r.optJSONArray("releases")
                if (rel != null && rel.length() > 0) {
                    val r0 = rel.optJSONObject(0)
                    if (r0 != null) {
                        album = r0.optString("title")
                        releaseId = r0.optString("id")
                    }
                }
                val key = t.lowercase() + "|" + artist.lowercase()
                if (artist.isNotEmpty() && seen.add(key)) out.add(Suggestion(t, artist, album, r.optInt("score", 0), releaseId))
                if (out.size >= 5) break
            }
            return out
        } finally {
            c.disconnect()
        }
    }
}
