package com.ashmit.kasturimusic

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Jamendo (Creative Commons music, free API). Client ID Settings mein user khud daalta hai. */
object JamendoManager {
    val categories = listOf("Popular", "Trending", "Hindi", "Pop", "Rock", "Chill", "Electronic", "Hip-hop", "Acoustic")

    private val cache = HashMap<String, Pair<Long, List<Song>>>()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun buildUrl(clientId: String, query: String, cat: Int): String {
        val sb = StringBuilder("https://api.jamendo.com/v3.0/tracks/?client_id=")
        sb.append(enc(clientId)).append("&format=json&limit=40&audioformat=mp31&imagesize=300")
        if (query.isNotEmpty()) {
            sb.append("&search=").append(enc(query))
        } else {
            when (cat) {
                0 -> sb.append("&order=popularity_week")
                1 -> sb.append("&order=popularity_month")
                2 -> sb.append("&search=hindi&order=popularity_total")
                3 -> sb.append("&tags=pop&order=popularity_week")
                4 -> sb.append("&tags=rock&order=popularity_week")
                5 -> sb.append("&tags=chillout&order=popularity_week")
                6 -> sb.append("&tags=electronic&order=popularity_week")
                7 -> sb.append("&tags=hiphop&order=popularity_week")
                else -> sb.append("&tags=acoustic&order=popularity_week")
            }
        }
        return sb.toString()
    }

    /** Background thread mein; cb main thread par (list, error). Results 10 minute cache hote hain (API requests bachane ke liye). */
    fun fetch(clientId: String, query: String, cat: Int, cb: (List<Song>?, String?) -> Unit) {
        val url = buildUrl(clientId, query, cat)
        val hit = synchronized(cache) { cache[url] }
        if (hit != null && System.currentTimeMillis() - hit.first < 10 * 60_000L) {
            cb(hit.second, null)
            return
        }
        Thread {
            var list: List<Song>? = null
            var err: String? = null
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 8000
                    c.readTimeout = 12000
                    c.setRequestProperty("User-Agent", "KasturiMusicPlayer/1.7 (https://github.com/Ashmit393/KasturiMusic)")
                    if (c.responseCode == 200) {
                        val text = c.inputStream.bufferedReader().use { it.readText() }
                        val o = JSONObject(text)
                        val h = o.optJSONObject("headers")
                        if (h != null && h.optString("status") != "success") {
                            val em = h.optString("error_message")
                            err = if (em.isBlank()) "Jamendo error" else em
                        } else {
                            list = parse(o)
                        }
                    } else {
                        err = "Jamendo ne jawab nahi diya (code " + c.responseCode + ")"
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                err = "Internet check karo"
            }
            val res = list
            val er = err
            if (res != null) synchronized(cache) { cache[url] = Pair(System.currentTimeMillis(), res) }
            Handler(Looper.getMainLooper()).post { cb(res, er) }
        }.start()
    }

    private fun parse(o: JSONObject): List<Song> {
        val out = ArrayList<Song>()
        val arr = o.optJSONArray("results") ?: return out
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val id = t.optString("id").toLongOrNull() ?: continue
            val audio = t.optString("audio")
            if (audio.isBlank()) continue
            val allowed = t.optBoolean("audiodownload_allowed", false)
            val dl = t.optString("audiodownload")
            val image = t.optString("image")
            out.add(
                Song(
                    -id,
                    t.optString("name", "Unknown"),
                    t.optString("artist_name", "Unknown artist"),
                    t.optString("album_name", ""),
                    0L,
                    t.optLong("duration", 0L) * 1000L,
                    "",
                    1,
                    audio,
                    if (image.isBlank()) null else image,
                    if (allowed && dl.isNotBlank()) dl else null
                )
            )
        }
        return out
    }
}
