package com.ashmit.kasturimusic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

private fun fitButton(ctx: Context, res: Int, padDp: Int): ImageButton {
    val b = ImageButton(ctx)
    b.setImageResource(res)
    b.setColorFilter(Color.WHITE)
    b.background = null
    b.scaleType = ImageView.ScaleType.FIT_CENTER
    val p = Ui.dp(ctx, padDp)
    b.setPadding(p, p, p, p)
    return b
}

/**
 * Full-screen Now Playing, Apple Music jaisa: silky blurred artwork background, bada art, left-aligned
 * title + artist, patli progress bar, bade controls, volume slider, neeche icons (lyrics, shuffle, repeat, queue).
 */
class NowPlayingView(ctx: Context, private val onClose: () -> Unit) : FrameLayout(ctx) {

    private val handler = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val slot = FrameLayout(ctx)
    private val bgBlur = ImageView(ctx)
    private var blurId = -1L
    private var artScaleTarget = 1f
    private val queueSheet = QueueSheet(ctx)
    private var anim: ValueAnimator? = null
    private var progress = 0f
    var onProgress: ((Float) -> Unit)? = null
    private val swipe = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val y0 = e1?.y ?: return false
            if (y0 < dp(120) && velocityY > 900f && Math.abs(velocityY) > Math.abs(velocityX)) {
                hide()
                return true
            }
            return false
        }
    })
    private val artView = ImageView(ctx)
    private val lyricsPanel = LyricsPanel(ctx, { ms -> AudioPlayerManager.seekTo(ms) }, { showLyricsMenu() })
    private val titleView = TextView(ctx)
    private val artistView = TextView(ctx)
    private val elapsed = TextView(ctx)
    private val remaining = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val volSeek = SeekBar(ctx)
    private val playBtn = ImageButton(ctx)
    private val prevBtn = fitButton(ctx, R.drawable.ic_rewind, 12)
    private val nextBtn = fitButton(ctx, R.drawable.ic_forward, 12)
    private val starBtn = fitButton(ctx, R.drawable.ic_star, 10)
    private val moreBtn = fitButton(ctx, R.drawable.ic_more, 8)
    private val lyricsBtn = fitButton(ctx, R.drawable.ic_lyrics, 12)
    private val airBtn = fitButton(ctx, R.drawable.ic_airplay, 12)
    private val queueBtn = fitButton(ctx, R.drawable.ic_queue, 12)
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

    /** true = queue khula tha aur band ho gaya (back dabane par pehle queue band hoti hai). */
    fun closeQueue(): Boolean = queueSheet.close()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        swipe.onTouchEvent(ev)
        return super.onInterceptTouchEvent(ev)
    }

    init {
        setBackgroundColor(Ui.BLACK)
        isClickable = true
        visibility = View.GONE
        build()
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    /** Apple jaisi patli rounded bar (thumb chhupa hua). */
    private fun styleBar(sb: SeekBar) {
        val bg = GradientDrawable()
        bg.setColor(Color.argb(80, 255, 255, 255))
        bg.cornerRadius = dp(2).toFloat()
        bg.setSize(0, dp(3))
        val fg = GradientDrawable()
        fg.setColor(Color.WHITE)
        fg.cornerRadius = dp(2).toFloat()
        fg.setSize(0, dp(3))
        val clip = ClipDrawable(fg, Gravity.START, ClipDrawable.HORIZONTAL)
        val ld = LayerDrawable(arrayOf(bg, clip))
        ld.setId(0, android.R.id.background)
        ld.setId(1, android.R.id.progress)
        sb.progressDrawable = ld
        sb.thumb = null
        sb.splitTrack = false
        sb.setPadding(0, dp(18), 0, dp(18))
    }

    private fun setActive(b: ImageButton, on: Boolean) {
        b.setColorFilter(if (on) Color.WHITE else Color.argb(150, 255, 255, 255))
        b.background = if (on) Ui.roundRect(Color.argb(60, 255, 255, 255), dp(14).toFloat()) else null
    }

    private fun build() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        bgBlur.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(bgBlur, FrameLayout.LayoutParams(match, match))
        val scrim = View(context)
        val sg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(25, 0, 0, 0), Color.argb(55, 0, 0, 0), Color.argb(125, 0, 0, 0))
        )
        scrim.background = sg
        addView(scrim, FrameLayout.LayoutParams(match, match))
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(26), dp(4), dp(26), dp(10))
        addView(col, FrameLayout.LayoutParams(match, match))

        // top: chhota handle (tap ya neeche swipe karke band)
        val handleBox = FrameLayout(context)
        val handle = View(context)
        handle.background = Ui.roundRect(Color.argb(110, 255, 255, 255), dp(3).toFloat())
        handleBox.addView(handle, FrameLayout.LayoutParams(dp(40), dp(5), Gravity.CENTER))
        handleBox.setOnClickListener { onClose() }
        col.addView(handleBox, LinearLayout.LayoutParams(match, dp(28)))

        // slot: artwork ya lyrics (bachi hui jagah le leta hai)
        artView.setBackgroundColor(Ui.GRAPHITE)
        Ui.round(artView, dp(14).toFloat())
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
        slp.topMargin = dp(4)
        slp.bottomMargin = dp(10)
        col.addView(slot, slp)

        // title + artist (left-aligned) | heart + more
        val info = LinearLayout(context)
        info.orientation = LinearLayout.HORIZONTAL
        info.gravity = Gravity.CENTER_VERTICAL
        val texts = LinearLayout(context)
        texts.orientation = LinearLayout.VERTICAL
        titleView.textSize = 22f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setTextColor(Color.WHITE)
        titleView.isSingleLine = true
        titleView.ellipsize = TextUtils.TruncateAt.END
        artistView.textSize = 18f
        artistView.setTextColor(Color.argb(170, 255, 255, 255))
        artistView.isSingleLine = true
        artistView.ellipsize = TextUtils.TruncateAt.END
        texts.addView(titleView)
        texts.addView(artistView)
        info.addView(texts, LinearLayout.LayoutParams(0, wrap, 1f))
        info.addView(starBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        moreBtn.background = Ui.roundRect(Color.argb(55, 255, 255, 255), dp(18).toFloat())
        val mlp = LinearLayout.LayoutParams(dp(36), dp(36))
        mlp.leftMargin = dp(6)
        info.addView(moreBtn, mlp)
        col.addView(info, LinearLayout.LayoutParams(match, wrap))

        // progress bar + times
        seek.max = 1000
        styleBar(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = AudioPlayerManager.current?.durationMs ?: 0L
                    val pos = p * dur / 1000L
                    elapsed.text = Ui.fmt(pos)
                    remaining.text = "-" + Ui.fmt(dur - pos)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {
                dragging = true
                sb.animate().scaleY(2.2f).setDuration(120).start()
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val dur = AudioPlayerManager.current?.durationMs ?: 0L
                AudioPlayerManager.seekTo((sb.progress * dur / 1000L).toInt())
                dragging = false
                sb.animate().scaleY(1f).setDuration(160).start()
            }
        })
        val sklp = LinearLayout.LayoutParams(match, wrap)
        sklp.topMargin = dp(6)
        col.addView(seek, sklp)
        val times = LinearLayout(context)
        times.orientation = LinearLayout.HORIZONTAL
        elapsed.textSize = 12f
        elapsed.setTextColor(Color.argb(150, 255, 255, 255))
        remaining.textSize = 12f
        remaining.setTextColor(Color.argb(150, 255, 255, 255))
        remaining.gravity = Gravity.END
        times.addView(elapsed, LinearLayout.LayoutParams(0, wrap, 1f))
        times.addView(remaining, LinearLayout.LayoutParams(0, wrap, 1f))
        col.addView(times, LinearLayout.LayoutParams(match, wrap))

        // controls: rewind | play | forward (bade glyphs, bina circle)
        playBtn.setImageResource(R.drawable.ic_play)
        playBtn.setColorFilter(Color.WHITE)
        playBtn.background = null
        playBtn.scaleType = ImageView.ScaleType.FIT_CENTER
        playBtn.setPadding(dp(4), dp(4), dp(4), dp(4))
        val controls = LinearLayout(context)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.addView(prevBtn, LinearLayout.LayoutParams(dp(64), dp(64)))
        val pl = LinearLayout.LayoutParams(dp(84), dp(84))
        pl.setMargins(dp(26), 0, dp(26), 0)
        controls.addView(playBtn, pl)
        controls.addView(nextBtn, LinearLayout.LayoutParams(dp(64), dp(64)))
        val clp = LinearLayout.LayoutParams(match, wrap)
        clp.topMargin = dp(2)
        col.addView(controls, clp)

        // volume slider
        volSeek.max = try { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } catch (e: Exception) { 15 }
        styleBar(volSeek)
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
        volLow.setColorFilter(Color.argb(150, 255, 255, 255))
        val volHigh = ImageView(context)
        volHigh.setImageResource(R.drawable.ic_volume_high)
        volHigh.setColorFilter(Color.argb(150, 255, 255, 255))
        vol.addView(volLow, LinearLayout.LayoutParams(dp(16), dp(16)))
        val vsl = LinearLayout.LayoutParams(0, wrap, 1f)
        vsl.setMargins(dp(10), 0, dp(10), 0)
        vol.addView(volSeek, vsl)
        vol.addView(volHigh, LinearLayout.LayoutParams(dp(18), dp(18)))
        val vlp = LinearLayout.LayoutParams(match, wrap)
        vlp.topMargin = dp(2)
        col.addView(vol, vlp)

        // neeche icons: lyrics (left) | output (beech) | queue (right)
        val bottom = LinearLayout(context)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.addView(lyricsBtn, LinearLayout.LayoutParams(dp(50), dp(50)))
        bottom.addView(View(context), LinearLayout.LayoutParams(0, dp(1), 1f))
        bottom.addView(airBtn, LinearLayout.LayoutParams(dp(50), dp(50)))
        bottom.addView(View(context), LinearLayout.LayoutParams(0, dp(1), 1f))
        bottom.addView(queueBtn, LinearLayout.LayoutParams(dp(50), dp(50)))
        val blp = LinearLayout.LayoutParams(match, wrap)
        blp.topMargin = dp(4)
        col.addView(bottom, blp)

        // actions
        playBtn.setOnClickListener { AudioPlayerManager.toggle() }
        prevBtn.setOnClickListener { AudioPlayerManager.prev() }
        nextBtn.setOnClickListener { AudioPlayerManager.next() }
        starBtn.setOnClickListener {
            val s = AudioPlayerManager.current
            if (s != null) {
                MusicRepository.toggleFav(s.id)
                AudioPlayerManager.notifyChanged()
            }
        }
        moreBtn.setOnClickListener { showMoreMenu() }
        lyricsBtn.setOnClickListener { toggleLyrics() }
        airBtn.setOnClickListener { openOutputs() }
        queueBtn.setOnClickListener { queueSheet.open() }
        for (v in listOf<View>(playBtn, prevBtn, nextBtn, starBtn, moreBtn, lyricsBtn, airBtn, queueBtn)) {
            Ui.pressable(v)
        }
        setActive(lyricsBtn, false)
        addView(queueSheet, FrameLayout.LayoutParams(match, match))
    }

    /** Agle/pichle gaano ka art pehle se load: gaana badalte hi background turant badle. */
    private fun prefetchNeighbors() {
        val q = AudioPlayerManager.queue
        val i = AudioPlayerManager.index
        for (d in intArrayOf(1, -1, 2)) {
            val s = q.getOrNull(i + d) ?: continue
            ArtworkLoader.prefetchBlur(context, s)
            if (artPx > 0) ArtworkLoader.prefetch(context, s, artPx)
        }
    }

    fun refresh() {
        val s = AudioPlayerManager.current
        titleView.text = s?.title ?: ""
        artistView.text = if (s == null) "" else s.artist
        val id = s?.id ?: -1L
        if (id != shownId && artPx > 0) {
            shownId = id
            Ui.bindArt(artView, s, artPx, 48)
        }
        if (id != blurId) {
            blurId = id
            Ui.bindBlur(bgBlur, s)
            prefetchNeighbors()
        }
        val t = if (AudioPlayerManager.isPlaying) 1f else 0.88f
        if (t != artScaleTarget) {
            artScaleTarget = t
            artView.animate().scaleX(t).scaleY(t).setDuration(380)
                .setInterpolator(DecelerateInterpolator(1.5f)).start()
        }
        if (lyricsMode && isOpen && id != lyricsSongId) loadLyrics()
        playBtn.setImageResource(if (AudioPlayerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        val fav = s != null && MusicRepository.isFav(s.id)
        starBtn.setColorFilter(if (fav) Color.WHITE else Color.argb(110, 255, 255, 255))
        updateProgress()
        updateVolume()
        queueSheet.refresh()
    }

    private fun updateProgress() {
        val dur = AudioPlayerManager.current?.durationMs ?: 0L
        val pos = AudioPlayerManager.position()
        if (!dragging) {
            seek.progress = if (dur > 0) (pos * 1000L / dur).toInt() else 0
            elapsed.text = Ui.fmt(pos.toLong())
            remaining.text = "-" + Ui.fmt(dur - pos)
        }
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
        setActive(lyricsBtn, lyricsMode)
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

    /** AirPlay jaisa: speaker / headphone chunne ke liye phone ki Bluetooth settings kholta hai. */
    private fun openOutputs() {
        try {
            val i = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) { }
    }

    private fun showMoreMenu() {
        val s = AudioPlayerManager.current ?: return
        val fav = MusicRepository.isFav(s.id)
        val rem = AudioPlayerManager.sleepRemainingMs()
        val sleepLabel = if (rem <= 0L) "Sleep timer" else "Sleep timer (" + (rem / 60000L + 1L) + " min left)"
        val items = arrayOf(
            if (fav) "Remove from favourites" else "Add to favourites",
            sleepLabel,
            "Lyrics options"
        )
        dialog().setTitle(s.title).setItems(items) { _, which ->
            when (which) {
                0 -> {
                    MusicRepository.toggleFav(s.id)
                    AudioPlayerManager.notifyChanged()
                }
                1 -> showSleepDialog()
                else -> showLyricsMenu()
            }
        }.show()
    }

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

    private fun showSleepDialog() {
        val labels = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes")
        val mins = intArrayOf(0, 15, 30, 45, 60, 90)
        dialog().setTitle("Sleep timer").setItems(labels) { _, which ->
            AudioPlayerManager.setSleepTimer(mins[which])
        }.show()
    }

    // ---------------- show / hide ----------------
    private fun applyProgress(p: Float) {
        progress = p
        val dm = resources.displayMetrics
        val h = if (height > 0) height else dm.heightPixels
        val w = if (width > 0) width else dm.widthPixels
        pivotX = w / 2f
        pivotY = h.toFloat()
        translationY = (1f - p) * h * 0.92f
        val sc = 0.9f + 0.1f * p
        scaleX = sc
        scaleY = sc
        alpha = (p * 1.8f).coerceIn(0f, 1f)
        onProgress?.invoke(p)
    }

    private fun animateTo(target: Float, duration: Long, interp: TimeInterpolator, onEnd: (() -> Unit)?) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(progress, target)
        a.duration = duration
        a.interpolator = interp
        a.addUpdateListener { applyProgress(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(an: Animator) { cancelled = true }
            override fun onAnimationEnd(an: Animator) {
                if (!cancelled && onEnd != null) onEnd()
            }
        })
        anim = a
        a.start()
    }

    fun show() {
        handler.removeCallbacks(ticker)
        visibility = View.VISIBLE
        applyProgress(progress)
        refresh()
        animateTo(1f, 460L, DecelerateInterpolator(1.9f), null)
        handler.post(ticker)
    }

    fun hide() {
        handler.removeCallbacks(ticker)
        queueSheet.close()
        animateTo(0f, 320L, AccelerateInterpolator(1.3f)) { visibility = View.GONE }
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
}
