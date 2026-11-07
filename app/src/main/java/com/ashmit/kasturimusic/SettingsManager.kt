package com.ashmit.kasturimusic

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        if (!::p.isInitialized) {
            p = ctx.applicationContext.getSharedPreferences("kasturi_settings", Context.MODE_PRIVATE)
        }
    }

    /** 0 = Light, 1 = Dark, 2 = AMOLED black (default) */
    var theme: Int
        get() = p.getInt("theme", 2)
        set(v) { p.edit().putInt("theme", v).apply() }

    var sort: Int
        get() = p.getInt("sort", 0)
        set(v) { p.edit().putInt("sort", v).apply() }

    var shuffle: Boolean
        get() = p.getBoolean("shuffle", false)
        set(v) { p.edit().putBoolean("shuffle", v).apply() }

    var repeat: Int
        get() = p.getInt("repeat", 0)
        set(v) { p.edit().putInt("repeat", v).apply() }

    var lastId: Long
        get() = p.getLong("last_id", 0L)
        set(v) { p.edit().putLong("last_id", v).apply() }

    var lastPos: Int
        get() = p.getInt("last_pos", 0)
        set(v) { p.edit().putInt("last_pos", v).apply() }
}
