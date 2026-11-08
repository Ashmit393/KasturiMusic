package com.ashmit.kasturimusic

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Small draggable system overlay. Playback itself remains owned by PlaybackService/AudioPlayerManager. */
class FloatingPlayerService : Service(), AudioPlayerManager.Listener {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var title: TextView? = null
    private var subtitle: TextView? = null
    private var playButton: TextView? = null
    private var expanded = false
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { updateLabels(); handler.postDelayed(this, 900L) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AudioPlayerManager.init(this)
        if (Build.VERSION.SDK_INT < 23 || !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Display over other apps to use Floating Player", Toast.LENGTH_LONG).show()
            stopSelf(); return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createBubble()
        AudioPlayerManager.addListener(this)
        handler.post(refresh)
    }

    private fun bg(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * resources.displayMetrics.density
        setStroke((1 * resources.displayMetrics.density).toInt().coerceAtLeast(1), 0x557F70C8)
    }

    private fun lp(width: Int, height: Int) = LinearLayout.LayoutParams(
        if (width < 0) width else (width * resources.displayMetrics.density).toInt(),
        if (height < 0) height else (height * resources.displayMetrics.density).toInt()
    )

    private fun createBubble() {
        val bubble = TextView(this).apply {
            text = "♫"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = bg(0xFF6541A5.toInt(), 56f); elevation = 12f
            setOnClickListener { if (expanded) collapse() else expand() }
            setOnTouchListener(dragListener())
        }
        bubbleView = bubble
        params = WindowManager.LayoutParams(
            (58 * resources.displayMetrics.density).toInt(), (58 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 220 }
        try { wm.addView(bubble, params) } catch (_: Exception) { stopSelf() }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(14, 10, 14, 10)
            background = bg(0xF51C1C22.toInt(), 22f); elevation = 16f
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        title = TextView(this).apply { text = "Kasturi Music"; textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); maxLines = 1 }
        subtitle = TextView(this).apply { text = "Floating player"; textSize = 11f; setTextColor(0xFFBDB8C8.toInt()); maxLines = 1 }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(title); labels.addView(subtitle)
        top.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val close = button("×") { collapse() }; top.addView(close, lp(34, 34))
        panel!!.addView(top)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("|◀") { AudioPlayerManager.prev() }, lp(54, 44))
        playButton = button("▶") { AudioPlayerManager.toggle() }; controls.addView(playButton, lp(54, 44))
        controls.addView(button("▶|") { AudioPlayerManager.next() }, lp(54, 44))
        panel!!.addView(controls)
        panel!!.setOnTouchListener(dragListener())
        updateLabels()
    }

    private fun button(textValue: String, action: () -> Unit): TextView = TextView(this).apply {
        text = textValue; textSize = 17f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        setOnClickListener { action() }
        background = bg(0xFF38313F.toInt(), 16f)
    }

    private fun dragListener() = View.OnTouchListener { v, event ->
        val p = params ?: return@OnTouchListener false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { v.tag = floatArrayOf(event.rawX, event.rawY, p.x.toFloat(), p.y.toFloat()); true }
            MotionEvent.ACTION_MOVE -> {
                val a = v.tag as? FloatArray ?: return@OnTouchListener false
                p.x = (a[2] + event.rawX - a[0]).toInt(); p.y = (a[3] + event.rawY - a[1]).toInt()
                try { wm.updateViewLayout(v, p) } catch (_: Exception) { }
                true
            }
            else -> false
        }
    }

    private fun expand() {
        if (expanded || panel == null) return
        try {
            val old = wmViewsBubble()
            if (old != null) wm.removeView(old)
            bubbleView = null
            val p = params ?: return
            p.width = (250 * resources.displayMetrics.density).toInt()
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
            val expandedPanel = panel ?: return
            wm.addView(expandedPanel, p); expanded = true
            updateLabels()
        } catch (_: Exception) { collapse() }
    }

    private fun collapse() {
        if (!expanded) return
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) { }
        expanded = false
        val bubble = TextView(this).apply {
            text = "♫"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = bg(0xFF6541A5.toInt(), 56f); elevation = 12f
            setOnClickListener { expand() }; setOnTouchListener(dragListener())
        }
        val p = params ?: return
        p.width = (58 * resources.displayMetrics.density).toInt(); p.height = p.width
        bubbleView = bubble
        bubbleView = bubble
        try { wm.addView(bubble, p) } catch (_: Exception) { stopSelf() }
    }

    private fun wmViewsBubble(): View? = bubbleView
    private var bubbleView: View? = null

    private fun updateLabels() {
        val song = AudioPlayerManager.current
        title?.text = song?.title ?: "Kasturi Music"
        subtitle?.text = song?.artist ?: "No song playing"
        playButton?.text = if (AudioPlayerManager.isPlaying) "Ⅱ" else "▶"
    }

    override fun onChanged() { handler.post { updateLabels() } }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        AudioPlayerManager.removeListener(this)
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) { }
        try { bubbleView?.let { wm.removeView(it) } } catch (_: Exception) { }
        super.onDestroy()
    }
}
