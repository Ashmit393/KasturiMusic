package com.ashmit.kasturimusic

import android.content.Context
import android.content.SharedPreferences

object MusicRepository {
    @Volatile
    var songs: List<Song> = emptyList()

    private lateinit var p: SharedPreferences
    private val favs = LinkedHashSet<Long>()
    private val recents = ArrayList<Long>()

    fun init(ctx: Context) {
        if (::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("kasturi_library", Context.MODE_PRIVATE)
        favs.addAll(parse(p.getString("favs", "")))
        recents.addAll(parse(p.getString("recents", "")))
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

    fun favSongs(): List<Song> {
        val map = songs.associateBy { it.id }
        return favs.mapNotNull { map[it] }.reversed()
    }

    fun recentSongs(): List<Song> {
        val map = songs.associateBy { it.id }
        return recents.mapNotNull { map[it] }
    }
}
