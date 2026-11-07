package com.ashmit.kasturimusic

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Full-screen Now Playing: artwork / lyrics, seek bar, volume slider, controls. */
class NowPlayingView(ctx: Context, private val onClose: () -> Unit) : FrameLayout(ctx) {

    private val handler = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val slot = FrameLayout(ctx)
    private val artView = ImageView(ctx)
    private val lyricsPanel = LyricsPanel(ctx, { ms -> AudioPlayerManager.seekTo(ms) }, { showLyricsMenu() })
    private val titleView = TextView(ctx)
    private val artistView = TextView(ctx)
    private val elapsed = TextView(ctx)
    private val total = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val volSeek = SeekBar(ctx)
    private val playBtn = ImageButton(ctx)
    private val shuffleBtn = Ui.iconButton(ctx, R.drawable.ic_shuffle, Ui.DIM)
    private val prevBtn = Ui.iconButton(ctx, R.drawable.ic_prev, Color.WHITE, 12)
    private val nextBtn = Ui.iconButton(ctx, R.drawable.ic_next, Color.WHITE, 12)
    private val repeatBtn = Ui.iconButton(ctx, R.drawable.ic_repeat, Ui.DIM)
    private val heartBtn = Ui.iconButton(ctx, R.drawable.ic_heart_outline, Ui.SILVER)
    private val lyricsBtn = TextView(ctx)
    private val queueBtn = TextView(ctx)
    private var dragging = false
    private var volDragging = false
    private var shownId = -1L
    private var artPx = 0
    private var lyricsMode = false
    private var lyricsSongId = -1L
    private val triedOnline = HashSet<Long>()

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            updateVolume()
            if (lyricsMode) lyricsPanel.tick(AudioPlayerManager.position())
            handler.postDelayed(this, if (lyricsMode) 250L else 500L)
        }
    }

    val isOpen: Boolean
        get() = visibility == View.VISIBLE

    init {
        setBackgroundColor(Ui.BLACK)
        isClickable = true
        visibility = View.GONE
        build()
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    private fun styleSeek(sb: SeekBar) {
        sb.progressTintList = ColorStateList.valueOf(Ui.SILVER)
        sb.thumbTintList = ColorStateList.valueOf(Ui.SILVER)
        sb.progressBackgroundTintList = ColorStateList.valueOf(Ui.GRAPHITE2)
    }

    private fun build() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(24), dp(8), dp(24), dp(12))
        addView(col, FrameLayout.LayoutParams(match, match))

        // top bar
        val top = LinearLayout(context)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val close = Ui.iconButton(context, R.drawable.ic_chevron_down, Ui.SILVER, 8)
        close.setOnClickListener { onClose() }
        top.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        val label = TextView(context)
        label.text = "NOW PLAYING"
        label.textSize = 12f
        label.setTextColor(Ui.DIM)
        label.gravity = Gravity.CENTER
        label.letterSpacing = 0.15f
        top.addView(label, LinearLayout.LayoutParams(0, wrap, 1f))
        top.addView(View(context), LinearLayout.LayoutParams(dp(48), dp(48)))
        col.addView(top, LinearLayout.LayoutParams(match, wrap))

        // slot: artwork ya lyrics (bachi hui jagah le leta hai)
        artView.setBackgroundColor(Ui.GRAPHITE)
        Ui.round(artView, dp(16).toFloat())
        slot.addView(artView, FrameLayout.LayoutParams(dp(100), dp(100), Gravity.CENTER))
        lyricsPanel.visibility = View.GONE
        slot.addView(lyricsPanel, FrameLayout.LayoutParams(match, match))
        slot.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val s = minOf(r - l, b - t) - dp(8)
            if (s > 0 && s != artPx) {
                post {
                    artPx = s
                    val lp = artView.layoutParams
                    lp.width = s
                    lp.height = s
                    artView.layoutParams = lp
                    shownId = -1L
                    refresh()
                }
            }
        }
        val slp = LinearLayout.LayoutParams(match, 0, 1f)
        slp.topMargin = dp(8)
        slp.bottomMargin = dp(4)
        col.addView(slot, slp)

        // title + artist
        titleView.textSize = 22f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setTextColor(Color.WHITE)
        titleView.gravity = Gravity.CENTER
        titleView.isSingleLine = true
        titleView.ellipsize = TextUtils.TruncateAt.END
        col.addView(titleView, LinearLayout.LayoutParams(match, wrap))
        artistView.textSize = 16f
        artistView.setTextColor(Ui.SILVER)
        artistView.gravity = Gravity.CENTER
        artistView.isSingleLine = true
        artistView.ellipsize = TextUtils.TruncateAt.END
        col.addView(artistView, LinearLayout.LayoutParams(match, wrap))

        // seek bar
        seek.max = 1000
        styleSeek(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = AudioPlayerManager.current?.durationMs ?: 0L
                    elapsed.text = Ui.fmt(p * dur / 1000L)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val dur = AudioPlayerManager.current?.durationMs ?: 0L
                AudioPlayerManager.seekTo((sb.progress * dur / 1000L).toInt())
                dragging = false
            }
        })
        val sklp = LinearLayout.LayoutParams(match, wrap)
        sklp.topMargin = dp(8)
        col.addView(seek, sklp)

        val times = LinearLayout(context)
        times.orientation = LinearLayout.HORIZONTAL
        elapsed.textSize = 12f
        elapsed.setTextColor(Ui.DIM)
        total.textSize = 12f
        total.setTextColor(Ui.DIM)
        total.gravity = Gravity.END
        times.addView(elapsed, LinearLayout.LayoutParams(0, wrap, 1f))
        times.addView(total, LinearLayout.LayoutParams(0, wrap, 1f))
        col.addView(times, LinearLayout.LayoutParams(match, wrap))

        // controls
        playBtn.setImageResource(R.drawable.ic_play)
        playBtn.setColorFilter(Color.BLACK)
        playBtn.background = Ui.roundRect(Ui.SILVER, dp(32).toFloat())
        playBtn.scaleType = ImageView.ScaleType.CENTER_INSIDE
        playBtn.setPadding(dp(16), dp(16), dp(16), dp(16))
        val controls = LinearLayout(context)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER_VERTICAL
        controls.addView(shuffleBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(prevBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        val plp = LinearLayout.LayoutParams(dp(64), dp(64))
        plp.setMargins(dp(8), 0, dp(8), 0)
        controls.addView(playBtn, plp)
        controls.addView(nextBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(repeatBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        val clp = LinearLayout.LayoutParams(match, wrap)
        clp.topMargin = dp(6)
        col.addView(controls, clp)

        // volume slider
        volSeek.max = try { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } catch (e: Exception) { 15 }
        styleSeek(volSeek)
        volSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0) } catch (e: Exception) { }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { volDragging = true }
            override fun onStopTrackingTouch(sb: SeekBar) { volDragging = false }
        })
        val vol = LinearLayout(context)
        vol.orientation = LinearLayout.HORIZONTAL
        vol.gravity = Gravity.CENTER_VERTICAL
        val volLow = ImageView(context)
        volLow.setImageResource(R.drawable.ic_volume_low)
        volLow.setColorFilter(Ui.DIM)
        val volHigh = ImageView(context)
        volHigh.setImageResource(R.drawable.ic_volume_high)
        volHigh.setColorFilter(Ui.DIM)
        vol.addView(volLow, LinearLayout.LayoutParams(dp(20), dp(20)))
        vol.addView(volSeek, LinearLayout.LayoutParams(0, wrap, 1f))
        vol.addView(volHigh, LinearLayout.LayoutParams(dp(20), dp(20)))
        val vlp = LinearLayout.LayoutParams(match, wrap)
        vlp.topMargin = dp(4)
        col.addView(vol, vlp)

        // bottom row: favourite | lyrics | queue
        lyricsBtn.text = "Lyrics"
        lyricsBtn.textSize = 15f
        lyricsBtn.setTextColor(Ui.SILVER)
        lyricsBtn.gravity = Gravity.CENTER
        queueBtn.text = "Queue"
        queueBtn.textSize = 15f
        queueBtn.setTextColor(Ui.SILVER)
        queueBtn.gravity = Gravity.CENTER
        val bottom = LinearLayout(context)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.addView(heartBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
        bottom.addView(lyricsBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
        bottom.addView(queueBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
        col.addView(bottom, LinearLayout.LayoutParams(match, wrap))

        // actions
        playBtn.setOnClickListener { AudioPlayerManager.toggle() }
        prevBtn.setOnClickListener { AudioPlayerManager.prev() }
        nextBtn.setOnClickListener { AudioPlayerManager.next() }
        shuffleBtn.setOnClickListener { AudioPlayerManager.setShuffle(!AudioPlayerManager.shuffle) }
        repeatBtn.setOnClickListener { AudioPlayerManager.cycleRepeat() }
        heartBtn.setOnClickListener {
            val s = AudioPlayerManager.current
            if (s != null) {
                MusicRepository.toggleFav(s.id)
                AudioPlayerManager.notifyChanged()
            }
        }
        lyricsBtn.setOnClickListener { toggleLyrics() }
        queueBtn.setOnClickListener { showQueue() }
    }

    fun refresh() {
        val s = AudioPlayerManager.current
        titleView.text = s?.title ?: ""
        artistView.text = if (s == null) "" else s.artist + " \u2022 " + s.album
        val id = s?.id ?: -1L
        if (id != shownId && artPx > 0) {
            shownId = id
            Ui.bindArt(artView, s, artPx, 48)
        }
        if (lyricsMode && isOpen && id != lyricsSongId) loadLyrics()
        playBtn.setImageResource(if (AudioPlayerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        shuffleBtn.setColorFilter(if (AudioPlayerManager.shuffle) Color.WHITE else Ui.DIM)
        val rep = AudioPlayerManager.repeat
        repeatBtn.setImageResource(if (rep == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
        repeatBtn.setColorFilter(if (rep == 0) Ui.DIM else Color.WHITE)
        val fav = s != null && MusicRepository.isFav(s.id)
        heartBtn.setImageResource(if (fav) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        updateProgress()
        updateVolume()
    }

    private fun updateProgress() {
        val dur = AudioPlayerManager.current?.durationMs ?: 0L
        val pos = AudioPlayerManager.position()
        if (!dragging) {
            seek.progress = if (dur > 0) (pos * 1000L / dur).toInt() else 0
            elapsed.text = Ui.fmt(pos.toLong())
        }
        total.text = Ui.fmt(dur)
    }

    private fun updateVolume() {
        if (volDragging) return
        val v = try { audio.getStreamVolume(AudioManager.STREAM_MUSIC) } catch (e: Exception) { 0 }
        if (volSeek.progress != v) volSeek.progress = v
    }

    // ---------------- lyrics ----------------
    private fun toggleLyrics() {
        lyricsMode = !lyricsMode
        artView.visibility = if (lyricsMode) View.GONE else View.VISIBLE
        lyricsPanel.visibility = if (lyricsMode) View.VISIBLE else View.GONE
        lyricsBtn.setTextColor(if (lyricsMode) Color.WHITE else Ui.SILVER)
        lyricsBtn.setTypeface(null, if (lyricsMode) Typeface.BOLD else Typeface.NORMAL)
        if (lyricsMode) loadLyrics()
    }

    private fun loadLyrics() {
        val s = AudioPlayerManager.current
        lyricsSongId = s?.id ?: -1L
        if (s == null) {
            lyricsPanel.setStatus("")
            return
        }
        val l = LyricsManager.load(context, s)
        if (l != null) {
            lyricsPanel.setLyrics(l)
        } else if (triedOnline.add(s.id)) {
            fetchFor(s)
        } else {
            lyricsPanel.setLyrics(null)
        }
    }

    private fun fetchFor(s: Song) {
        lyricsPanel.setStatus("Lyrics dhoondh raha hoon...")
        LyricsManager.fetchOnline(context, s) { raw ->
            if (AudioPlayerManager.current?.id == s.id && lyricsMode) {
                val l = if (raw != null) LyricsManager.parse(raw) else null
                if (l != null) lyricsPanel.setLyrics(l) else lyricsPanel.setLyrics(LyricsManager.load(context, s))
            }
        }
    }

    private fun dialog() = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)

    private fun showLyricsMenu() {
        val s = AudioPlayerManager.current ?: return
        val items = arrayOf("Find online", "Add / edit manually", "Delete saved lyrics")
        dialog().setTitle("Lyrics").setItems(items) { _, which ->
            when (which) {
                0 -> { triedOnline.add(s.id); fetchFor(s) }
                1 -> showEditor(s)
                2 -> { LyricsManager.delete(context, s); lyricsPanel.setLyrics(null) }
            }
        }.show()
    }

    private fun showEditor(s: Song) {
        val et = EditText(context)
        et.setText(LyricsManager.loadRaw(context, s) ?: "")
        et.hint = "Lyrics paste karo. Time ke saath (LRC) ho to line-by-line sync hoga."
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(Ui.DIM)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.gravity = Gravity.TOP or Gravity.START
        et.minLines = 8
        et.maxLines = 12
        val box = FrameLayout(context)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(et, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog().setTitle(s.title).setView(box)
            .setPositiveButton("Save") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isEmpty()) LyricsManager.delete(context, s) else LyricsManager.save(context, s, t)
                lyricsPanel.setLyrics(LyricsManager.load(context, s))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- show / hide ----------------
    fun show() {
        handler.removeCallbacks(ticker)
        visibility = View.VISIBLE
        val h = if (height > 0) height else resources.displayMetrics.heightPixels
        translationY = h.toFloat()
        refresh()
        animate().translationY(0f).setDuration(220).start()
        handler.post(ticker)
    }

    fun hide() {
        handler.removeCallbacks(ticker)
        val h = if (height > 0) height else resources.displayMetrics.heightPixels
        animate().translationY(h.toFloat()).setDuration(200).withEndAction {
            visibility = View.GONE
        }.start()
    }

    fun pauseTicker() {
        handler.removeCallbacks(ticker)
    }

    fun resumeTicker() {
        if (isOpen) {
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private fun showQueue() {
        val q = AudioPlayerManager.queue
        if (q.isEmpty()) return
        val cur = AudioPlayerManager.index
        val items = Array(q.size) { i -> (if (i == cur) "[Playing] " else "") + q[i].title }
        val d = dialog()
            .setTitle("Queue")
            .setItems(items) { _, which -> AudioPlayerManager.playAt(which) }
            .create()
        d.show()
        d.listView.setSelection(maxOf(0, cur - 2))
    }
}
