package com.ashmit.kasturimusic

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private var pendingFloating = false
    private var floatSwitch: Switch? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onStart() {
        super.onStart()
        AudioPlayerManager.activityStarted()
    }

    override fun onStop() {
        AudioPlayerManager.activityStopped()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (pendingFloating && Settings.canDrawOverlays(this)) {
            pendingFloating = false
            floatSwitch?.isChecked = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioPlayerManager.init(this)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        build()
        Theme.applyBars(window)
    }

    private fun dialog() = AlertDialog.Builder(this, Theme.dialogStyle)

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 12f
        t.setTextColor(Theme.sub)
        t.letterSpacing = 0.12f
        t.setPadding(dp(8), dp(22), 0, dp(8))
        return t
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.roundRect(Theme.card, dp(14).toFloat())
        c.setPadding(dp(16), dp(10), dp(16), dp(10))
        return c
    }

    private fun build() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.bg)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(16), dp(20), dp(32))
        scroll.addView(col, ViewGroup.LayoutParams(match, wrap))

        // top bar
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val back = TextView(this)
        back.text = "\u2039"
        back.textSize = 34f
        back.setTextColor(Theme.accent)
        back.setPadding(0, 0, dp(12), 0)
        back.setOnClickListener { finish() }
        top.addView(back)
        val title = TextView(this)
        title.text = "Settings"
        title.textSize = 28f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        top.addView(title)
        col.addView(top)

        // Appearance
        col.addView(label("APPEARANCE"))
        val ac = card()
        val group = RadioGroup(this)
        val names = arrayOf("AMOLED Black", "Dark", "Light")
        val modes = intArrayOf(Theme.AMOLED, Theme.DARK, Theme.LIGHT)
        val ids = IntArray(3) { View.generateViewId() }
        for (i in 0 until 3) {
            val rb = RadioButton(this)
            rb.id = ids[i]
            rb.text = names[i]
            rb.textSize = 16f
            rb.setTextColor(Theme.text)
            rb.buttonTintList = ColorStateList.valueOf(Theme.accent)
            rb.setPadding(dp(8), dp(10), 0, dp(10))
            group.addView(rb, RadioGroup.LayoutParams(match, wrap))
        }
        val current = SettingsManager.theme
        for (i in 0 until 3) if (modes[i] == current) group.check(ids[i])
        group.setOnCheckedChangeListener { _, checkedId ->
            for (i in 0 until 3) {
                if (ids[i] == checkedId && modes[i] != SettingsManager.theme) {
                    SettingsManager.theme = modes[i]
                    recreate()
                }
            }
        }
        ac.addView(group)
        col.addView(ac, LinearLayout.LayoutParams(match, wrap))
        val hint = TextView(this)
        hint.text = "Now Playing screen hamesha dark glass look mein rehti hai."
        hint.textSize = 12f
        hint.setTextColor(Theme.sub)
        hint.setPadding(dp(8), dp(8), dp(8), 0)
        col.addView(hint)

        // Sound
        col.addView(label("SOUND"))
        val sc = card()
        val eqTitle = TextView(this)
        eqTitle.text = "Equalizer"
        eqTitle.textSize = 16f
        eqTitle.setTextColor(Theme.text)
        eqTitle.setPadding(0, dp(8), 0, 0)
        val eqSub = TextView(this)
        eqSub.text = "Bass aur treble adjust karo"
        eqSub.textSize = 13f
        eqSub.setTextColor(Theme.sub)
        eqSub.setPadding(0, 0, 0, dp(8))
        sc.addView(eqTitle)
        sc.addView(eqSub)
        sc.setOnClickListener { showEqualizer() }
        col.addView(sc, LinearLayout.LayoutParams(match, wrap))

        // Playback
        col.addView(label("PLAYBACK"))
        val pc = card()
        pc.addView(settingRow("Sleep timer", { sleepText() }) { sub -> showSleepDialog(sub) })
        pc.addView(settingRow("Fade between songs", { fadeText() }) { sub -> showFadeDialog(sub) })
        val shakeSw = Switch(this)
        shakeSw.text = "Shake to change song"
        shakeSw.textSize = 16f
        shakeSw.setTextColor(Theme.text)
        shakeSw.isChecked = SettingsManager.shake
        shakeSw.setPadding(0, dp(12), 0, dp(12))
        shakeSw.setOnCheckedChangeListener { _, c ->
            SettingsManager.shake = c
            AudioPlayerManager.notifyChanged()
        }
        pc.addView(shakeSw)

        val floatingSw = Switch(this)
        floatingSw.text = "Floating player over other apps"
        floatingSw.textSize = 16f
        floatingSw.setTextColor(Theme.text)
        floatingSw.isChecked = SettingsManager.floatingPlayer && Settings.canDrawOverlays(this)
        floatingSw.setPadding(0, dp(12), 0, dp(4))
        floatSwitch = floatingSw
        floatingSw.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !Settings.canDrawOverlays(this)) {
                pendingFloating = true
                floatingSw.isChecked = false
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                android.widget.Toast.makeText(this, "Allow karke wapas aao, option apne aap on ho jaayega.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                SettingsManager.floatingPlayer = enabled
                AudioPlayerManager.notifyChanged()
            }
        }
        pc.addView(floatingSw)
        col.addView(pc, LinearLayout.LayoutParams(match, wrap))

        // About
        col.addView(label("ABOUT"))
        val abc = card()
        abc.setPadding(dp(16), dp(16), dp(16), dp(16))
        fun line(text: String, size: Float, color: Int, bold: Boolean, top: Int) {
            val t = TextView(this)
            t.text = text
            t.textSize = size
            t.setTextColor(color)
            if (bold) t.setTypeface(null, Typeface.BOLD)
            t.setPadding(0, dp(top), 0, 0)
            abc.addView(t)
        }
        var v = "1.0.0"
        try { v = packageManager.getPackageInfo(packageName, 0).versionName ?: v } catch (e: Exception) { }
        line("KASTURI MUSIC PLAYER", 18f, Theme.text, true, 0)
        line("Version $v", 13f, Theme.sub, false, 2)
        line("Made with \u2665 by Ashmit Singh", 16f, Theme.accent, true, 14)
        line("Developer: Ashmit Singh", 14f, Theme.text, false, 4)
        line("Songs play offline. Internet is used only to find lyrics. No ads. No tracking.", 13f, Theme.sub, false, 14)
        line("ashmit393.github.io/KasturiMusic", 13f, Theme.sub, false, 10)
        col.addView(abc, LinearLayout.LayoutParams(match, wrap))

        setContentView(scroll)
    }

    // ---------------- playback settings ----------------
    private fun settingRow(title: String, sub: () -> String, onClick: (TextView) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, dp(10), 0, dp(10))
        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTextColor(Theme.text)
        val s = TextView(this)
        s.text = sub()
        s.textSize = 13f
        s.setTextColor(Theme.sub)
        row.addView(t)
        row.addView(s)
        row.setOnClickListener { onClick(s) }
        return row
    }

    private fun sleepText(): String {
        val ms = AudioPlayerManager.sleepRemainingMs()
        return if (ms <= 0L) "Off" else (ms / 60000L + 1L).toString() + " min left"
    }

    private fun fadeText(): String {
        val s = SettingsManager.crossfadeSec
        return if (s == 0) "Off" else "$s seconds"
    }

    private fun showSleepDialog(sub: TextView) {
        val labels = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes")
        val mins = intArrayOf(0, 15, 30, 45, 60, 90)
        dialog().setTitle("Sleep timer").setItems(labels) { _, which ->
            AudioPlayerManager.setSleepTimer(mins[which])
            sub.text = sleepText()
        }.show()
    }

    private fun showFadeDialog(sub: TextView) {
        val labels = arrayOf("Off", "3 seconds", "6 seconds", "9 seconds", "12 seconds")
        val secs = intArrayOf(0, 3, 6, 9, 12)
        dialog().setTitle("Fade between songs").setItems(labels) { _, which ->
            SettingsManager.crossfadeSec = secs[which]
            AudioPlayerManager.applyFadeSetting()
            sub.text = fadeText()
        }.show()
    }

    // ---------------- equalizer ----------------
    private fun showEqualizer() {
        if (!EqualizerManager.available) {
            dialog().setTitle("Equalizer")
                .setMessage("Equalizer abhi available nahi hai. Pehle app mein koi gaana chalao aur wapas aao. Agar phir bhi na khule, to is phone par equalizer support nahi hai.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(8), dp(20), dp(8))
        val sw = Switch(this)
        sw.text = "Equalizer on"
        sw.textSize = 16f
        sw.isChecked = EqualizerManager.enabled
        sw.setOnCheckedChangeListener { _, c -> EqualizerManager.enabled = c }
        col.addView(sw)

        val range = EqualizerManager.range()
        val bars = ArrayList<SeekBar>()
        val presets = EqualizerManager.presets()
        if (presets.isNotEmpty()) {
            val pt = TextView(this)
            pt.text = "Presets \u203A"
            pt.textSize = 15f
            pt.setTextColor(Theme.accent)
            pt.setPadding(0, dp(14), 0, dp(6))
            pt.setOnClickListener {
                dialog().setTitle("Presets").setItems(presets.toTypedArray()) { _, which ->
                    EqualizerManager.usePreset(which)
                    for (b in bars.indices) bars[b].progress = EqualizerManager.level(b) - range[0]
                }.show()
            }
            col.addView(pt)
        }
        for (b in 0 until EqualizerManager.bands()) {
            val f = EqualizerManager.freqHz(b)
            val lab = TextView(this)
            lab.text = if (f >= 1000) (f / 1000).toString() + " kHz" else "$f Hz"
            lab.textSize = 13f
            lab.setTextColor(Theme.sub)
            lab.setPadding(0, dp(10), 0, 0)
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = range[1] - range[0]
            sb.progress = EqualizerManager.level(b) - range[0]
            sb.progressTintList = ColorStateList.valueOf(Theme.accent)
            sb.thumbTintList = ColorStateList.valueOf(Theme.accent)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) EqualizerManager.setLevel(b, p + range[0])
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
            bars.add(sb)
            col.addView(sb)
        }
        val sv = ScrollView(this)
        sv.addView(col)
        dialog().setTitle("Equalizer").setView(sv).setPositiveButton("Done", null).show()
    }
}
