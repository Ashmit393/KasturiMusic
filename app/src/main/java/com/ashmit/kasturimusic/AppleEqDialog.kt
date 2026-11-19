package com.ashmit.kasturimusic

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

object AppleEqDialog {
    fun show(ctx: Context) {
        val sessionId = AudioPlayerManager.sessionId()
        EqualizerManager.ensureInit(sessionId)
        val bands = EqualizerManager.bandCount()
        if (bands <= 0) {
            Toast.makeText(ctx, "Equalizer available nahi hai is device par", Toast.LENGTH_SHORT).show()
            return
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.GRAPHITE)
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 18), Ui.dp(ctx, 20), Ui.dp(ctx, 18))
        }

        val title = TextView(ctx).apply {
            text = "EQUALIZER"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            letterSpacing = 0.1f
        }
        root.addView(title)

        val presets = EqualizerManager.presetNames()
        if (presets.isNotEmpty()) {
            val presetRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 8))
            }
            val presetBtn = TextView(ctx).apply {
                val cur = presets.getOrElse(EqualizerManager.currentPreset()) { "Custom" }
                text = "Preset: $cur (Tap)"
                textSize = 13f
                setTextColor(Ui.SILVER)
                background = Ui.roundRect(Ui.GRAPHITE2, Ui.dp(ctx, 10).toFloat())
                setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
                setOnClickListener {
                    AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("EQ Preset")
                        .setItems(presets) { _, which ->
                            EqualizerManager.usePreset(which)
                            text = "Preset: ${presets[which]}"
                        }
                        .show()
                }
            }
            presetRow.addView(presetBtn)
            root.addView(presetRow)
        }

        val minDb = EqualizerManager.minDb()
        val maxDb = EqualizerManager.maxDb()
        val range = maxOf(1, maxDb - minDb)

        for (i in 0 until bands) {
            val hz = EqualizerManager.centerFreqHz(i)
            val labelText = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4))
            }
            val lbl = TextView(ctx).apply {
                text = labelText
                textSize = 12f
                setTextColor(Ui.SILVER)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 64), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val valTxt = TextView(ctx).apply {
                val curDb = EqualizerManager.bandDb(i)
                text = "${if (curDb > 0) "+" else ""}${curDb} dB"
                textSize = 12f
                setTextColor(Ui.DIM)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 56), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val sb = SeekBar(ctx).apply {
                max = range
                progress = (EqualizerManager.bandDb(i) - minDb).coerceIn(0, range)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                        val db = minDb + p
                        valTxt.text = "${if (db > 0) "+" else ""}${db} dB"
                        if (fromUser) {
                            EqualizerManager.setBandDb(i, db)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(lbl)
            row.addView(sb)
            row.addView(valTxt)
            root.addView(row)
        }

        val resetRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, Ui.dp(ctx, 10), 0, 0)
        }
        val resetBtn = Button(ctx).apply {
            text = "Flat / Reset"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = Ui.roundRect(Ui.GRAPHITE2, Ui.dp(ctx, 10).toFloat())
            setOnClickListener {
                EqualizerManager.resetFlat()
                Toast.makeText(ctx, "EQ Reset", Toast.LENGTH_SHORT).show()
            }
        }
        resetRow.addView(resetBtn)
        root.addView(resetRow)

        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(root)
            .show()
    }
}
