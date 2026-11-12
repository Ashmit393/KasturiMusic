package com.ashmit.kasturimusic

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private const val TAB_HOME = 0
private const val TAB_SONGS = 1
private const val TAB_PLAYLISTS = 5
private const val TAB_FAV = 6
private const val TAB_RECENT = 7

private class Row(
    val title: String,
    val sub: String,
    val song: Song?,
    val group: List<Song>?,
    val artSong: Song?,
    val playlist: String? = null,
    val isNew: Boolean = false,
    val header: Boolean = false,
    val section: List<Song>? = null
)

private class RowHolder(ctx: Context) {
    val art = ImageView(ctx)
    val title = TextView(ctx)
    val sub = TextView(ctx)
    val root = LinearLayout(ctx)

    init {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d + 0.5f).toInt()
        art.setBackgroundColor(Theme.card)
        Ui.round(art, dp(8).toFloat())
        title.textSize = 16f
        title.isSingleLine = true
        title.ellipsize = TextUtils.TruncateAt.END
        sub.textSize = 13f
        sub.setTextColor(Theme.sub)
        sub.isSingleLine = true
        sub.ellipsize = TextUtils.TruncateAt.END
        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER_VERTICAL
        root.setPadding(dp(16), dp(6), dp(16), dp(6))
        root.addView(art, LinearLayout.LayoutParams(dp(52), dp(52)))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.addView(title)
        col.addView(sub)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(12)
        root.addView(col, lp)
    }
}

class MainActivity : Activity(), AudioPlayerManager.Listener {

    private val tabNames = arrayOf(
        "Home", "Songs", "Artists", "Albums", "Folders", "Playlists", "Favourites", "Recent"
    )
    private var tab = TAB_HOME
    private var query = ""
    private var drillTitle: String? = null
    private var drillSongs: List<Song>? = null
    private var drillPlaylist: String? = null
    private var scanning = false
    private var scanned = false
    private var lastScan = 0L
    private var askedPermission = false
    private var miniShownId = -1L
    private var miniWasVisible = false
    private var appliedTheme = -1
    private var searching = false
    private var flinging = false
    private var lastAnimPos = -1
    private var miniCollapsed = false
    private var miniP = 0f
    private var miniAnim: ValueAnimator? = null

    private val cardOutline = RoundOutline(0f)
    private val artOutline = RoundOutline(0f)
    private val edgeDrawable = GradientDrawable()

    private lateinit var content: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var listView: ListView
    private lateinit var rowAdapter: RowAdapter
    private lateinit var titleView: TextView
    private lateinit var backView: TextView
    private lateinit var emptyView: TextView
    private lateinit var searchBox: EditText
    private lateinit var sortText: TextView
    private lateinit var rescanText: TextView
    private lateinit var cancelText: TextView
    private lateinit var tabViews: List<TextView>
    private lateinit var mini: LinearLayout
    private lateinit var miniCard: FrameLayout
    private lateinit var miniBlur: ImageView
    private lateinit var miniArt: ImageView
    private lateinit var miniCol: LinearLayout
    private lateinit var miniTitle: TextView
    private lateinit var miniSub: TextView
    private lateinit var miniPlay: ImageButton
    private lateinit var miniPrev: ImageButton
    private lateinit var miniNext: ImageButton
    private lateinit var nowPlaying: NowPlayingView
    private lateinit var homeScroll: ScrollView
    private lateinit var homeBox: LinearLayout
    private lateinit var tabsScroll: HorizontalScrollView
    private lateinit var searchRowView: LinearLayout
    private var homeHero: View? = null
    private var homeKey = ""
    private var scanCount = 0
    private var appliedGlass = false
    private lateinit var photoFrame: PhotoFrame
    private lateinit var backdropView: View
    private val navItems = ArrayList<LinearLayout>()
    private val navIcons = ArrayList<ImageView>()
    private val navLabels = ArrayList<TextView>()

    private fun dp(v: Int) = Ui.dp(this, v)

    // ---------------- lifecycle ----------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioPlayerManager.init(this)
        appliedTheme = SettingsManager.theme
        appliedGlass = SettingsManager.liquidGlass
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        buildUi()
        Theme.applyBars(window)
        if (hasAudio()) {
            scan()
        } else {
            askedPermission = true
            requestPermissions(neededPermissions(), 1)
        }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        AudioPlayerManager.activityStarted()
        if (SettingsManager.theme != appliedTheme || SettingsManager.liquidGlass != appliedGlass) {
            recreate()
            return
        }
        photoFrame.refresh()
        stopService(Intent(this, FloatingPlayerService::class.java))
        AudioPlayerManager.addListener(this)
        onChanged()
        nowPlaying.resumeTicker()
        if (hasAudio() && (!scanned || System.currentTimeMillis() - lastScan > 120_000L)) scan()
    }

    override fun onStop() {
        AudioPlayerManager.removeListener(this)
        if (::nowPlaying.isInitialized) nowPlaying.pauseTicker()
        AudioPlayerManager.savePosition()
        if (SettingsManager.floatingPlayer && AudioPlayerManager.current != null && android.os.Build.VERSION.SDK_INT >= 23 && android.provider.Settings.canDrawOverlays(this)) {
            if (::miniCard.isInitialized && miniCard.visibility == View.VISIBLE && miniCard.height > 0) {
                val loc = IntArray(2)
                miniCard.getLocationOnScreen(loc)
                FloatingPlayerService.startX = (resources.displayMetrics.widthPixels - dp(64)) / 2
                FloatingPlayerService.startY = loc[1]
            } else {
                FloatingPlayerService.startX = -1
                FloatingPlayerService.startY = -1
            }
            try { startService(Intent(this, FloatingPlayerService::class.java)) } catch (_: Exception) { }
        }
        AudioPlayerManager.activityStopped()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (nowPlaying.isOpen) {
            if (!nowPlaying.closeQueue()) nowPlaying.hide()
        } else if (searching) {
            exitSearch()
        } else if (drillSongs != null || drillPlaylist != null) {
            goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasAudio()) scan() else refresh()
    }

    // ---------------- permissions ----------------
    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasAudio(): Boolean =
        checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun onEmptyClick() {
        if (!hasAudio()) {
            if (askedPermission && !shouldShowRequestPermissionRationale(audioPermission())) {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                startActivity(i)
            } else {
                askedPermission = true
                requestPermissions(neededPermissions(), 1)
            }
        } else {
            scan()
        }
    }

    // ---------------- scan / restore ----------------
    private fun scan() {
        if (scanning) return
        scanning = true
        val app = applicationContext
        Thread {
            val list = MetadataManager.apply(MusicScanner.scan(app))
            runOnUiThread {
                scanning = false
                scanned = true
                lastScan = System.currentTimeMillis()
                scanCount++
                MusicRepository.songs = list
                if (AudioPlayerManager.current == null) restoreLast(list)
                else AudioPlayerManager.updateSongs(list.associateBy { it.id })
                refresh()
                onChanged()
            }
        }.start()
    }

    private fun restoreLast(list: List<Song>) {
        val id = SettingsManager.lastId
        if (id == 0L) return
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) AudioPlayerManager.restore(list, i, SettingsManager.lastPos)
    }

    // ---------------- listener ----------------
    override fun onChanged() {
        updateMini()
        nowPlaying.refresh()
        val noDrill = drillSongs == null && drillPlaylist == null
        if ((tab == TAB_FAV || tab == TAB_RECENT || tab == TAB_HOME) && noDrill) refresh()
        else rowAdapter.notifyDataSetChanged()
    }

    private fun updateMini() {
        val s = AudioPlayerManager.current
        if (s == null) {
            miniCard.visibility = View.GONE
            miniWasVisible = false
            return
        }
        if (!miniWasVisible) {
            miniWasVisible = true
            miniCard.visibility = View.VISIBLE
            miniCard.translationY = dp(90).toFloat()
            miniCard.alpha = 0f
            miniCard.animate().translationY(0f).alpha(1f).setDuration(380)
                .setInterpolator(DecelerateInterpolator(1.8f)).start()
        }
        miniTitle.text = s.title
        miniSub.text = s.artist
        if (s.id != miniShownId) {
            miniShownId = s.id
            Ui.bindArt(miniArt, s, 160)
            Ui.bindBlur(miniBlur, s)
        }
        miniPlay.setImageResource(if (AudioPlayerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    // ---------------- mini player: bubble <-> bar ----------------
    private fun applyMiniProgress(p: Float) {
        miniP = p
        val fullW = resources.displayMetrics.widthPixels - dp(20)
        val small = dp(64)
        val lp = miniCard.layoutParams as FrameLayout.LayoutParams
        lp.width = (fullW + (small - fullW) * p).toInt()
        miniCard.layoutParams = lp
        val r = dp(18) + (dp(32) - dp(18)) * p
        cardOutline.radius = r
        miniCard.invalidateOutline()
        edgeDrawable.cornerRadius = r
        artOutline.radius = dp(8) + (dp(24) - dp(8)) * p
        miniArt.invalidateOutline()
        val a = (1f - p * 1.7f).coerceIn(0f, 1f)
        miniCol.alpha = a
        miniPlay.alpha = a
        miniNext.alpha = a
        miniPrev.alpha = a
    }

    private fun setMiniContentVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.GONE
        miniCol.visibility = vis
        miniPlay.visibility = vis
        miniNext.visibility = vis
        miniPrev.visibility = vis
    }

    private fun setMiniCollapsed(c: Boolean) {
        if (c == miniCollapsed) return
        miniCollapsed = c
        miniAnim?.cancel()
        if (!c) setMiniContentVisible(true)
        val a = ValueAnimator.ofFloat(miniP, if (c) 1f else 0f)
        a.duration = 330
        a.interpolator = if (c) DecelerateInterpolator(1.6f) else OvershootInterpolator(0.9f)
        a.addUpdateListener { applyMiniProgress(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: Animator) {
                if (miniCollapsed) setMiniContentVisible(false)
            }
        })
        miniAnim = a
        a.start()
    }

    // ---------------- search open / close ----------------
    private fun enterSearch() {
        if (searching) return
        searching = true
        header.visibility = View.GONE
        sortText.visibility = View.GONE
        rescanText.visibility = View.GONE
        cancelText.visibility = View.VISIBLE
    }

    private fun exitSearch() {
        if (!searching) return
        searching = false
        searchBox.setText("")
        searchBox.clearFocus()
        content.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        header.visibility = View.VISIBLE
        sortText.visibility = View.VISIBLE
        rescanText.visibility = View.VISIBLE
        cancelText.visibility = View.GONE
    }

    // ---------------- UI ----------------
    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val root = FrameLayout(this)
        root.setBackgroundColor(Theme.bg)

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(8), 0, 0)
        content.isFocusable = true
        content.isFocusableInTouchMode = true
        content.layoutTransition = LayoutTransition()

        // header: Kasturi Music + Settings
        header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(20), dp(8), dp(12), dp(4))
        backView = TextView(this)
        backView.text = "\u2039"
        backView.textSize = 34f
        backView.setTextColor(Theme.accent)
        backView.setPadding(0, 0, dp(10), 0)
        backView.visibility = View.GONE
        backView.setOnClickListener { goBack() }
        header.addView(backView)
        titleView = TextView(this)
        titleView.text = "Kasturi Music"
        titleView.textSize = 28f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setTextColor(Theme.text)
        titleView.isSingleLine = true
        titleView.ellipsize = TextUtils.TruncateAt.END
        header.addView(titleView, LinearLayout.LayoutParams(0, wrap, 1f))
        header.addView(actionText("Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        content.addView(header, LinearLayout.LayoutParams(match, wrap))

        // search + sort + rescan (+ cancel)
        val searchRow = LinearLayout(this)
        searchRow.orientation = LinearLayout.HORIZONTAL
        searchRow.gravity = Gravity.CENTER_VERTICAL
        searchRow.setPadding(dp(16), dp(4), dp(8), dp(8))
        searchRow.layoutTransition = LayoutTransition()
        searchRowView = searchRow
        searchBox = EditText(this)
        searchBox.hint = "Search songs, artists, albums"
        searchBox.setHintTextColor(Theme.sub)
        searchBox.setTextColor(Theme.text)
        searchBox.textSize = 16f
        searchBox.isSingleLine = true
        searchBox.imeOptions = EditorInfo.IME_ACTION_SEARCH
        searchBox.background = Ui.roundRect(Theme.card, dp(12).toFloat())
        searchBox.setPadding(dp(14), dp(10), dp(14), dp(10))
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                refresh()
            }
        })
        searchBox.setOnFocusChangeListener { _, has -> if (has) enterSearch() }
        searchBox.setOnEditorActionListener { v, _, _ ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
            true
        }
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, wrap, 1f))
        sortText = actionText("Sort") { showSortDialog() }
        rescanText = actionText("Rescan") { if (hasAudio()) scan() else onEmptyClick() }
        cancelText = actionText("Cancel") { exitSearch() }
        cancelText.visibility = View.GONE
        searchRow.addView(sortText)
        searchRow.addView(rescanText)
        searchRow.addView(cancelText)
        content.addView(searchRow, LinearLayout.LayoutParams(match, wrap))

        // tabs
        val strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        strip.setPadding(dp(16), 0, dp(8), dp(8))
        val views = ArrayList<TextView>()
        for (i in tabNames.indices) {
            val tv = TextView(this)
            tv.text = tabNames[i]
            tv.textSize = 14f
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(16), dp(8), dp(16), dp(8))
            tv.setOnClickListener { selectTab(i) }
            if (i == TAB_HOME) tv.visibility = View.GONE
            Ui.pressable(tv)
            val lp = LinearLayout.LayoutParams(wrap, wrap)
            lp.rightMargin = dp(8)
            strip.addView(tv, lp)
            views.add(tv)
        }
        tabViews = views
        val hs = HorizontalScrollView(this)
        tabsScroll = hs
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(strip)
        content.addView(hs, LinearLayout.LayoutParams(match, wrap))

        // list
        rowAdapter = RowAdapter()
        listView = ListView(this)
        listView.divider = null
        listView.setSelector(android.R.color.transparent)
        listView.isVerticalScrollBarEnabled = false
        listView.overScrollMode = View.OVER_SCROLL_NEVER
        listView.clipToPadding = false
        listView.isScrollingCacheEnabled = false
        listView.setPadding(0, 0, 0, dp(170))
        val footer = TextView(this)
        footer.text = "Made with \u2665 by Ashmit Singh"
        footer.textSize = 12f
        footer.setTextColor(Theme.sub)
        footer.gravity = Gravity.CENTER
        footer.setPadding(0, dp(24), 0, dp(24))
        listView.addFooterView(footer, null, false)
        listView.adapter = rowAdapter
        listView.setOnItemClickListener { _, _, pos, _ -> onRowClick(rowAdapter.rows.getOrNull(pos)) }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val r = rowAdapter.rows.getOrNull(pos)
            if (r == null) {
                false
            } else if (r.song != null) {
                showSongMenu(r.song)
                true
            } else if (r.playlist != null) {
                showPlaylistMenu(r.playlist)
                true
            } else {
                false
            }
        }
        listView.setOnScrollListener(object : AbsListView.OnScrollListener {
            private var lastKey = 0
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                val fl = scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                if (fl) {
                    flinging = true
                } else if (flinging) {
                    flinging = false
                    rowAdapter.notifyDataSetChanged()
                }
            }

            override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) {
                val c = view.getChildAt(0) ?: return
                val key = first * 10000 - c.top
                val dy = key - lastKey
                lastKey = key
                if (miniCard.visibility != View.VISIBLE) return
                if (first == 0 && c.top >= 0) {
                    setMiniCollapsed(false)
                } else if (dy > 6) {
                    setMiniCollapsed(true)
                } else if (dy < -6) {
                    setMiniCollapsed(false)
                }
            }
        })
        content.addView(listView, LinearLayout.LayoutParams(match, 0, 1f))

        emptyView = TextView(this)
        emptyView.textSize = 15f
        emptyView.setTextColor(Theme.accent)
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32))
        emptyView.visibility = View.GONE
        emptyView.setOnClickListener { onEmptyClick() }
        content.addView(emptyView, LinearLayout.LayoutParams(match, 0, 1f))

        homeBox = LinearLayout(this)
        homeBox.orientation = LinearLayout.VERTICAL
        homeScroll = ScrollView(this)
        homeScroll.isVerticalScrollBarEnabled = false
        homeScroll.addView(homeBox, ViewGroup.LayoutParams(match, wrap))
        homeScroll.visibility = View.GONE
        homeScroll.setOnScrollChangeListener { _, _, y, _, oldY ->
            val h = homeHero
            if (h != null) {
                h.translationY = y * 0.3f
                h.alpha = (1f - y / dp(360).toFloat()).coerceIn(0.35f, 1f)
            }
            if (miniCard.visibility == View.VISIBLE) {
                if (y <= 0) setMiniCollapsed(false)
                else if (y - oldY > 6) setMiniCollapsed(true)
                else if (y - oldY < -6) setMiniCollapsed(false)
            }
        }
        content.addView(homeScroll, LinearLayout.LayoutParams(match, 0, 1f))

        val backdrop = PhotoFrame(this, content)
        photoFrame = backdrop
        backdropView = backdrop
        root.addView(backdrop, FrameLayout.LayoutParams(match, match))

        // mini player (glass, hamesha dark)
        mini = LinearLayout(this)
        mini.orientation = LinearLayout.HORIZONTAL
        mini.gravity = Gravity.CENTER_VERTICAL
        mini.setPadding(dp(8), dp(8), dp(8), dp(8))
        miniArt = ImageView(this)
        miniArt.setBackgroundColor(Ui.GRAPHITE2)
        artOutline.radius = dp(8).toFloat()
        miniArt.outlineProvider = artOutline
        miniArt.clipToOutline = true
        mini.addView(miniArt, LinearLayout.LayoutParams(dp(48), dp(48)))
        miniCol = LinearLayout(this)
        miniCol.orientation = LinearLayout.VERTICAL
        miniTitle = TextView(this)
        miniTitle.textSize = 15f
        miniTitle.setTextColor(Color.WHITE)
        miniTitle.isSingleLine = true
        miniTitle.ellipsize = TextUtils.TruncateAt.END
        miniSub = TextView(this)
        miniSub.textSize = 12f
        miniSub.setTextColor(Ui.DIM)
        miniSub.isSingleLine = true
        miniSub.ellipsize = TextUtils.TruncateAt.END
        miniCol.addView(miniTitle)
        miniCol.addView(miniSub)
        val mlp = LinearLayout.LayoutParams(0, wrap, 1f)
        mlp.leftMargin = dp(10)
        mini.addView(miniCol, mlp)
        miniPrev = Ui.iconButton(this, R.drawable.ic_prev, Color.WHITE, 10)
        miniPrev.setOnClickListener { AudioPlayerManager.prev() }
        Ui.pressable(miniPrev)
        mini.addView(miniPrev, LinearLayout.LayoutParams(dp(40), dp(44)))
        miniPlay = Ui.iconButton(this, R.drawable.ic_play, Color.WHITE, 10)
        miniPlay.setOnClickListener { AudioPlayerManager.toggle() }
        Ui.pressable(miniPlay)
        mini.addView(miniPlay, LinearLayout.LayoutParams(dp(40), dp(44)))
        miniNext = Ui.iconButton(this, R.drawable.ic_next, Color.WHITE, 10)
        miniNext.setOnClickListener { AudioPlayerManager.next() }
        Ui.pressable(miniNext)
        mini.addView(miniNext, LinearLayout.LayoutParams(dp(44), dp(44)))

        miniCard = FrameLayout(this)
        miniCard.setBackgroundColor(Ui.GRAPHITE)
        cardOutline.radius = dp(18).toFloat()
        miniCard.outlineProvider = cardOutline
        miniCard.clipToOutline = true
        miniCard.visibility = View.GONE
        miniBlur = ImageView(this)
        miniBlur.scaleType = ImageView.ScaleType.CENTER_CROP
        miniCard.addView(miniBlur, FrameLayout.LayoutParams(match, match))
        val scrim = View(this)
        scrim.setBackgroundColor(Color.argb(110, 0, 0, 0))
        miniCard.addView(scrim, FrameLayout.LayoutParams(match, match))
        miniCard.addView(mini, FrameLayout.LayoutParams(match, match))
        if (SettingsManager.liquidGlass) {
            miniBlur.visibility = View.GONE
            scrim.visibility = View.GONE
            miniCard.addView(
                GlassBlurView(this, backdrop, Color.argb(95, 18, 18, 22)), 0,
                FrameLayout.LayoutParams(match, match)
            )
        }
        val edge = View(this)
        edgeDrawable.setColor(Color.TRANSPARENT)
        edgeDrawable.cornerRadius = dp(18).toFloat()
        edgeDrawable.setStroke(dp(1), Color.argb(60, 255, 255, 255))
        edge.background = edgeDrawable
        miniCard.addView(edge, FrameLayout.LayoutParams(match, match))

        // gestures: tap = open, swipe up = open, swipe left/right = next/previous
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (miniCollapsed) setMiniCollapsed(false) else nowPlaying.show()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (miniCollapsed || e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dy) > Math.abs(dx) && dy < -dp(30)) {
                    nowPlaying.show()
                    return true
                }
                if (Math.abs(dx) > dp(60) && Math.abs(dx) > Math.abs(dy)) {
                    if (dx < 0) AudioPlayerManager.next() else AudioPlayerManager.prev()
                    return true
                }
                return false
            }
        })
        miniCard.setOnTouchListener { v, e ->
            gd.onTouchEvent(e)
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(OvershootInterpolator(2f)).start()
            }
            true
        }
        val fullW = resources.displayMetrics.widthPixels - dp(20)
        val mplp = FrameLayout.LayoutParams(fullW, dp(64))
        mplp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        mplp.bottomMargin = dp(84)
        root.addView(miniCard, mplp)

        buildNav(root)

        nowPlaying = NowPlayingView(this) { nowPlaying.hide() }
        nowPlaying.onProgress = { p ->
            content.pivotX = content.width / 2f
            content.pivotY = content.height / 2f
            content.scaleX = 1f - 0.05f * p
            content.scaleY = 1f - 0.05f * p
            content.alpha = 1f - 0.5f * p
        }
        root.addView(nowPlaying, FrameLayout.LayoutParams(match, match))

        setContentView(root)
        styleTabs()
    }

    private fun actionText(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 13f
        t.setTextColor(Theme.accent)
        t.setPadding(dp(8), dp(8), dp(8), dp(8))
        t.setOnClickListener { onClick() }
        Ui.pressable(t)
        return t
    }

    private fun styleTabs() {
        for (i in tabViews.indices) {
            val tv = tabViews[i]
            if (i == tab) {
                tv.background = Ui.roundRect(Theme.accent, dp(18).toFloat())
                tv.setTextColor(Theme.onAccent)
                tv.setTypeface(null, Typeface.BOLD)
            } else {
                tv.background = Ui.roundRect(Theme.card, dp(18).toFloat())
                tv.setTextColor(Theme.accent)
                tv.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun swapAnim(fromRight: Boolean) {
        listView.animate().cancel()
        listView.alpha = 0f
        listView.translationX = if (fromRight) dp(28).toFloat() else 0f
        listView.translationY = if (fromRight) 0f else dp(10).toFloat()
        listView.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(240)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    private fun selectTab(i: Int) {
        tab = i
        clearDrill()
        lastAnimPos = -1
        refresh()
        styleTabs()
        listView.setSelection(0)
        swapAnim(false)
    }

    private fun clearDrill() {
        drillSongs = null
        drillPlaylist = null
        drillTitle = null
    }

    private fun goBack() {
        clearDrill()
        lastAnimPos = -1
        refresh()
        swapAnim(false)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------- row clicks ----------------
    private fun onRowClick(r: Row?) {
        if (r == null || r.header) return
        if (r.isNew) {
            createPlaylist(null)
        } else if (r.song != null) {
            val songs = r.section ?: rowAdapter.rows.mapNotNull { it.song }
            AudioPlayerManager.playQueue(songs, songs.indexOf(r.song))
        } else if (r.playlist != null) {
            exitSearch()
            drillPlaylist = r.playlist
            drillTitle = r.title
            query = ""
            lastAnimPos = -1
            refresh()
            listView.setSelection(0)
            swapAnim(true)
        } else if (r.group != null) {
            exitSearch()
            drillTitle = r.title
            drillSongs = r.group
            query = ""
            lastAnimPos = -1
            refresh()
            listView.setSelection(0)
            swapAnim(true)
        }
    }

    private fun autoFix(s: Song) {
        toast("Sahi info dhoondh raha hoon...")
        AutoFix.suggest(s) { list ->
            if (list == null) {
                toast("Internet check karo, abhi suggestion nahi mila")
            } else if (list.isEmpty()) {
                toast("Koi suggestion nahi mila")
            } else {
                val labels = Array(list.size) { i ->
                    val x = list[i]
                    x.title + " \u2014 " + x.artist + (if (x.album.isNotBlank()) " (" + x.album + ")" else "")
                }
                dialog().setTitle("Sahi info chuno (MusicBrainz)").setItems(labels) { _, which ->
                    val x = list[which]
                    MetadataManager.set(s.id, x.title, x.artist, if (x.album.isBlank()) s.album else x.album)
                    scan()
                    toast("Info update ho gayi")
                }.setNegativeButton("Cancel", null).show()
            }
        }
    }

    // ---------------- dialogs ----------------
    private fun dialog() = AlertDialog.Builder(this, Theme.dialogStyle)

    private fun askText(title: String, initial: String, ok: String, onOk: (String) -> Unit) {
        val et = EditText(this)
        et.setText(initial)
        et.isSingleLine = true
        et.setSelection(et.text.length)
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(et, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog().setTitle(title).setView(box)
            .setPositiveButton(ok) { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSortDialog() {
        val items = arrayOf("Title", "Artist", "Album", "Duration")
        dialog().setTitle("Sort by")
            .setSingleChoiceItems(items, SettingsManager.sort) { d, which ->
                SettingsManager.sort = which
                refresh()
                d.dismiss()
            }.show()
    }

    private fun createPlaylist(afterCreate: ((String) -> Unit)?) {
        askText("New playlist", "", "Create") { n ->
            if (n.isNotEmpty()) {
                if (PlaylistManager.create(n)) {
                    if (afterCreate != null) afterCreate(n)
                    refresh()
                } else {
                    toast("This name already exists")
                }
            }
        }
    }

    private fun showAddToPlaylist(s: Song) {
        val names = PlaylistManager.names()
        val items = (names + "New playlist...").toTypedArray()
        dialog().setTitle("Add to playlist").setItems(items) { _, which ->
            if (which < names.size) {
                val ok = PlaylistManager.add(names[which], s.id)
                toast(if (ok) "Added to " + names[which] else "Already in " + names[which])
                refresh()
            } else {
                createPlaylist { n ->
                    PlaylistManager.add(n, s.id)
                    toast("Added to $n")
                }
            }
        }.show()
    }

    private fun showPlaylistMenu(name: String) {
        val items = arrayOf("Rename", "Delete")
        dialog().setTitle(name).setItems(items) { _, which ->
            if (which == 0) {
                askText("Rename playlist", name, "Save") { n ->
                    if (n.isNotEmpty()) {
                        if (PlaylistManager.rename(name, n)) refresh() else toast("This name already exists")
                    }
                }
            } else {
                dialog().setTitle("Delete \"$name\"?")
                    .setMessage("Gaane phone se delete nahi honge, sirf playlist hategi.")
                    .setPositiveButton("Delete") { _, _ ->
                        PlaylistManager.delete(name)
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.show()
    }

    private fun showEditInfo(s: Song) {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(8), dp(20), 0)
        val note = TextView(this)
        note.text = "Ye badlav sirf is app mein dikhega. Original gaane ki file nahi badlegi."
        note.textSize = 12f
        note.setTextColor(Theme.sub)
        col.addView(note)
        fun field(hint: String, value: String): EditText {
            val e = EditText(this)
            e.hint = hint
            e.setText(value)
            e.isSingleLine = true
            col.addView(e)
            return e
        }
        val t = field("Title", s.title)
        val a = field("Artist", s.artist)
        val al = field("Album", s.album)
        dialog().setTitle("Edit info").setView(col)
            .setPositiveButton("Save") { _, _ ->
                MetadataManager.set(
                    s.id,
                    t.text.toString().trim().ifEmpty { s.title },
                    a.text.toString().trim().ifEmpty { s.artist },
                    al.text.toString().trim().ifEmpty { s.album }
                )
                scan()
            }
            .setNeutralButton("Reset") { _, _ ->
                MetadataManager.reset(s.id)
                scan()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSongMenu(s: Song) {
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        fun item(l: String, a: () -> Unit) {
            labels.add(l)
            acts.add(a)
        }
        item("Play next") { AudioPlayerManager.playNext(s) }
        item("Add to queue") { AudioPlayerManager.enqueue(s) }
        item(if (MusicRepository.isFav(s.id)) "Remove from favourites" else "Add to favourites") {
            MusicRepository.toggleFav(s.id)
            refresh()
        }
        item("Add to playlist") { showAddToPlaylist(s) }
        val pl = drillPlaylist
        if (pl != null) {
            item("Remove from playlist") {
                PlaylistManager.remove(pl, s.id)
                refresh()
            }
        }
        item("Edit info") { showEditInfo(s) }
        item("Auto-fix info (online)") { autoFix(s) }
        dialog().setTitle(s.title).setItems(labels.toTypedArray()) { _, which -> acts[which]() }.show()
    }

    // ---------------- data -> rows ----------------
    private fun sortSongs(list: List<Song>): List<Song> = when (SettingsManager.sort) {
        1 -> list.sortedWith(compareBy<Song>({ it.artist.lowercase() }, { it.title.lowercase() }))
        2 -> list.sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.title.lowercase() }))
        3 -> list.sortedBy { it.durationMs }
        else -> list.sortedBy { it.title.lowercase() }
    }

    private fun songRow(s: Song, section: List<Song>?): Row =
        Row(s.title, s.artist + " \u2022 " + Ui.fmt(s.durationMs), s, null, s, null, false, false, section)

    private fun buildRows(): List<Row> {
        val pl = drillPlaylist
        val q = query.trim().lowercase()
        val noDrill = pl == null && drillSongs == null

        if (tab == TAB_HOME && noDrill && q.isEmpty()) {
            val rows = ArrayList<Row>()
            val recents = MusicRepository.recentSongs().take(8)
            val most = MusicRepository.mostPlayed().take(8)
            if (recents.isEmpty() && most.isEmpty()) {
                rows.add(Row("Koi gaana bajao, yahan recently played aur most played dikhenge.", "hint", null, null, null, null, false, true))
            } else {
                if (recents.isNotEmpty()) {
                    rows.add(Row("Recently played", "", null, null, null, null, false, true))
                    for (s in recents) rows.add(songRow(s, recents))
                }
                if (most.isNotEmpty()) {
                    rows.add(Row("Most played", "", null, null, null, null, false, true))
                    for (s in most) rows.add(songRow(s, most))
                }
            }
            return rows
        }

        if (tab == TAB_PLAYLISTS && noDrill) {
            val rows = ArrayList<Row>()
            rows.add(Row("New playlist", "Tap to create one", null, null, null, null, true))
            for (n in PlaylistManager.names()) {
                if (q.isNotEmpty() && !n.lowercase().contains(q)) continue
                val songs = PlaylistManager.songs(n)
                rows.add(Row(n, songs.size.toString() + " songs", null, null, songs.firstOrNull(), n))
            }
            return rows
        }

        val base: List<Song> = if (pl != null) PlaylistManager.songs(pl) else drillSongs ?: when (tab) {
            TAB_FAV -> MusicRepository.favSongs()
            TAB_RECENT -> MusicRepository.recentSongs()
            else -> MusicRepository.songs
        }
        val filtered = if (q.isEmpty()) base else base.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
        val songList = pl != null || drillSongs != null ||
            tab == TAB_HOME || tab == TAB_SONGS || tab == TAB_FAV || tab == TAB_RECENT
        if (songList) {
            val keepOrder = pl != null || (tab == TAB_RECENT && drillSongs == null)
            val sorted = if (keepOrder) filtered else sortSongs(filtered)
            return sorted.map { songRow(it, null) }
        }
        val rows = ArrayList<Row>()
        when (tab) {
            2 -> for ((k, v) in filtered.groupBy { it.artist }) rows.add(Row(k, v.size.toString() + " songs", null, v, v[0]))
            3 -> for (v in filtered.groupBy { it.albumId }.values) rows.add(Row(v[0].album, v[0].artist + " \u2022 " + v.size + " songs", null, v, v[0]))
            4 -> for ((k, v) in filtered.groupBy { it.folder }) rows.add(Row(k.substringAfterLast('/').ifEmpty { "Root" }, v.size.toString() + " songs", null, v, v[0]))
        }
        rows.sortBy { it.title.lowercase() }
        return rows
    }

    private fun refresh() {
        val rows = buildRows()
        rowAdapter.rows = rows
        rowAdapter.notifyDataSetChanged()
        titleView.text = drillTitle ?: "Kasturi Music"
        backView.visibility = if (drillSongs != null || drillPlaylist != null) View.VISIBLE else View.GONE
        val homeMode = tab == TAB_HOME && drillSongs == null && drillPlaylist == null && query.trim().isEmpty()
        val msg: String? = when {
            !hasAudio() -> "Allow music access to see your songs.\n\nTap here"
            scanned && MusicRepository.songs.isEmpty() -> "No songs found on this phone.\n\nTap here to scan again"
            !homeMode && rows.isEmpty() -> "Nothing here yet"
            else -> null
        }
        val showHome = homeMode && msg == null
        homeScroll.visibility = if (showHome) View.VISIBLE else View.GONE
        tabsScroll.visibility = if (homeMode) View.GONE else View.VISIBLE
        searchRowView.visibility = if (homeMode) View.GONE else View.VISIBLE
        if (msg == null) {
            listView.visibility = if (showHome) View.GONE else View.VISIBLE
            emptyView.visibility = View.GONE
            if (showHome) refreshHome()
        } else {
            emptyView.text = msg + "\n\n\nMade with \u2665 by Ashmit Singh"
            listView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        }
        updateNav()
    }

    // ---------------- bottom navigation ----------------
    private fun buildNav(root: FrameLayout) {
        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.setColor(if (SettingsManager.liquidGlass) Color.TRANSPARENT else Color.argb(245, 26, 26, 28))
        g.cornerRadius = dp(30).toFloat()
        g.setStroke(dp(1), Color.argb(50, 255, 255, 255))
        pill.background = g
        pill.setPadding(dp(8), dp(6), dp(8), dp(6))
        val icons = intArrayOf(R.drawable.ic_home, R.drawable.ic_search, R.drawable.ic_library)
        val names = arrayOf("Home", "Search", "Library")
        for (i in 0 until 3) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            val ic = ImageView(this)
            ic.setImageResource(icons[i])
            ic.setColorFilter(Ui.DIM)
            item.addView(ic, LinearLayout.LayoutParams(dp(22), dp(22)))
            val lb = TextView(this)
            lb.text = names[i]
            lb.textSize = 10f
            lb.setTextColor(Ui.DIM)
            lb.gravity = Gravity.CENTER
            item.addView(lb)
            item.setOnClickListener { onNavClick(i) }
            Ui.pressable(item)
            pill.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            navItems.add(item)
            navIcons.add(ic)
            navLabels.add(lb)
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
        lp.gravity = Gravity.BOTTOM
        lp.setMargins(dp(24), 0, dp(24), dp(14))
        val card = FrameLayout(this)
        card.outlineProvider = RoundOutline(dp(30).toFloat())
        card.clipToOutline = true
        if (SettingsManager.liquidGlass) {
            card.addView(
                GlassBlurView(this, backdropView, Color.argb(120, 18, 18, 22)),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        card.addView(pill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(card, lp)
    }

    private fun onNavClick(i: Int) {
        when (i) {
            0 -> {
                exitSearch()
                if (tab != TAB_HOME || drillSongs != null || drillPlaylist != null) selectTab(TAB_HOME)
            }
            1 -> {
                if (tab == TAB_HOME) selectTab(TAB_SONGS)
                searchBox.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchBox, 0)
            }
            else -> {
                exitSearch()
                if (tab == TAB_HOME) selectTab(TAB_SONGS)
            }
        }
    }

    private fun updateNav() {
        if (navItems.size < 3) return
        val sel = if (searching) 1 else if (tab == TAB_HOME) 0 else 2
        for (i in 0 until 3) {
            val on = i == sel
            navItems[i].background = if (on) Ui.roundRect(Color.argb(60, 255, 255, 255), dp(22).toFloat()) else null
            navIcons[i].setColorFilter(if (on) Color.WHITE else Ui.DIM)
            navLabels[i].setTextColor(if (on) Color.WHITE else Ui.DIM)
            navLabels[i].setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    // ---------------- Home (Your Mix) ----------------
    private fun refreshHome() {
        val recents = MusicRepository.recentSongs().take(12)
        val most = MusicRepository.mostPlayed().take(12)
        val favs = MusicRepository.favSongs().take(12)
        val all = MusicRepository.songs
        val key = MusicRepository.totalPlays().toString() + "|" + recents.joinToString(",") { it.id.toString() } + "|" +
            most.joinToString(",") { it.id.toString() } + "|" +
            favs.joinToString(",") { it.id.toString() } + "|" + all.size + "|" + scanCount + "|" + appliedTheme
        if (key == homeKey) return
        homeKey = key
        buildHome(recents, most, favs, all)
    }

    private fun listenText(ms: Long): String {
        val min = ms / 60000L
        return if (min >= 60) (min / 60).toString() + "h " + (min % 60).toString() + "m" else min.toString() + "m"
    }

    /** Home par: kitne gaane bajaye, kitne alag gaane, kitna suna. */
    private fun addStatsTiles() {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(16), dp(16), dp(16), 0)
        fun tile(value: String, label: String, last: Boolean) {
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            t.gravity = Gravity.CENTER
            t.background = Ui.roundRect(Theme.card, dp(18).toFloat())
            t.setPadding(dp(8), dp(14), dp(8), dp(14))
            val v = TextView(this)
            v.text = value
            v.textSize = 22f
            v.setTypeface(null, Typeface.BOLD)
            v.setTextColor(Theme.text)
            v.gravity = Gravity.CENTER
            val l = TextView(this)
            l.text = label
            l.textSize = 11f
            l.setTextColor(Theme.sub)
            l.gravity = Gravity.CENTER
            t.addView(v)
            t.addView(l)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (!last) lp.rightMargin = dp(10)
            row.addView(t, lp)
        }
        tile(MusicRepository.totalPlays().toString(), "plays", false)
        tile(MusicRepository.uniquePlayed().toString(), "songs played", false)
        tile(listenText(MusicRepository.listenMs()), "listened (approx)", true)
        homeBox.addView(row)
    }

    /** Most played: kaun sa gaana kitni baar suna. */
    private fun addTopList() {
        val top = MusicRepository.topPlayed(8)
        if (top.isEmpty()) return
        val label = TextView(this)
        label.text = "Most played"
        label.textSize = 20f
        label.setTypeface(null, Typeface.BOLD)
        label.setTextColor(Theme.text)
        label.setPadding(dp(20), dp(24), dp(20), dp(8))
        homeBox.addView(label)
        val songs = top.map { it.first }
        for (i in top.indices) {
            val sg = top[i].first
            val n = top[i].second
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(20), dp(6), dp(20), dp(6))
            val rank = TextView(this)
            rank.text = (i + 1).toString()
            rank.textSize = 16f
            rank.setTypeface(null, Typeface.BOLD)
            rank.setTextColor(if (i < 3) Theme.accent else Theme.sub)
            rank.gravity = Gravity.CENTER
            row.addView(rank, LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT))
            val art = ImageView(this)
            art.setBackgroundColor(Theme.card)
            Ui.round(art, dp(8).toFloat())
            Ui.bindArt(art, sg, 120, 10)
            val alp = LinearLayout.LayoutParams(dp(46), dp(46))
            alp.leftMargin = dp(8)
            row.addView(art, alp)
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val tt = TextView(this)
            tt.text = sg.title
            tt.textSize = 15f
            tt.setTextColor(Theme.text)
            tt.isSingleLine = true
            tt.ellipsize = TextUtils.TruncateAt.END
            val aa = TextView(this)
            aa.text = sg.artist
            aa.textSize = 12f
            aa.setTextColor(Theme.sub)
            aa.isSingleLine = true
            aa.ellipsize = TextUtils.TruncateAt.END
            col.addView(tt)
            col.addView(aa)
            val clp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            clp.leftMargin = dp(12)
            row.addView(col, clp)
            val cnt = TextView(this)
            cnt.text = if (n == 1) "1 play" else "$n plays"
            cnt.textSize = 12f
            cnt.setTextColor(Theme.accent)
            row.addView(cnt)
            row.setOnClickListener { AudioPlayerManager.playQueue(songs, i) }
            Ui.pressable(row)
            homeBox.addView(row)
        }
    }

    private fun buildHome(recents: List<Song>, most: List<Song>, favs: List<Song>, all: List<Song>) {
        homeBox.removeAllViews()
        val dm = resources.displayMetrics
        val w = dm.widthPixels - dp(32)
        val f = w / dp(328).toFloat()
        fun s(v: Int) = (dp(v) * f).toInt()
        val seed = (recents + most + all.take(12)).distinctBy { it.id }.take(5)

        // ---- hero card ----
        val hero = FrameLayout(this)
        hero.setBackgroundColor(Ui.GRAPHITE)
        Ui.round(hero, dp(28).toFloat())
        val heroBlur = ImageView(this)
        heroBlur.scaleType = ImageView.ScaleType.CENTER_CROP
        hero.addView(heroBlur, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        Ui.bindBlur(heroBlur, seed.firstOrNull())
        val heroScrim = View(this)
        heroScrim.setBackgroundColor(Color.argb(120, 0, 0, 0))
        hero.addView(heroScrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val titleCol = LinearLayout(this)
        titleCol.orientation = LinearLayout.VERTICAL
        titleCol.setPadding(s(20), s(16), 0, 0)
        val big = TextView(this)
        big.text = "Your\nMix"
        big.textSize = 40f
        big.setTypeface(null, Typeface.BOLD)
        big.setTextColor(Color.WHITE)
        big.setLineSpacing(0f, 0.9f)
        titleCol.addView(big)
        val subT = TextView(this)
        subT.text = if (seed.isEmpty()) "Phone mein gaane daalo" else "Today's mix for you"
        subT.textSize = 13f
        subT.setTextColor(Ui.DIM)
        subT.setPadding(0, dp(6), 0, 0)
        titleCol.addView(subT)
        hero.addView(titleCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // collage: gol artworks
        val sizes = intArrayOf(130, 66, 52, 80, 62)
        val lefts = intArrayOf(110, 18, 250, 24, 236)
        val tops = intArrayOf(160, 176, 160, 236, 258)
        for (i in seed.indices) {
            val px = s(sizes[i])
            val iv = ImageView(this)
            iv.setBackgroundColor(Ui.GRAPHITE2)
            iv.outlineProvider = RoundOutline(px / 2f)
            iv.clipToOutline = true
            Ui.bindArt(iv, seed[i], 200, 14)
            val lp = FrameLayout.LayoutParams(px, px)
            lp.leftMargin = s(lefts[i])
            lp.topMargin = s(tops[i])
            iv.setOnClickListener { AudioPlayerManager.playQueue(seed, i) }
            Ui.pressable(iv)
            hero.addView(iv, lp)
            iv.scaleX = 0f
            iv.scaleY = 0f
            iv.animate().scaleX(1f).scaleY(1f).setStartDelay(140L + i * 70L).setDuration(440)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        }

        // shuffle button
        val shuffle = Ui.iconButton(this, R.drawable.ic_shuffle, Color.BLACK, 18)
        shuffle.background = Ui.roundRect(Ui.SILVER, dp(34).toFloat())
        shuffle.setOnClickListener {
            if (all.isNotEmpty()) {
                AudioPlayerManager.setShuffle(true)
                AudioPlayerManager.playQueue(all, (0 until all.size).random())
            }
        }
        Ui.pressable(shuffle)
        val slp = FrameLayout.LayoutParams(dp(68), dp(68))
        slp.gravity = Gravity.TOP or Gravity.END
        slp.setMargins(0, dp(18), dp(18), 0)
        hero.addView(shuffle, slp)

        val hlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s(330))
        hlp.setMargins(dp(16), dp(8), dp(16), 0)
        homeBox.addView(hero, hlp)
        homeHero = hero
        hero.alpha = 0f
        hero.translationY = dp(24).toFloat()
        hero.animate().alpha(1f).translationY(0f).setDuration(420)
            .setInterpolator(DecelerateInterpolator(1.8f)).start()

        addStatsTiles()

        // ---- carousels ----
        fun carousel(title: String, list: List<Song>) {
            if (list.isEmpty()) return
            val label = TextView(this)
            label.text = title
            label.textSize = 20f
            label.setTypeface(null, Typeface.BOLD)
            label.setTextColor(Theme.text)
            label.setPadding(dp(20), dp(24), dp(20), dp(10))
            homeBox.addView(label)
            val hs = HorizontalScrollView(this)
            hs.isHorizontalScrollBarEnabled = false
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(dp(16), 0, dp(8), 0)
            for (i in list.indices) {
                val sg = list[i]
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                val art = ImageView(this)
                art.setBackgroundColor(Theme.card)
                Ui.round(art, dp(16).toFloat())
                Ui.bindArt(art, sg, 280, 22)
                col.addView(art, LinearLayout.LayoutParams(dp(132), dp(132)))
                val tt = TextView(this)
                tt.text = sg.title
                tt.textSize = 14f
                tt.setTextColor(Theme.text)
                tt.isSingleLine = true
                tt.ellipsize = TextUtils.TruncateAt.END
                tt.setPadding(0, dp(8), 0, 0)
                col.addView(tt)
                val aa = TextView(this)
                aa.text = sg.artist
                aa.textSize = 12f
                aa.setTextColor(Theme.sub)
                aa.isSingleLine = true
                aa.ellipsize = TextUtils.TruncateAt.END
                col.addView(aa)
                col.setOnClickListener { AudioPlayerManager.playQueue(list, i) }
                Ui.pressable(col)
                val clp = LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT)
                clp.rightMargin = dp(12)
                row.addView(col, clp)
            }
            hs.addView(row)
            homeBox.addView(hs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        carousel("Recently played", recents)
        addTopList()
        carousel("Favourites", favs)
        if (recents.isEmpty() && most.isEmpty() && favs.isEmpty()) carousel("Songs", all.take(20))

        homeBox.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)))
    }

    // ---------------- adapter ----------------
    private inner class RowAdapter : BaseAdapter() {
        var rows: List<Row> = emptyList()

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getViewTypeCount(): Int = 2
        override fun getItemViewType(position: Int): Int = if (rows[position].header) 1 else 0
        override fun isEnabled(position: Int): Boolean = !rows[position].header

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val r = rows[position]
            if (r.header) {
                val tv = (convertView as? TextView) ?: TextView(this@MainActivity)
                val hint = r.sub == "hint"
                tv.text = r.title
                tv.textSize = if (hint) 14f else 20f
                tv.setTypeface(null, if (hint) Typeface.NORMAL else Typeface.BOLD)
                tv.setTextColor(if (hint) Theme.sub else Theme.text)
                tv.setPadding(dp(20), dp(if (hint) 24 else 20), dp(20), dp(8))
                return tv
            }
            val h: RowHolder
            val v: View
            if (convertView == null) {
                h = RowHolder(this@MainActivity)
                v = h.root
                v.tag = h
            } else {
                v = convertView
                h = convertView.tag as RowHolder
            }
            val sg = r.song
            val playing = sg != null && sg.id == AudioPlayerManager.current?.id
            h.title.text = r.title
            h.title.setTextColor(if (playing || r.isNew) Theme.accent else Theme.text)
            h.title.setTypeface(null, if (playing || r.isNew) Typeface.BOLD else Typeface.NORMAL)
            h.sub.text = r.sub
            Ui.bindArt(h.art, r.artSong, 160, 12, !flinging)
            // naye aane wale rows halke se slide + fade hote hain
            v.animate().cancel()
            if (!flinging && position > lastAnimPos) {
                lastAnimPos = position
                v.alpha = 0f
                v.translationY = dp(18).toFloat()
                v.animate().alpha(1f).translationY(0f).setDuration(260)
                    .setStartDelay(minOf(position, 8) * 18L)
                    .setInterpolator(DecelerateInterpolator(1.5f)).start()
            } else {
                v.alpha = 1f
                v.translationY = 0f
            }
            return v
        }
    }
}
