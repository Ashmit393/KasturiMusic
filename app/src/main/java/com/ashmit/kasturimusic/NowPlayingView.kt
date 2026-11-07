package com.ashmit.kasturimusic

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Full-screen Now Playing overlay (Apple Music jaisa simple layout). */
class NowPlayingView(ctx: Context, private val onClose: () -> Unit) : FrameLayout(ctx) {

    private val handler = Handler(Looper.getMainLooper())
    private val artView = ImageView(ctx)
    private val titleView = TextView(ctx)
    private val artistView = TextView(ctx)
    private val elapsed = TextView(ctx)
    private val total = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val playBtn = ImageButton(ctx)
    private val shuffleBtn = Ui.iconButton(ctx, R.drawable.ic_shuffle, Ui.DIM)
    private val prevBtn = Ui.iconButton(ctx, R.drawable.ic_prev, Color.WHITE, 12)
    private val nextBtn = Ui.iconButton(ctx, R.drawable.ic_next, Color.WHITE, 12)
    private val repeatBtn = Ui.iconButton(ctx, R.drawable.ic_repeat, Ui.DIM)
    private val heartBtn = Ui.iconButton(ctx, R.drawable.ic_heart_outline, Ui.SILVER)
    private val queueBtn = TextView(ctx)
    private var dragging = false
    private var shownId = -1L
    private var artPx = 400

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
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

    private fun build() {
        val dm = resources.displayMetrics
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(24), dp(8), dp(24), dp(16))
        addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

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
        top.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(View(context), LinearLayout.LayoutParams(dp(48), dp(48)))
        col.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // artwork
        artPx = minOf(dm.widthPixels - dp(80), (dm.heightPixels * 0.36f).toInt())
        artView.setBackgroundColor(Ui.GRAPHITE)
        Ui.round(artView, dp(16).toFloat())
        val alp = LinearLayout.LayoutParams(artPx, artPx)
        alp.topMargin = dp(12)
        col.addView(artView, alp)

        // title + artist
        titleView.textSize = 22f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setTextColor(Color.WHITE)
        titleView.gravity = Gravity.CENTER
        titleView.isSingleLine = true
        titleView.ellipsize = TextUtils.TruncateAt.END
        val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.topMargin = dp(20)
        col.addView(titleView, tlp)
        artistView.textSize = 16f
        artistView.setTextColor(Ui.SILVER)
        artistView.gravity = Gravity.CENTER
        artistView.isSingleLine = true
        artistView.ellipsize = TextUtils.TruncateAt.END
        col.addView(artistView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // seek bar
        seek.max = 1000
        seek.progressTintList = ColorStateList.valueOf(Ui.SILVER)
        seek.thumbTintList = ColorStateList.valueOf(Ui.SILVER)
        seek.progressBackgroundTintList = ColorStateList.valueOf(Ui.GRAPHITE2)
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
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp.topMargin = dp(16)
        col.addView(seek, slp)

        val times = LinearLayout(context)
        times.orientation = LinearLayout.HORIZONTAL
        elapsed.textSize = 12f
        elapsed.setTextColor(Ui.DIM)
        total.textSize = 12f
        total.setTextColor(Ui.DIM)
        total.gravity = Gravity.END
        times.addView(elapsed, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(total, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(times, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // controls
        playBtn.setImageResource(R.drawable.ic_play)
        playBtn.setColorFilter(Color.BLACK)
        playBtn.background = Ui.roundRect(Ui.SILVER, dp(36).toFloat())
        playBtn.scaleType = ImageView.ScaleType.CENTER_INSIDE
        playBtn.setPadding(dp(18), dp(18), dp(18), dp(18))

        val controls = LinearLayout(context)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER_VERTICAL
        controls.addView(shuffleBtn, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(prevBtn, LinearLayout.LayoutParams(0, dp(56), 1f))
        val plp = LinearLayout.LayoutParams(dp(72), dp(72))
        plp.setMargins(dp(8), 0, dp(8), 0)
        controls.addView(playBtn, plp)
        controls.addView(nextBtn, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(repeatBtn, LinearLayout.LayoutParams(0, dp(56), 1f))
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.topMargin = dp(12)
        col.addView(controls, clp)

        // bottom row: favourite + queue
        queueBtn.text = "Queue"
        queueBtn.textSize = 15f
        queueBtn.setTextColor(Ui.SILVER)
        queueBtn.gravity = Gravity.CENTER
        val bottom = LinearLayout(context)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.addView(heartBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(queueBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.topMargin = dp(8)
        col.addView(bottom, blp)

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
        queueBtn.setOnClickListener { showQueue() }
    }

    fun refresh() {
        val s = AudioPlayerManager.current
        titleView.text = s?.title ?: ""
        artistView.text = if (s == null) "" else s.artist + " \u2022 " + s.album
        val id = s?.id ?: -1L
        if (id != shownId) {
            shownId = id
            Ui.bindArt(artView, s, artPx, 48)
        }
        playBtn.setImageResource(if (AudioPlayerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        shuffleBtn.setColorFilter(if (AudioPlayerManager.shuffle) Color.WHITE else Ui.DIM)
        val rep = AudioPlayerManager.repeat
        repeatBtn.setImageResource(if (rep == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
        repeatBtn.setColorFilter(if (rep == 0) Ui.DIM else Color.WHITE)
        val fav = s != null && MusicRepository.isFav(s.id)
        heartBtn.setImageResource(if (fav) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        updateProgress()
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
        val d = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Queue")
            .setItems(items) { _, which -> AudioPlayerManager.playAt(which) }
            .create()
        d.show()
        d.listView.setSelection(maxOf(0, cur - 2))
    }
}
