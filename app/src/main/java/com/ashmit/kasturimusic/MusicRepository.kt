package com.ashmit.kasturimusic

import android.content.Context
import android.content.SharedPreferences

object MusicRepository {
    @Volatile
    var songs: List<Song> = emptyList()

    private lateinit var p: SharedPreferences
    private val favs = LinkedHashSet<Long>()
    private val recents = ArrayList<Long>()
    private val plays = HashMap<Long, Int>()

    fun init(ctx: Context) {
        if (::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("kasturi_library", Context.MODE_PRIVATE)
        favs.addAll(parse(p.getString("favs", "")))
        recents.addAll(parse(p.getString("recents", "")))
        val pc = p.getString("plays", "") ?: ""
        if (pc.isNotEmpty()) {
            for (part in pc.split(",")) {
                val kv = part.split(":")
                if (kv.size == 2) {
                    val id = kv[0].toLongOrNull()
                    val n = kv[1].toIntOrNull()
                    if (id != null && n != null) plays[id] = n
                }
            }
        }
    }

    private fun parse(s: String?): List<Long> =
        if (s.isNullOrEmpty()) emptyList() else s.split(",").mapNotNull { it.toLongOrNull() }

    fun isFav(id: Long): Boolean = favs.contains(id)

    fun toggleFav(id: Long) {
        if (!favs.add(id)) favs.remove(id)
        p.edit().putString("favs", favs.joinToString(",")).apply()
    }

    fun addRecent(id: Long) {
        recents.remove(id)
        recents.add(0, id)
        while (recents.size > 50) recents.removeAt(recents.size - 1)
        p.edit().putString("recents", recents.joinToString(",")).apply()
    }

    fun addPlay(id: Long) {
        plays[id] = (plays[id] ?: 0) + 1
        p.edit().putString("plays", plays.entries.joinToString(",") { it.key.toString() + ":" + it.value }).apply()
    }

    fun mostPlayed(): List<Song> {
        val map = songs.associateBy { it.id }
        return plays.entries.sortedByDescending { it.value }.mapNotNull { map[it.key] }
    }

    fun favSongs(): List<Song> {
        val map = songs.associateBy { it.id }
        return favs.mapNotNull { map[it] }.reversed()
    }

    fun recentSongs(): List<Song> {
        val map = songs.associateBy { it.id }
        return recents.mapNotNull { map[it] }
    }
}
