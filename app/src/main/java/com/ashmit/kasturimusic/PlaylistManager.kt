package com.ashmit.kasturimusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object PlaylistManager {
    private lateinit var p: SharedPreferences
    private val lists = LinkedHashMap<String, MutableList<Long>>()

    fun init(ctx: Context) {
        if (::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("kasturi_playlists", Context.MODE_PRIVATE)
        try {
            val arr = JSONArray(p.getString("data", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("ids")
                val l = ArrayList<Long>()
                for (j in 0 until ids.length()) l.add(ids.getLong(j))
                lists[o.getString("n")] = l
            }
        } catch (e: Exception) { }
    }

    private fun save() {
        val arr = JSONArray()
        for ((n, ids) in lists) {
            val o = JSONObject()
            o.put("n", n)
            o.put("ids", JSONArray(ids))
            arr.put(o)
        }
        p.edit().putString("data", arr.toString()).apply()
    }

    fun names(): List<String> = lists.keys.toList()

    fun create(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || lists.containsKey(n)) return false
        lists[n] = ArrayList()
        save()
        return true
    }

    fun rename(old: String, newName: String): Boolean {
        val n = newName.trim()
        if (n.isEmpty() || (n != old && lists.containsKey(n))) return false
        val entries = lists.entries.map { Pair(it.key, it.value) }
        lists.clear()
        for (e in entries) lists[if (e.first == old) n else e.first] = e.second
        save()
        return true
    }

    fun delete(name: String) {
        lists.remove(name)
        save()
    }

    fun add(name: String, id: Long): Boolean {
        val l = lists[name] ?: return false
        if (l.contains(id)) return false
        l.add(id)
        save()
        return true
    }

    fun remove(name: String, id: Long) {
        lists[name]?.remove(id)
        save()
    }

    fun songs(name: String): List<Song> {
        val map = MusicRepository.songs.associateBy { it.id }
        return lists[name]?.mapNotNull { map[it] } ?: emptyList()
    }
}
