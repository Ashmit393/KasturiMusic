package com.ashmit.kasturimusic

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView

/** Apple-style lyrics: chalti line badi + safed + halki glow, baaki lines dhundhli. */
class LyricsPanel(
    ctx: Context,
    private val onSeek: (Int) -> Unit,
    private val onMenu: () -> Unit
) : FrameLayout(ctx) {

    private val list = ListView(ctx)
    private val msg = TextView(ctx)
    private val menu = TextView(ctx)
    private val spacerTop = View(ctx)
    private val spacerBottom = View(ctx)
    private val adapter = LineAdapter()
    private var lyrics: Lyrics? = null
    private var current = -1
    private var lastTouch = 0L

    private val cMid = Color.argb(160, 255, 255, 255)
    private val cDim = Color.argb(85, 255, 255, 255)
    private val cPlain = Color.argb(230, 255, 255, 255)
    private val cGlow = Color.argb(235, 255, 255, 255)

    private fun dp(v: Int) = Ui.dp(context, v)

    init {
        list.divider = null
        list.setSelector(android.R.color.transparent)
        list.isVerticalScrollBarEnabled = false
        list.overScrollMode = View.OVER_SCROLL_NEVER
        spacerTop.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        spacerBottom.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        list.addHeaderView(spacerTop, null, false)
        list.addFooterView(spacerBottom, null, false)
        list.adapter = adapter
        list.setOnTouchListener { _, _ ->
            lastTouch = SystemClock.uptimeMillis()
            false
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val l = lyrics
            val i = pos - 1
            if (l != null && l.synced && i in l.lines.indices) onSeek(l.times[i].toInt())
        }
        list.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val h = bottom - top
            if (h > 0 && spacerTop.layoutParams.height != h / 3) {
                list.post {
                    spacerTop.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h / 3)
                    spacerBottom.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h * 2 / 3)
                    list.requestLayout()
                }
            }
        }
        list.setPadding(dp(16), 0, dp(16), 0)
        addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        msg.textSize = 16f
        msg.setTextColor(Ui.DIM)
        msg.gravity = Gravity.CENTER
        msg.setPadding(dp(24), dp(24), dp(24), dp(24))
        msg.visibility = View.GONE
        addView(msg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        menu.text = "Edit"
        menu.textSize = 12f
        menu.setTextColor(Ui.SILVER)
        menu.background = Ui.roundRect(Ui.GRAPHITE, dp(12).toFloat())
        menu.setPadding(dp(14), dp(6), dp(14), dp(6))
        menu.setOnClickListener { onMenu() }
        val mlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mlp.gravity = Gravity.TOP or Gravity.END
        mlp.setMargins(0, dp(4), dp(4), 0)
        addView(menu, mlp)
    }

    fun setLyrics(l: Lyrics?) {
        lyrics = l
        current = -1
        if (l == null) {
            setStatus("Lyrics nahi mili.\nUpar Edit dabake online dhoondho ya khud paste karo.")
            return
        }
        msg.visibility = View.GONE
        list.visibility = View.VISIBLE
        adapter.notifyDataSetChanged()
        list.setSelection(0)
    }

    fun setStatus(text: String) {
        lyrics = null
        current = -1
        msg.text = text
        msg.visibility = View.VISIBLE
        list.visibility = View.GONE
        adapter.notifyDataSetChanged()
    }

    /** Har 250ms par call hota hai. */
    fun tick(posMs: Int) {
        val l = lyrics ?: return
        if (!l.synced) return
        val idx = l.indexAt(posMs.toLong() + 250L)
        if (idx == current) return
        current = idx
        adapter.notifyDataSetChanged()
        if (idx >= 0 && SystemClock.uptimeMillis() - lastTouch > 3000L) {
            list.smoothScrollToPositionFromTop(idx + 1, list.height / 3, 350)
        }
    }

    private inner class LineAdapter : BaseAdapter() {
        override fun getCount(): Int = lyrics?.lines?.size ?: 0
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv: TextView
            if (convertView is TextView) {
                tv = convertView
            } else {
                tv = TextView(context)
                tv.setPadding(dp(4), dp(10), dp(4), dp(10))
                tv.setTypeface(null, Typeface.BOLD)
            }
            val l = lyrics ?: return tv
            tv.text = l.lines[position]
            if (!l.synced) {
                tv.textSize = 18f
                tv.setTextColor(cPlain)
                tv.setShadowLayer(0f, 0f, 0f, 0)
            } else if (position == current) {
                tv.textSize = 26f
                tv.setTextColor(Color.WHITE)
                tv.setShadowLayer(dp(12).toFloat(), 0f, 0f, cGlow)
            } else {
                tv.textSize = 22f
                tv.setTextColor(if (Math.abs(position - current) == 1) cMid else cDim)
                tv.setShadowLayer(0f, 0f, 0f, 0)
            }
            return tv
        }
    }
}
