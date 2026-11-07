package com.ashmit.kasturimusic

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

private class Row(
    val title: String,
    val sub: String,
    val song: Song?,
    val group: List<Song>?,
    val artSong: Song?
)

private class RowHolder(ctx: Context) {
    val art = ImageView(ctx)
    val title = TextView(ctx)
    val sub = TextView(ctx)
    val root = LinearLayout(ctx)

    init {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d + 0.5f).toInt()
        art.setBackgroundColor(Ui.GRAPHITE)
        Ui.round(art, dp(8).toFloat())
        title.textSize = 16f
        title.isSingleLine = true
        title.ellipsize = TextUtils.TruncateAt.END
        sub.textSize = 13f
        sub.setTextColor(Ui.DIM)
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

    private val tabNames = arrayOf("Songs", "Artists", "Albums", "Folders", "Favourites", "Recent")
    private var tab = 0
    private var query = ""
    private var drillTitle: String? = null
    private var drillSongs: List<Song>? = null
    private var scanning = false
    private var scanned = false
    private var lastScan = 0L
    private var askedPermission = false
    private var miniShownId = -1L

    private lateinit var listView: ListView
    private lateinit var rowAdapter: RowAdapter
    private lateinit var titleView: TextView
    private lateinit var backView: TextView
    private lateinit var emptyView: TextView
    private lateinit var searchBox: EditText
    private lateinit var tabViews: List<TextView>
    private lateinit var mini: LinearLayout
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniSub: TextView
    private lateinit var miniPlay: ImageButton
    private lateinit var nowPlaying: NowPlayingView

    private fun dp(v: Int) = Ui.dp(this, v)

    // ---------------- lifecycle ----------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioPlayerManager.init(this)
        buildUi()
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
        AudioPlayerManager.addListener(this)
        onChanged()
        nowPlaying.resumeTicker()
        if (hasAudio() && (!scanned || System.currentTimeMillis() - lastScan > 120_000L)) scan()
    }

    override fun onStop() {
        AudioPlayerManager.removeListener(this)
        nowPlaying.pauseTicker()
        AudioPlayerManager.savePosition()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (nowPlaying.isOpen) {
            nowPlaying.hide()
        } else if (drillSongs != null) {
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
            val list = MusicScanner.scan(app)
            runOnUiThread {
                scanning = false
                scanned = true
                lastScan = System.currentTimeMillis()
                MusicRepository.songs = list
                if (AudioPlayerManager.current == null) restoreLast(list)
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
        if ((tab == 4 || tab == 5) && drillSongs == null) refresh() else rowAdapter.notifyDataSetChanged()
    }

    private fun updateMini() {
        val s = AudioPlayerManager.current
        if (s == null) {
            mini.visibility = View.GONE
            return
        }
        mini.visibility = View.VISIBLE
        miniTitle.text = s.title
        miniSub.text = s.artist
        if (s.id != miniShownId) {
            miniShownId = s.id
            Ui.bindArt(miniArt, s, 160)
        }
        miniPlay.setImageResource(if (AudioPlayerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    // ---------------- UI ----------------
    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val root = FrameLayout(this)
        root.setBackgroundColor(Ui.BLACK)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(8), 0, 0)

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(20), dp(8), dp(16), dp(4))
        backView = TextView(this)
        backView.text = "\u2039"
        backView.textSize = 34f
        backView.setTextColor(Ui.SILVER)
        backView.setPadding(0, 0, dp(10), 0)
        backView.visibility = View.GONE
        backView.setOnClickListener { goBack() }
        header.addView(backView)
        titleView = TextView(this)
        titleView.text = "Library"
        titleView.textSize = 30f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setTextColor(Color.WHITE)
        titleView.isSingleLine = true
        titleView.ellipsize = TextUtils.TruncateAt.END
        header.addView(titleView, LinearLayout.LayoutParams(0, wrap, 1f))
        header.addView(actionText("Sort") { showSortDialog() })
        header.addView(actionText("Rescan") { if (hasAudio()) scan() else onEmptyClick() })
        header.addView(actionText("About") { showAbout() })
        content.addView(header, LinearLayout.LayoutParams(match, wrap))

        // search
        searchBox = EditText(this)
        searchBox.hint = "Search songs, artists, albums"
        searchBox.setHintTextColor(Ui.DIM)
        searchBox.setTextColor(Color.WHITE)
        searchBox.textSize = 16f
        searchBox.isSingleLine = true
        searchBox.imeOptions = EditorInfo.IME_ACTION_SEARCH
        searchBox.background = Ui.roundRect(Ui.GRAPHITE, dp(12).toFloat())
        searchBox.setPadding(dp(14), dp(10), dp(14), dp(10))
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                refresh()
            }
        })
        val slp = LinearLayout.LayoutParams(match, wrap)
        slp.setMargins(dp(16), dp(4), dp(16), dp(8))
        content.addView(searchBox, slp)

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
            val lp = LinearLayout.LayoutParams(wrap, wrap)
            lp.rightMargin = dp(8)
            strip.addView(tv, lp)
            views.add(tv)
        }
        tabViews = views
        val hs = HorizontalScrollView(this)
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
        listView.setPadding(0, 0, 0, dp(88))
        val footer = TextView(this)
        footer.text = "Made with \u2665 by Ashmit Singh"
        footer.textSize = 12f
        footer.setTextColor(Ui.DIM)
        footer.gravity = Gravity.CENTER
        footer.setPadding(0, dp(24), 0, dp(24))
        listView.addFooterView(footer, null, false)
        listView.adapter = rowAdapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val r = rowAdapter.rows.getOrNull(pos)
            if (r != null) {
                if (r.song != null) {
                    val songs = rowAdapter.rows.mapNotNull { it.song }
                    AudioPlayerManager.playQueue(songs, songs.indexOf(r.song))
                } else if (r.group != null) {
                    drillTitle = r.title
                    drillSongs = r.group
                    query = ""
                    searchBox.setText("")
                    refresh()
                    listView.setSelection(0)
                }
            }
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val s = rowAdapter.rows.getOrNull(pos)?.song
            if (s == null) {
                false
            } else {
                showSongMenu(s)
                true
            }
        }
        content.addView(listView, LinearLayout.LayoutParams(match, 0, 1f))

        emptyView = TextView(this)
        emptyView.textSize = 15f
        emptyView.setTextColor(Ui.SILVER)
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32))
        emptyView.visibility = View.GONE
        emptyView.setOnClickListener { onEmptyClick() }
        content.addView(emptyView, LinearLayout.LayoutParams(match, 0, 1f))

        root.addView(content, FrameLayout.LayoutParams(match, match))

        // mini player
        mini = LinearLayout(this)
        mini.orientation = LinearLayout.HORIZONTAL
        mini.gravity = Gravity.CENTER_VERTICAL
        mini.background = Ui.roundRect(Ui.GRAPHITE, dp(16).toFloat())
        mini.setPadding(dp(8), dp(8), dp(8), dp(8))
        mini.visibility = View.GONE
        mini.setOnClickListener { nowPlaying.show() }
        miniArt = ImageView(this)
        miniArt.setBackgroundColor(Ui.GRAPHITE2)
        Ui.round(miniArt, dp(8).toFloat())
        mini.addView(miniArt, LinearLayout.LayoutParams(dp(48), dp(48)))
        val miniCol = LinearLayout(this)
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
        miniPlay = Ui.iconButton(this, R.drawable.ic_play, Color.WHITE, 10)
        miniPlay.setOnClickListener { AudioPlayerManager.toggle() }
        mini.addView(miniPlay, LinearLayout.LayoutParams(dp(44), dp(44)))
        val miniNext = Ui.iconButton(this, R.drawable.ic_next, Color.WHITE, 10)
        miniNext.setOnClickListener { AudioPlayerManager.next() }
        mini.addView(miniNext, LinearLayout.LayoutParams(dp(44), dp(44)))
        val mplp = FrameLayout.LayoutParams(match, dp(64))
        mplp.gravity = Gravity.BOTTOM
        mplp.setMargins(dp(10), 0, dp(10), dp(10))
        root.addView(mini, mplp)

        nowPlaying = NowPlayingView(this) { nowPlaying.hide() }
        root.addView(nowPlaying, FrameLayout.LayoutParams(match, match))

        setContentView(root)
        styleTabs()
    }

    private fun actionText(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 13f
        t.setTextColor(Ui.SILVER)
        t.setPadding(dp(8), dp(8), dp(8), dp(8))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun styleTabs() {
        for (i in tabViews.indices) {
            val tv = tabViews[i]
            if (i == tab) {
                tv.background = Ui.roundRect(Ui.SILVER, dp(18).toFloat())
                tv.setTextColor(Color.BLACK)
                tv.setTypeface(null, Typeface.BOLD)
            } else {
                tv.background = Ui.roundRect(Ui.GRAPHITE, dp(18).toFloat())
                tv.setTextColor(Ui.SILVER)
                tv.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun selectTab(i: Int) {
        tab = i
        drillSongs = null
        drillTitle = null
        refresh()
        styleTabs()
        listView.setSelection(0)
    }

    private fun goBack() {
        drillSongs = null
        drillTitle = null
        refresh()
    }

    // ---------------- dialogs ----------------
    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun showSortDialog() {
        val items = arrayOf("Title", "Artist", "Album", "Duration")
        dialog().setTitle("Sort by")
            .setSingleChoiceItems(items, SettingsManager.sort) { d, which ->
                SettingsManager.sort = which
                refresh()
                d.dismiss()
            }.show()
    }

    private fun showSongMenu(s: Song) {
        val fav = MusicRepository.isFav(s.id)
        val items = arrayOf("Play next", "Add to queue", if (fav) "Remove from favourites" else "Add to favourites")
        dialog().setTitle(s.title).setItems(items) { _, which ->
            when (which) {
                0 -> AudioPlayerManager.playNext(s)
                1 -> AudioPlayerManager.enqueue(s)
                2 -> { MusicRepository.toggleFav(s.id); refresh() }
            }
        }.show()
    }

    private fun showAbout() {
        var v = "1.0.0"
        try {
            v = packageManager.getPackageInfo(packageName, 0).versionName ?: v
        } catch (e: Exception) { }
        dialog().setTitle("KASTURI MUSIC PLAYER")
            .setMessage("Version $v\n\nMade with \u2665 by Ashmit Singh\n\nOffline. No ads. No tracking.")
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------------- data -> rows ----------------
    private fun sortSongs(list: List<Song>): List<Song> = when (SettingsManager.sort) {
        1 -> list.sortedWith(compareBy<Song>({ it.artist.lowercase() }, { it.title.lowercase() }))
        2 -> list.sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.title.lowercase() }))
        3 -> list.sortedBy { it.durationMs }
        else -> list.sortedBy { it.title.lowercase() }
    }

    private fun buildRows(): List<Row> {
        val base: List<Song> = drillSongs ?: when (tab) {
            4 -> MusicRepository.favSongs()
            5 -> MusicRepository.recentSongs()
            else -> MusicRepository.songs
        }
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) base else base.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
        val songList = drillSongs != null || tab == 0 || tab == 4 || tab == 5
        if (songList) {
            val sorted = if (tab == 5 && drillSongs == null) filtered else sortSongs(filtered)
            return sorted.map { Row(it.title, it.artist + " \u2022 " + Ui.fmt(it.durationMs), it, null, it) }
        }
        val rows = ArrayList<Row>()
        when (tab) {
            1 -> for ((k, v) in filtered.groupBy { it.artist }) rows.add(Row(k, v.size.toString() + " songs", null, v, v[0]))
            2 -> for (v in filtered.groupBy { it.albumId }.values) rows.add(Row(v[0].album, v[0].artist + " \u2022 " + v.size + " songs", null, v, v[0]))
            3 -> for ((k, v) in filtered.groupBy { it.folder }) rows.add(Row(k.substringAfterLast('/').ifEmpty { "Root" }, v.size.toString() + " songs", null, v, v[0]))
        }
        rows.sortBy { it.title.lowercase() }
        return rows
    }

    private fun refresh() {
        val rows = buildRows()
        rowAdapter.rows = rows
        rowAdapter.notifyDataSetChanged()
        titleView.text = drillTitle ?: "Library"
        backView.visibility = if (drillSongs != null) View.VISIBLE else View.GONE
        val msg: String? = when {
            !hasAudio() -> "Allow music access to see your songs.\n\nTap here"
            scanned && MusicRepository.songs.isEmpty() -> "No songs found on this phone.\n\nTap here to scan again"
            rows.isEmpty() -> "Nothing here yet"
            else -> null
        }
        if (msg == null) {
            listView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        } else {
            emptyView.text = msg + "\n\n\nMade with \u2665 by Ashmit Singh"
            listView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        }
    }

    // ---------------- adapter ----------------
    private inner class RowAdapter : BaseAdapter() {
        var rows: List<Row> = emptyList()

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
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
            val r = rows[position]
            val sg = r.song
            val playing = sg != null && sg.id == AudioPlayerManager.current?.id
            h.title.text = r.title
            h.title.setTextColor(if (playing) Ui.SILVER else Color.WHITE)
            h.title.setTypeface(null, if (playing) Typeface.BOLD else Typeface.NORMAL)
            h.sub.text = r.sub
            Ui.bindArt(h.art, r.artSong, 160)
            return v
        }
    }
}
