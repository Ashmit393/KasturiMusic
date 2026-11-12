package com.ashmit.kasturimusic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Developer ki jaankari. PHONE khali ho to wo line dikhti hi nahi. */
object AboutInfo {
    const val NAME = "Ashmit Singh"
    const val GITHUB = "https://github.com/Ashmit393"
    const val EMAIL = "ashmitsingh393@gmail.com"
    const val PHONE = ""
    const val WEBSITE = "https://ashmit393.github.io/KasturiMusic"
    const val SOURCE = "https://github.com/Ashmit393/KasturiMusic"
}

class AboutActivity : Activity() {
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioPlayerManager.init(this)
        build()
        Theme.applyBars(window)
    }

    override fun onStart() {
        super.onStart()
        AudioPlayerManager.activityStarted()
    }

    override fun onStop() {
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

    private fun line(parent: LinearLayout, text: String, size: Float, color: Int, bold: Boolean, top: Int) {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, dp(top), 0, 0)
        parent.addView(t)
    }

    private fun linkRow(parent: LinearLayout, title: String, value: String, onClick: () -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, dp(10), 0, dp(10))
        val t = TextView(this)
        t.text = title
        t.textSize = 12f
        t.setTextColor(Theme.sub)
        val v = TextView(this)
        v.text = value
        v.textSize = 16f
        v.setTextColor(Theme.text)
        row.addView(t)
        row.addView(v)
        row.setOnClickListener { onClick() }
        parent.addView(row)
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Ye khul nahi paya", Toast.LENGTH_SHORT).show()
        }
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
        title.text = "About"
        title.textSize = 28f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        top.addView(title)
        col.addView(top)

        var v = "1.0.0"
        try { v = packageManager.getPackageInfo(packageName, 0).versionName ?: v } catch (e: Exception) { }

        col.addView(label("APP"))
        val app = card()
        line(app, "KASTURI MUSIC PLAYER", 18f, Theme.text, true, 0)
        line(app, "Version $v", 13f, Theme.sub, false, 2)
        line(app, "Made with \u2665 by " + AboutInfo.NAME, 16f, Theme.accent, true, 14)
        line(app, "Free, ad-free aur tracking-free music player.", 13f, Theme.sub, false, 10)
        col.addView(app, LinearLayout.LayoutParams(match, wrap))

        col.addView(label("DEVELOPER"))
        val dev = card()
        line(dev, AboutInfo.NAME, 18f, Theme.text, true, 0)
        linkRow(dev, "GitHub", "github.com/Ashmit393") {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(AboutInfo.GITHUB)))
        }
        linkRow(dev, "Email", AboutInfo.EMAIL) {
            launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + AboutInfo.EMAIL)))
        }
        if (AboutInfo.PHONE.isNotBlank()) {
            linkRow(dev, "Contact", AboutInfo.PHONE) {
                launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + AboutInfo.PHONE)))
            }
        }
        linkRow(dev, "Website", "ashmit393.github.io/KasturiMusic") {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(AboutInfo.WEBSITE)))
        }
        linkRow(dev, "Source code", "github.com/Ashmit393/KasturiMusic") {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(AboutInfo.SOURCE)))
        }
        col.addView(dev, LinearLayout.LayoutParams(match, wrap))

        col.addView(label("CREDITS"))
        val cr = card()
        line(cr, "Lyrics: LRCLIB (free, open)", 14f, Theme.text, false, 0)
        line(cr, "Song info suggestions: MusicBrainz", 14f, Theme.text, false, 6)
        line(cr, "Gaane aapke phone ke hain; internet sirf lyrics aur info dhoondhne ke liye use hota hai.", 12f, Theme.sub, false, 10)
        col.addView(cr, LinearLayout.LayoutParams(match, wrap))

        setContentView(PhotoFrame(this, scroll))
    }
}
