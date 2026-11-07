package com.ashmit.kasturimusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gaane ka naam / artist / album badalna. Ye badlav app ke andar save hota hai,
 * original audio file ko chhuya nahi jaata (isliye safe hai).
 */
object MetadataManager {
    private lateinit var p: SharedPreferences
    private val map = HashMap<Long, Array<String>>()

    fun init(ctx: Context) {
        if (::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("kasturi_meta", Context.MODE_PRIVATE)
        try {
            val o = JSONObject(p.getString("data", "{}") ?: "{}")
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                map[k.toLong()] = arrayOf(a.getString(0), a.getString(1), a.getString(2))
            }
        } catch (e: Exception) { }
    }

    private fun save() {
        val o = JSONObject()
        for ((k, v) in map) o.put(k.toString(), JSONArray(listOf(v[0], v[1], v[2])))
        p.edit().putString("data", o.toString()).apply()
    }

    fun set(id: Long, title: String, artist: String, album: String) {
        map[id] = arrayOf(title, artist, album)
        save()
    }

    fun reset(id: Long) {
        map.remove(id)
        save()
    }

    fun apply(list: List<Song>): List<Song> {
        if (map.isEmpty()) return list
        return list.map { s ->
            val m = map[s.id]
            if (m == null) s else s.copy(
                title = m[0].ifBlank { s.title },
                artist = m[1].ifBlank { s.artist },
                album = m[2].ifBlank { s.album }
            )
        }
    }
}
