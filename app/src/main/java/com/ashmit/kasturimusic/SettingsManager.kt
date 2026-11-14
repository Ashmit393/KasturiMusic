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

    /** Gaano ke beech fade (seconds). 0 = off */
    var crossfadeSec: Int
        get() = p.getInt("xfade", 0)
        set(v) { p.edit().putInt("xfade", v).apply() }

    var floatingPlayer: Boolean
        get() = p.getBoolean("floating_player", false)
        set(v) { p.edit().putBoolean("floating_player", v).apply() }

    /** Background photo ka blur (0-100) aur dimming (0-100) */
    var bgBlur: Int
        get() = p.getInt("bg_blur", 60)
        set(v) { p.edit().putInt("bg_blur", v).apply() }

    var bgDim: Int
        get() = p.getInt("bg_dim", 40)
        set(v) { p.edit().putInt("bg_dim", v).apply() }

    /** true = compat (software) glass blur, har phone par chalta hai; false = GPU blur (Android 12+) */
    var glassCompat: Boolean
        get() = p.getBoolean("glass_compat", true)
        set(v) { p.edit().putBoolean("glass_compat", v).apply() }

    /** Nav bar aur mini player par live glass blur */
    var liquidGlass: Boolean
        get() = p.getBoolean("liquid_glass", true)
        set(v) { p.edit().putBoolean("liquid_glass", v).apply() }

    var shake: Boolean
        get() = p.getBoolean("shake", false)
        set(v) { p.edit().putBoolean("shake", v).apply() }

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
