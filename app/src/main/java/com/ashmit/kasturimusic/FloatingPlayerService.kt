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
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Dusre apps ke upar chalne wala floating player: gol bubble (album art) -> tap par expand,
 * prev / play-pause / next, drag karke kahin bhi rakho. App screen par ho to chhup jaata hai.
 * Playback AudioPlayerManager/PlaybackService hi chalate hain.
 */
class FloatingPlayerService : Service(), AudioPlayerManager.Listener {

    companion object {
        private var lastX = 0
        private var lastY = -1
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val evalRunnable = Runnable { evaluateNow() }
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var shownId = -1L
    private var snapAnim: ValueAnimator? = null

    private var bubble: FrameLayout? = null
    private var bubbleArt: ImageView? = null
    private var card: FrameLayout? = null
    private var cardArt: ImageView? = null
    private var cardBlur: ImageView? = null
    private var titleView: TextView? = null
    private var artistView: TextView? = null
    private var playBtn: ImageButton? = null

    private fun dp(v: Int) = Ui.dp(this, v)

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
        AudioPlayerManager.removeListener(this)
        hide()
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

    // ---------------- build ----------------
    private fun show() {
        val r = buildViews()
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = lastX
        p.y = if (lastY < 0) dp(220) else lastY
        try {
            wm.addView(r, p)
            root = r
            params = p
            expanded = false
            shownId = -1L
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

    private fun buildViews(): FrameLayout {
        val r = FrameLayout(this)

        // ---- bubble ----
        val b = FrameLayout(this)
        val ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        ring.setColor(Ui.GRAPHITE)
        ring.setStroke(dp(1), Color.argb(90, 255, 255, 255))
        b.background = ring
        val ba = ImageView(this)
        ba.setBackgroundColor(Ui.GRAPHITE2)
        ba.outlineProvider = RoundOutline(dp(26).toFloat())
        ba.clipToOutline = true
        b.addView(ba, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
        r.addView(b, FrameLayout.LayoutParams(dp(60), dp(60)))
        bubble = b
        bubbleArt = ba
        attachDrag(b) { setExpanded(true) }

        // ---- expanded card ----
        val c = FrameLayout(this)
        c.setBackgroundColor(Ui.GRAPHITE)
        c.outlineProvider = RoundOutline(dp(22).toFloat())
        c.clipToOutline = true
        c.visibility = View.GONE
        val blur = ImageView(this)
        blur.scaleType = ImageView.ScaleType.CENTER_CROP
        c.addView(blur, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val scrim = View(this)
        scrim.setBackgroundColor(Color.argb(140, 0, 0, 0))
        c.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(12), dp(12), dp(10))

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.gravity = Gravity.CENTER_VERTICAL
        val art = ImageView(this)
        art.setBackgroundColor(Ui.GRAPHITE2)
        art.outlineProvider = RoundOutline(dp(12).toFloat())
        art.clipToOutline = true
        art.setOnClickListener { openApp() }
        row1.addView(art, LinearLayout.LayoutParams(dp(48), dp(48)))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.textSize = 14f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Color.WHITE)
        t.isSingleLine = true
        t.ellipsize = TextUtils.TruncateAt.END
        val a = TextView(this)
        a.textSize = 12f
        a.setTextColor(Ui.DIM)
        a.isSingleLine = true
        a.ellipsize = TextUtils.TruncateAt.END
        texts.addView(t)
        texts.addView(a)
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = dp(10)
        row1.addView(texts, tlp)
        val close = Ui.iconButton(this, R.drawable.ic_close, Ui.DIM, 8)
        close.setOnClickListener { setExpanded(false) }
        row1.addView(close, LinearLayout.LayoutParams(dp(36), dp(36)))
        col.addView(row1)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.gravity = Gravity.CENTER
        val prev = Ui.iconButton(this, R.drawable.ic_prev, Color.WHITE, 10)
        prev.setOnClickListener { AudioPlayerManager.prev() }
        Ui.pressable(prev)
        val play = Ui.iconButton(this, R.drawable.ic_play, Color.BLACK, 14)
        play.background = Ui.roundRect(Ui.SILVER, dp(26).toFloat())
        play.setOnClickListener { AudioPlayerManager.toggle() }
        Ui.pressable(play)
        val next = Ui.iconButton(this, R.drawable.ic_next, Color.WHITE, 10)
        next.setOnClickListener { AudioPlayerManager.next() }
        Ui.pressable(next)
        row2.addView(prev, LinearLayout.LayoutParams(dp(48), dp(44)))
        val plp = LinearLayout.LayoutParams(dp(52), dp(52))
        plp.setMargins(dp(14), 0, dp(14), 0)
        row2.addView(play, plp)
        row2.addView(next, LinearLayout.LayoutParams(dp(48), dp(44)))
        val r2lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        r2lp.topMargin = dp(6)
        col.addView(row2, r2lp)

        c.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val edge = View(this)
        val eg = GradientDrawable()
        eg.setColor(Color.TRANSPARENT)
        eg.cornerRadius = dp(22).toFloat()
        eg.setStroke(dp(1), Color.argb(60, 255, 255, 255))
        edge.background = eg
        c.addView(edge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        attachDrag(c) { }

        r.addView(c, FrameLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.WRAP_CONTENT))
        card = c
        cardArt = art
        cardBlur = blur
        titleView = t
        artistView = a
        playBtn = play
        return r
    }

    private fun refreshViews() {
        val s = AudioPlayerManager.current ?: return
        if (s.id != shownId) {
            shownId = s.id
            bubbleArt?.let { Ui.bindArt(it, s, 128, 12) }
            cardArt?.let { Ui.bindArt(it, s, 128, 10) }
            cardBlur?.let { Ui.bindBlur(it, s) }
        }
        titleView?.text = s.title
        artistView?.text = s.artist
        playBtn?.setImageResource(if (AudioPlayerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    // ---------------- drag / tap / snap ----------------
    private fun attachDrag(v: View, onTap: () -> Unit) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
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
                        startX = p.x
                        startY = p.y
                        moved = false
                        snapAnim?.cancel()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (!moved && (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6))) moved = true
                        if (moved) {
                            p.x = startX + dx.toInt()
                            p.y = startY + dy.toInt()
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

    /** Bubble ko kinare tak chipkata hai; expanded card ko screen ke andar rakhta hai. */
    private fun snap() {
        val r = root ?: return
        val p = params ?: return
        val dm = resources.displayMetrics
        val w = if (r.width > 0) r.width else dp(60)
        val h = if (r.height > 0) r.height else dp(60)
        val maxX = maxOf(0, dm.widthPixels - w)
        val targetX = if (expanded) p.x.coerceIn(0, maxX) else if (p.x + w / 2 < dm.widthPixels / 2) 0 else maxX
        val targetY = p.y.coerceIn(0, maxOf(0, dm.heightPixels - h - dp(48)))
        val sx = p.x
        val sy = p.y
        snapAnim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 260
        a.interpolator = OvershootInterpolator(0.8f)
        a.addUpdateListener {
            val f = it.animatedValue as Float
            p.x = (sx + (targetX - sx) * f).toInt()
            p.y = (sy + (targetY - sy) * f).toInt()
            try { wm.updateViewLayout(r, p) } catch (ex: Exception) { }
            lastX = p.x
            lastY = p.y
        }
        snapAnim = a
        a.start()
    }

    // ---------------- expand / collapse ----------------
    private fun setExpanded(e: Boolean) {
        val b = bubble ?: return
        val c = card ?: return
        if (e == expanded) return
        expanded = e
        if (e) {
            b.visibility = View.GONE
            c.visibility = View.VISIBLE
            c.pivotX = dp(30).toFloat()
            c.pivotY = dp(30).toFloat()
            c.alpha = 0f
            c.scaleX = 0.6f
            c.scaleY = 0.6f
            c.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(OvershootInterpolator(1.1f)).start()
            handler.postDelayed({ snap() }, 60L)
        } else {
            c.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(180).withEndAction {
                c.visibility = View.GONE
                b.visibility = View.VISIBLE
                b.alpha = 0f
                b.animate().alpha(1f).setDuration(160).start()
                snap()
            }.start()
        }
    }

    private fun openApp() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }
}
