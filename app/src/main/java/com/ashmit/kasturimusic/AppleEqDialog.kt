package com.ashmit.kasturimusic

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

object AppleEqDialog {
    fun show(ctx: Context) {
        EqualizerManager.init(ctx)
        val bandCount = EqualizerManager.bands()
        if (!EqualizerManager.available || bandCount <= 0) {
            Toast.makeText(ctx, "Equalizer available nahi hai is device par", Toast.LENGTH_SHORT).show()
            return
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.GRAPHITE)
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 18), Ui.dp(ctx, 20), Ui.dp(ctx, 18))
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(ctx).apply {
            text = "EQUALIZER"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val enableSw = Switch(ctx).apply {
            isChecked = EqualizerManager.enabled
            setOnCheckedChangeListener { _, checked ->
                EqualizerManager.enabled = checked
            }
        }
        topRow.addView(title)
        topRow.addView(enableSw)
        root.addView(topRow)

        val presetList = EqualizerManager.presets()
        if (presetList.isNotEmpty()) {
            val presetRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 8))
            }
            val presetBtn = TextView(ctx).apply {
                text = "Preset: Tap to select"
                textSize = 13f
                setTextColor(Ui.SILVER)
                background = Ui.roundRect(Ui.GRAPHITE2, Ui.dp(ctx, 10).toFloat())
                setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
                setOnClickListener {
                    val items: Array<CharSequence> = presetList.map { it as CharSequence }.toTypedArray()
                    AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("EQ Preset")
                        .setItems(items) { _, which: Int ->
                            EqualizerManager.usePreset(which)
                            text = "Preset: ${presetList[which]}"
                            Toast.makeText(ctx, "Preset applied: ${presetList[which]}", Toast.LENGTH_SHORT).show()
                        }
                        .show()
                }
            }
            presetRow.addView(presetBtn)
            root.addView(presetRow)
        }

        val r = EqualizerManager.range()
        val minMb = r[0]
        val maxMb = r[1]
        val spanMb = maxOf(1, maxMb - minMb)

        for (i in 0 until bandCount) {
            val hz = EqualizerManager.freqHz(i)
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
            val curMb = EqualizerManager.level(i)
            val curDb = curMb / 100
            val valTxt = TextView(ctx).apply {
                text = "${if (curDb > 0) "+" else ""}${curDb} dB"
                textSize = 12f
                setTextColor(Ui.DIM)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 56), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val sb = SeekBar(ctx).apply {
                max = spanMb
                progress = (curMb - minMb).coerceIn(0, spanMb)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                        val mb = minMb + p
                        val db = mb / 100
                        valTxt.text = "${if (db > 0) "+" else ""}${db} dB"
                        if (fromUser) {
                            EqualizerManager.setLevel(i, mb)
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
                for (i in 0 until bandCount) {
                    EqualizerManager.setLevel(i, 0)
                }
                Toast.makeText(ctx, "EQ Flat (0 dB)", Toast.LENGTH_SHORT).show()
            }
        }
        resetRow.addView(resetBtn)
        root.addView(resetRow)

        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(root)
            .show()
    }
}
