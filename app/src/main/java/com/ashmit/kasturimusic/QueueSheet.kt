package com.ashmit.kasturimusic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Queue ka bottom sheet: neeche se smooth slide, dim background, rows ek-ek karke aate hain, neeche swipe karke band. */
class QueueSheet(ctx: Context) : FrameLayout(ctx) {
    private val dim = View(ctx)
    private val panel = LinearLayout(ctx)
    private val list = ListView(ctx)
    private val countView = TextView(ctx)
    private val adapter = QAdapter()
    private var sheetOpen = false
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private var animateRows = false
    private var panelH = 0

    val isSheetOpen: Boolean
        get() = sheetOpen

    private fun dp(v: Int) = Ui.dp(context, v)

    init {
        visibility = View.GONE
        panelH = (resources.displayMetrics.heightPixels * 0.62f).toInt()
        dim.setBackgroundColor(Color.BLACK)
        dim.alpha = 0f
        dim.setOnClickListener { close() }
        addView(dim, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val r = dp(26).toFloat()
        val bg = GradientDrawable()
        bg.setColor(Color.argb(250, 24, 24, 27))
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bg.setStroke(dp(1), Color.argb(40, 255, 255, 255))
        panel.background = bg
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true

        val header = LinearLayout(context)
        header.orientation = LinearLayout.VERTICAL
        val handle = View(context)
        handle.background = Ui.roundRect(Color.argb(90, 255, 255, 255), dp(2).toFloat())
        val hlp = LinearLayout.LayoutParams(dp(40), dp(4))
        hlp.gravity = Gravity.CENTER_HORIZONTAL
        hlp.topMargin = dp(10)
        header.addView(handle, hlp)
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(20), dp(12), dp(12), dp(8))
        val title = TextView(context)
        title.text = "Queue"
        title.textSize = 22f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        countView.textSize = 13f
        countView.setTextColor(Ui.DIM)
        row.addView(countView)
        val done = TextView(context)
        done.text = "Done"
        done.textSize = 15f
        done.setTextColor(Ui.SILVER)
        done.setPadding(dp(16), dp(8), dp(12), dp(8))
        done.setOnClickListener { close() }
        row.addView(done)
        header.addView(row)
        var downY = 0f
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY
                    anim?.cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = maxOf(0f, e.rawY - downY)
                    panel.translationY = dy
                    dim.alpha = 0.6f * (1f - dy / panelH).coerceIn(0f, 1f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (panel.translationY > panelH * 0.22f) {
                        progress = (1f - panel.translationY / panelH).coerceIn(0f, 1f)
                        close()
                    } else {
                        progress = 1f
                        panel.animate().translationY(0f).setDuration(220).start()
                        dim.animate().alpha(0.6f).setDuration(220).start()
                    }
                    true
                }
                else -> false
            }
        }
        panel.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        list.divider = null
        list.setSelector(android.R.color.transparent)
        list.isVerticalScrollBarEnabled = false
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            AudioPlayerManager.playAt(pos)
            adapter.notifyDataSetChanged()
        }
        panel.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(panel, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelH, Gravity.BOTTOM))
        applyProgress(0f)
    }

    private fun applyProgress(p: Float) {
        progress = p
        dim.alpha = 0.6f * p
        panel.translationY = (1f - p) * panelH
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

    fun open() {
        if (sheetOpen) return
        if (AudioPlayerManager.queue.isEmpty()) return
        sheetOpen = true
        visibility = View.VISIBLE
        countView.text = AudioPlayerManager.queue.size.toString() + " songs"
        animateRows = true
        adapter.notifyDataSetChanged()
        list.setSelection(maxOf(0, AudioPlayerManager.index - 2))
        applyProgress(0f)
        animateTo(1f, 460L, DecelerateInterpolator(2.2f), null)
        postDelayed({ animateRows = false }, 1000L)
    }

    /** true = queue khula tha aur ab band ho raha hai. */
    fun close(): Boolean {
        if (!sheetOpen) return false
        sheetOpen = false
        animateTo(0f, 300L, AccelerateInterpolator(1.4f)) { visibility = View.GONE }
        return true
    }

    fun refresh() {
        if (sheetOpen) {
            countView.text = AudioPlayerManager.queue.size.toString() + " songs"
            adapter.notifyDataSetChanged()
        }
    }

    private inner class QAdapter : BaseAdapter() {
        override fun getCount(): Int = AudioPlayerManager.queue.size
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h: Holder
            val v: View
            if (convertView == null) {
                h = Holder(context)
                v = h.root
                v.tag = h
            } else {
                v = convertView
                h = convertView.tag as Holder
            }
            val s = AudioPlayerManager.queue[position]
            val cur = position == AudioPlayerManager.index
            h.num.text = (position + 1).toString()
            h.title.text = s.title
            h.title.setTextColor(if (cur) Color.WHITE else Color.argb(220, 255, 255, 255))
            h.title.setTypeface(null, if (cur) Typeface.BOLD else Typeface.NORMAL)
            h.sub.text = s.artist
            h.num.setTextColor(if (cur) Ui.SILVER else Ui.DIM)
            Ui.bindArt(h.art, s, 120, 10)
            v.animate().cancel()
            if (animateRows) {
                v.alpha = 0f
                v.translationY = dp(24).toFloat()
                v.animate().alpha(1f).translationY(0f).setDuration(300)
                    .setStartDelay(120L + minOf(position, 9) * 32L)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
            } else {
                v.alpha = 1f
                v.translationY = 0f
            }
            return v
        }
    }

    private class Holder(ctx: Context) {
        val num = TextView(ctx)
        val art = ImageView(ctx)
        val title = TextView(ctx)
        val sub = TextView(ctx)
        val root = LinearLayout(ctx)

        init {
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * d + 0.5f).toInt()
            num.textSize = 13f
            num.gravity = Gravity.CENTER
            art.setBackgroundColor(Ui.GRAPHITE2)
            Ui.round(art, dp(8).toFloat())
            title.textSize = 15f
            title.isSingleLine = true
            title.ellipsize = TextUtils.TruncateAt.END
            sub.textSize = 12f
            sub.setTextColor(Ui.DIM)
            sub.isSingleLine = true
            sub.ellipsize = TextUtils.TruncateAt.END
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.setPadding(dp(12), dp(6), dp(16), dp(6))
            root.addView(num, LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(art, LinearLayout.LayoutParams(dp(44), dp(44)))
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.addView(title)
            col.addView(sub)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.leftMargin = dp(12)
            root.addView(col, lp)
        }
    }
}
