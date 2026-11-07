package com.ashmit.kasturimusic

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer

/** Asli Android Equalizer (audio effect). Support na ho to available = false, fake nahi karte. */
object EqualizerManager {
    private lateinit var p: SharedPreferences
    private var eq: Equalizer? = null

    fun init(ctx: Context) {
        if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("kasturi_eq", Context.MODE_PRIVATE)
    }

    var enabled: Boolean
        get() = p.getBoolean("on", false)
        set(v) {
            p.edit().putBoolean("on", v).apply()
            try { eq?.enabled = v } catch (ex: Exception) { }
        }

    val available: Boolean
        get() = eq != null

    /** Har naye MediaPlayer ke baad call hota hai. */
    fun attach(session: Int) {
        release()
        try {
            val e = Equalizer(0, session)
            val range = e.bandLevelRange
            for (b in 0 until e.numberOfBands) {
                val lv = p.getInt("band$b", 0).coerceIn(range[0].toInt(), range[1].toInt())
                e.setBandLevel(b.toShort(), lv.toShort())
            }
            e.enabled = enabled
            eq = e
        } catch (ex: Exception) {
            eq = null
        }
    }

    fun release() {
        try { eq?.release() } catch (ex: Exception) { }
        eq = null
    }

    fun bands(): Int = try { eq?.numberOfBands?.toInt() ?: 0 } catch (ex: Exception) { 0 }

    fun range(): IntArray {
        return try {
            val r = eq!!.bandLevelRange
            intArrayOf(r[0].toInt(), r[1].toInt())
        } catch (ex: Exception) { intArrayOf(-1500, 1500) }
    }

    fun freqHz(b: Int): Int = try { eq!!.getCenterFreq(b.toShort()) / 1000 } catch (ex: Exception) { 0 }

    fun level(b: Int): Int = try { eq!!.getBandLevel(b.toShort()).toInt() } catch (ex: Exception) { 0 }

    fun setLevel(b: Int, lv: Int) {
        try { eq?.setBandLevel(b.toShort(), lv.toShort()) } catch (ex: Exception) { }
        p.edit().putInt("band$b", lv).apply()
    }

    fun presets(): List<String> {
        val e = eq ?: return emptyList()
        return try {
            (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
        } catch (ex: Exception) { emptyList() }
    }

    fun usePreset(i: Int) {
        val e = eq ?: return
        try {
            e.usePreset(i.toShort())
            for (b in 0 until e.numberOfBands) {
                p.edit().putInt("band$b", e.getBandLevel(b.toShort()).toInt()).apply()
            }
        } catch (ex: Exception) { }
    }
}
