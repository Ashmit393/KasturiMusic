package com.ashmit.kasturimusic

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Dusre apps ke upar floating player.
 * - App se bahar aate hi bubble wahin dikhta hai jahan app ke andar mini player ka bubble hota hai.
 * - Bubble par tap = wahi mini player card (art, naam, prev / play-pause / next) smooth expand hota hai.
 * - Card ke baahar tap ya naam par tap = wapas bubble. Drag karke kahin bhi rakh sakte ho.
 */
class FloatingPlayerService : Service(), AudioPlayerManager.Listener {

    companion object {
        /** MainActivity app se bahar jaate waqt mini player ki asli screen position yahan likhta hai. */
        var startX = -1
        var startY = -1
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val evalRunnable = Runnable { evaluateNow() }
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var barDragged = false
    private var bubbleX = 0
    private var bubbleY = 0
    private var shownId = -1L
    private var snapAnim: ValueAnimator? = null

    private var bubble: FrameLayout? = null
    private var bubbleArt: ImageView? = null
    private var bubbleBlur: ImageView? = null
    private var bar: FrameLayout? = null
    private var barArt: ImageView? = null
    private var barBlur: ImageView? = null
    private var titleView: TextView? = null
    private var artistView: TextView? = null
    private var playBtn: ImageButton? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun barWidth(): Int {
        val w = resources.displayMetrics.widthPixels
        return minOf(w - dp(20), dp(380))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AudioPlayerManager.init(this)
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        AudioPlayerManager.addListener(this)
        scheduleEval()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleEval()
        return START_NOT_STICKY
    }

    override fun onChanged() {
        scheduleEval()
    }

    override fun onDestroy() {
        handler.removeCallbacks(evalRunnable)
        if (::wm.isInitialized) {
            AudioPlayerManager.removeListener(this)
            hide()
        }
        super.onDestroy()
    }

    private fun scheduleEval() {
        handler.removeCallbacks(evalRunnable)
        handler.postDelayed(evalRunnable, 250L)
    }

    /** Overlay tab dikhta hai jab: setting on + permission + app screen par nahi + gaana maujood. */
    private fun evaluateNow() {
        val want = SettingsManager.floatingPlayer && Settings.canDrawOverlays(this) &&
            !AudioPlayerManager.appVisible && AudioPlayerManager.current != null
        if (want) {
            if (root == null) show()
            refreshViews()
        } else if (root != null) {
            hide()
        }
    }

    // ---------------- show / hide ----------------
    private fun show() {
        val r = buildViews()
        val dm = resources.displayMetrics
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = if (startX >= 0) startX else (dm.widthPixels - dp(64)) / 2
        p.y = if (startY >= 0) startY else dm.heightPixels - dp(150)
        try {
            wm.addView(r, p)
            root = r
            params = p
            expanded = false
            barDragged = false
            shownId = -1L
            val b = bubble
            if (b != null) {
                b.alpha = 0f
                b.scaleX = 0.5f
                b.scaleY = 0.5f
                b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340)
                    .setInterpolator(OvershootInterpolator(1.6f)).start()
            }
        } catch (e: Exception) {
            root = null
            params = null
        }
    }

    private fun hide() {
        snapAnim?.cancel()
        val r = root
        if (r != null) {
            try { wm.removeView(r) } catch (e: Exception) { }
        }
        root = null
        params = null
        expanded = false
    }

    // ---------------- views ----------------
    private fun buildViews(): FrameLayout {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val r = FrameLayout(this)
        r.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE && expanded) setExpanded(false)
            false
        }

        // ---- bubble: in-app collapsed mini player jaisa (64dp gol, blur + art) ----
        val b = FrameLayout(this)
        b.setBackgroundColor(Ui.GRAPHITE)
        b.outlineProvider = RoundOutline(dp(32).toFloat())
        b.clipToOutline = true
        val bb = ImageView(this)
        bb.scaleType = ImageView.ScaleType.CENTER_CROP
        b.addView(bb, FrameLayout.LayoutParams(match, match))
        val bs = View(this)
        bs.setBackgroundColor(Color.argb(110, 0, 0, 0))
        b.addView(bs, FrameLayout.LayoutParams(match, match))
        val ba = ImageView(this)
        ba.setBackgroundColor(Ui.GRAPHITE2)
        ba.outlineProvider = RoundOutline(dp(24).toFloat())
        ba.clipToOutline = true
        b.addView(ba, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        val bEdge = View(this)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.TRANSPARENT)
        bg.setStroke(dp(1), Color.argb(70, 255, 255, 255))
        bEdge.background = bg
        b.addView(bEdge, FrameLayout.LayoutParams(match, match))
        r.addView(b, FrameLayout.LayoutParams(dp(64), dp(64)))
        bubble = b
        bubbleBlur = bb
        bubbleArt = ba
        attachDrag(b) { setExpanded(true) }

        // ---- expanded card: in-app mini player bar jaisa ----
        val c = FrameLayout(this)
        c.setBackgroundColor(Ui.GRAPHITE)
        c.outlineProvider = RoundOutline(dp(18).toFloat())
        c.clipToOutline = true
        c.visibility = View.GONE
        val cb = ImageView(this)
        cb.scaleType = ImageView.ScaleType.CENTER_CROP
        c.addView(cb, FrameLayout.LayoutParams(match, match))
        val cs = View(this)
        cs.setBackgroundColor(Color.argb(120, 0, 0, 0))
        c.addView(cs, FrameLayout.LayoutParams(match, match))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), dp(8), dp(8), dp(8))
        val art = ImageView(this)
        art.setBackgroundColor(Ui.GRAPHITE2)
        art.outlineProvider = RoundOutline(dp(8).toFloat())
        art.clipToOutline = true
        art.setOnClickListener { openApp() }
        row.addView(art, LinearLayout.LayoutParams(dp(48), dp(48)))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.textSize = 15f
        t.setTextColor(Color.WHITE)
        t.isSingleLine = true
        t.ellipsize = TextUtils.TruncateAt.END
        t.setTypeface(null, Typeface.BOLD)
        val a = TextView(this)
        a.textSize = 12f
        a.setTextColor(Ui.DIM)
        a.isSingleLine = true
        a.ellipsize = TextUtils.TruncateAt.END
        texts.addView(t)
        texts.addView(a)
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = dp(10)
        row.addView(texts, tlp)
        val prev = Ui.iconButton(this, R.drawable.ic_prev, Color.WHITE, 10)
        prev.setOnClickListener { AudioPlayerManager.prev() }
        Ui.pressable(prev)
        row.addView(prev, LinearLayout.LayoutParams(dp(40), dp(44)))
        val play = Ui.iconButton(this, R.drawable.ic_play, Color.WHITE, 10)
        play.setOnClickListener { AudioPlayerManager.toggle() }
        Ui.pressable(play)
        row.addView(play, LinearLayout.LayoutParams(dp(40), dp(44)))
        val next = Ui.iconButton(this, R.drawable.ic_next, Color.WHITE, 10)
        next.setOnClickListener { AudioPlayerManager.next() }
        Ui.pressable(next)
        row.addView(next, LinearLayout.LayoutParams(dp(44), dp(44)))
        c.addView(row, FrameLayout.LayoutParams(match, match))
        val edge = View(this)
        val eg = GradientDrawable()
        eg.setColor(Color.TRANSPARENT)
        eg.cornerRadius = dp(18).toFloat()
        eg.setStroke(dp(1), Color.argb(60, 255, 255, 255))
        edge.background = eg
        c.addView(edge, FrameLayout.LayoutParams(match, match))
        attachDrag(c) { setExpanded(false) }
        r.addView(c, FrameLayout.LayoutParams(barWidth(), dp(64)))
        bar = c
        barBlur = cb
        barArt = art
        titleView = t
        artistView = a
        playBtn = play
        return r
    }

    private fun refreshViews() {
        val s = AudioPlayerManager.current ?: return
        if (s.id != shownId) {
            shownId = s.id
            bubbleArt?.let { Ui.bindArt(it, s, 128, 10) }
            barArt?.let { Ui.bindArt(it, s, 128, 10) }
            bubbleBlur?.let { Ui.bindBlur(it, s) }
            barBlur?.let { Ui.bindBlur(it, s) }
        }
        titleView?.text = s.title
        artistView?.text = s.artist
        playBtn?.setImageResource(if (AudioPlayerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    // ---------------- drag / tap / snap ----------------
    private fun attachDrag(v: View, onTap: () -> Unit) {
        var downX = 0f
        var downY = 0f
        var startPx = 0
        var startPy = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            val p = params
            if (p == null) {
                false
            } else {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startPx = p.x
                        startPy = p.y
                        moved = false
                        snapAnim?.cancel()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (!moved && (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6))) moved = true
                        if (moved) {
                            p.x = startPx + dx.toInt()
                            p.y = startPy + dy.toInt()
                            if (expanded) barDragged = true
                            val r = root
                            if (r != null) {
                                try { wm.updateViewLayout(r, p) } catch (ex: Exception) { }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) onTap() else snap()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        snap()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /** Drag ke baad: bubble kinare par chipakta hai, card screen ke andar rehta hai. */
    private fun snap() {
        val r = root ?: return
        val p = params ?: return
        val dm = resources.displayMetrics
        val w = if (expanded) barWidth() else dp(64)
        val h = dp(64)
        val maxX = maxOf(0, dm.widthPixels - w)
        val edgeGap = dp(6)
        val targetX = if (expanded) {
            p.x.coerceIn(0, maxX)
        } else if (p.x + w / 2 < dm.widthPixels / 2) {
            edgeGap
        } else {
            maxX - edgeGap
        }
        val targetY = p.y.coerceIn(dp(24), maxOf(dp(24), dm.heightPixels - h - dp(48)))
        val sx = p.x
        val sy = p.y
        snapAnim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 280
        a.interpolator = DecelerateInterpolator(1.8f)
        a.addUpdateListener {
            val f = it.animatedValue as Float
            p.x = (sx + (targetX - sx) * f).toInt()
            p.y = (sy + (targetY - sy) * f).toInt()
            try { wm.updateViewLayout(r, p) } catch (ex: Exception) { }
        }
        snapAnim = a
        a.start()
    }

    // ---------------- expand / collapse ----------------
    private fun setExpanded(e: Boolean) {
        val b = bubble ?: return
        val c = bar ?: return
        val p = params ?: return
        val r = root ?: return
        if (e == expanded) return
        val dm = resources.displayMetrics
        val bw = barWidth()
        if (e) {
            expanded = true
            barDragged = false
            bubbleX = p.x
            bubbleY = p.y
            val newX = ((dm.widthPixels - bw) / 2).coerceAtLeast(0)
            val bubbleCenter = bubbleX + dp(32)
            b.visibility = View.GONE
            c.visibility = View.VISIBLE
            p.x = newX
            p.y = p.y.coerceIn(dp(24), maxOf(dp(24), dm.heightPixels - dp(64) - dp(48)))
            try { wm.updateViewLayout(r, p) } catch (ex: Exception) { }
            c.pivotX = (bubbleCenter - newX).toFloat().coerceIn(0f, bw.toFloat())
            c.pivotY = dp(32).toFloat()
            c.alpha = 0f
            c.scaleX = dp(64).toFloat() / bw
            c.scaleY = 0.9f
            c.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(OvershootInterpolator(0.9f)).start()
        } else {
            expanded = false
            c.animate().alpha(0f).scaleX(dp(64).toFloat() / bw).scaleY(0.9f).setDuration(200)
                .setInterpolator(DecelerateInterpolator(1.4f)).withEndAction {
                    c.visibility = View.GONE
                    val pp = params
                    if (pp != null) {
                        pp.x = if (barDragged) (pp.x + bw / 2 - dp(32)).coerceIn(0, maxOf(0, dm.widthPixels - dp(64))) else bubbleX
                        if (!barDragged) pp.y = bubbleY
                        try { wm.updateViewLayout(r, pp) } catch (ex: Exception) { }
                    }
                    b.visibility = View.VISIBLE
                    b.alpha = 0f
                    b.scaleX = 0.6f
                    b.scaleY = 0.6f
                    b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(OvershootInterpolator(1.5f)).start()
                    if (barDragged) snap()
                }.start()
        }
    }

    private fun openApp() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }
}
