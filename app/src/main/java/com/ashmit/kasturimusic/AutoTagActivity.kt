package com.ashmit.kasturimusic

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Auto-tag screen: shuru/band, progress, review list, undo. */
class AutoTagActivity : Activity() {
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var startBtn: TextView
    private lateinit var reviewRow: TextView
    private lateinit var artSwitch: Switch
    private lateinit var messySwitch: Switch
    private lateinit var skipSwitch: Switch

    private val tick = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 500L)
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioPlayerManager.init(this)
        AutoTagger.init(this)
        build()
        Theme.applyBars(window)
    }

    override fun onStart() {
        super.onStart()
        AudioPlayerManager.activityStarted()
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AudioPlayerManager.activityStopped()
        super.onStop()
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.roundRect(Theme.card, dp(14).toFloat())
        c.setPadding(dp(16), dp(12), dp(16), dp(12))
        return c
    }

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 12f
        t.setTextColor(Theme.sub)
        t.letterSpacing = 0.12f
        t.setPadding(dp(8), dp(22), 0, dp(8))
        return t
    }

    private fun sw(text: String, on: Boolean): Switch {
        val s = Switch(this)
        s.text = text
        s.textSize = 15f
        s.setTextColor(Theme.text)
        s.isChecked = on
        s.setPadding(0, dp(10), 0, dp(10))
        return s
    }

    private fun build() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.TRANSPARENT)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(16), dp(20), dp(32))
        scroll.addView(col, ViewGroup.LayoutParams(match, wrap))

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
        title.text = "Auto-tag"
        title.textSize = 28f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        top.addView(title)
        col.addView(top)

        val info = card()
        val infoText = TextView(this)
        infoText.text = "Gaano ke naam, artist aur album free MusicBrainz database se apne aap sahi karta hai. Sirf pakka match apne aap lagta hai, baaki Review mein aate hain. Original files nahi badalti, aur Undo se sab wapas ho jaata hai.\n\n" +
            "1 gaana lagbhag 1 second leta hai. Har gaane ke liye kuch KB internet data lagta hai (cover ke saath lagbhag 50 KB)."
        infoText.textSize = 13f
        infoText.setTextColor(Theme.sub)
        info.addView(infoText)
        val lp0 = LinearLayout.LayoutParams(match, wrap)
        lp0.topMargin = dp(16)
        col.addView(info, lp0)

        col.addView(label("OPTIONS"))
        val opt = card()
        messySwitch = sw("Sirf galat ya adhoori info wale gaane", true)
        skipSwitch = sw("Pehle check kiye gaane chhodo", true)
        artSwitch = sw("Cover art bhi lao (zyada data)", false)
        opt.addView(messySwitch)
        opt.addView(skipSwitch)
        opt.addView(artSwitch)
        col.addView(opt, LinearLayout.LayoutParams(match, wrap))

        startBtn = TextView(this)
        startBtn.textSize = 16f
        startBtn.setTypeface(null, Typeface.BOLD)
        startBtn.gravity = Gravity.CENTER
        startBtn.setPadding(0, dp(14), 0, dp(14))
        startBtn.setOnClickListener { onStartStop() }
        Ui.pressable(startBtn)
        val slp = LinearLayout.LayoutParams(match, wrap)
        slp.topMargin = dp(20)
        col.addView(startBtn, slp)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.progressTintList = android.content.res.ColorStateList.valueOf(Theme.accent)
        val plp = LinearLayout.LayoutParams(match, wrap)
        plp.topMargin = dp(16)
        col.addView(progress, plp)
        status = TextView(this)
        status.textSize = 13f
        status.setTextColor(Theme.sub)
        status.setPadding(0, dp(6), 0, 0)
        col.addView(status)

        col.addView(label("RESULTS"))
        val res = card()
        reviewRow = TextView(this)
        reviewRow.textSize = 16f
        reviewRow.setTextColor(Theme.text)
        reviewRow.setPadding(0, dp(10), 0, dp(10))
        reviewRow.setOnClickListener { showReviewList() }
        res.addView(reviewRow)
        val undo = TextView(this)
        undo.text = "Undo all auto-tag changes"
        undo.textSize = 16f
        undo.setTextColor(Theme.text)
        undo.setPadding(0, dp(10), 0, dp(10))
        undo.setOnClickListener { confirmUndo() }
        res.addView(undo)
        val reset = TextView(this)
        reset.text = "Checked list reset karo (sab gaane phir se check honge)"
        reset.textSize = 14f
        reset.setTextColor(Theme.sub)
        reset.setPadding(0, dp(10), 0, dp(10))
        reset.setOnClickListener {
            AutoTagger.resetChecked()
            Toast.makeText(this, "Reset ho gaya", Toast.LENGTH_SHORT).show()
        }
        res.addView(reset)
        col.addView(res, LinearLayout.LayoutParams(match, wrap))

        setContentView(PhotoFrame(this, scroll))
        updateUi()
    }

    private fun onStartStop() {
        if (AutoTagger.running) {
            AutoTagger.cancelFlag = true
            return
        }
        val songs = MusicRepository.songs
        if (songs.isEmpty()) {
            Toast.makeText(this, "Pehle Library mein gaane load hone do", Toast.LENGTH_LONG).show()
            return
        }
        AutoTagger.start(this, songs, messySwitch.isChecked, skipSwitch.isChecked, artSwitch.isChecked)
        updateUi()
    }

    private fun updateUi() {
        val running = AutoTagger.running
        startBtn.text = if (running) "Stop" else "Start auto-tag"
        startBtn.setTextColor(Theme.onAccent)
        startBtn.background = Ui.roundRect(Theme.accent, dp(14).toFloat())
        progress.max = maxOf(1, AutoTagger.total)
        progress.progress = AutoTagger.checked
        val sb = StringBuilder()
        if (running) {
            sb.append("Checking ").append(AutoTagger.checked).append(" / ").append(AutoTagger.total)
        } else if (AutoTagger.finished) {
            sb.append("Poora hua: ").append(AutoTagger.checked).append(" / ").append(AutoTagger.total).append(" check kiye")
        } else {
            sb.append("Abhi shuru nahi hua")
        }
        sb.append("  \u2022  Fixed ").append(AutoTagger.fixed)
        sb.append("  \u2022  No match ").append(AutoTagger.noMatch)
        val err = AutoTagger.error
        if (err != null) sb.append("\n").append(err)
        status.text = sb.toString()
        val rc = AutoTagger.reviewCount()
        reviewRow.text = if (rc > 0) "Review ($rc gaane, tap karke chuno)" else "Review: koi gaana nahi"
        if (running) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun dialog() = AlertDialog.Builder(this, Theme.dialogStyle)

    private fun showReviewList() {
        val list = AutoTagger.reviewList()
        if (list.isEmpty()) return
        val labels = Array(list.size) { i -> list[i].song.title + " \u2014 " + list[i].song.artist }
        dialog().setTitle("Review").setItems(labels) { _, which -> showOptions(list[which]) }
            .setNegativeButton("Close", null).show()
    }

    private fun showOptions(r: AutoTagger.Review) {
        val labels = Array(r.options.size) { i ->
            val x = r.options[i]
            x.title + " \u2014 " + x.artist + (if (x.album.isNotBlank()) " (" + x.album + ")" else "")
        }
        dialog().setTitle("Sahi info chuno").setItems(labels) { _, which ->
            AutoTagger.applyManual(this, r.song, r.options[which])
            Toast.makeText(this, "Info update ho gayi", Toast.LENGTH_SHORT).show()
            updateUi()
        }.setNeutralButton("Skip") { _, _ ->
            AutoTagger.skipReview(r.song)
            updateUi()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmUndo() {
        dialog().setTitle("Undo all?")
            .setMessage("Auto-tag se hue saare naam, artist, album aur cover wapas original ho jaayenge.")
            .setPositiveButton("Undo") { _, _ ->
                AutoTagger.undoAll(this)
                Toast.makeText(this, "Wapas original kar diya", Toast.LENGTH_SHORT).show()
                updateUi()
            }
            .setNegativeButton("Cancel", null).show()
    }
}
