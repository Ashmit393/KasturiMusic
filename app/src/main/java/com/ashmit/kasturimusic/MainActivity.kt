package com.ashmit.kasturimusic

import android.Manifest
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
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
import android.widget.Toast

private const val TAB_SONGS = 0
private const val TAB_PLAYLISTS = 4
private const val TAB_FAV = 5
private const val TAB_RECENT = 6

private class Row(
    val title: String,
    val sub: String,
    val song: Song?,
    val group: List<Song>?,
    val artSong: Song?,
    val playlist: String? = null,
    val isNew: Boolean = false
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

    private val tabNames = arrayOf("Songs", "Artists", "Albums", "Folders", "Playlists", "Favourites", "Recent")
    private var tab = TAB_SONGS
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

    private lateinit var listView: ListView
    private lateinit var rowAdapter: RowAdapter
    private lateinit var titleView: TextView
    private lateinit var backView: TextView
    private lateinit var emptyView: TextView
    private lateinit var searchBox: EditText
    private lateinit var tabViews: List<TextView>
    private lateinit var mini: LinearLayout
    private lateinit var miniCard: FrameLayout
    private lateinit var miniBlur: ImageView
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
        appliedTheme = SettingsManager.theme
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
        if (SettingsManager.theme != appliedTheme) {
            recreate()
            return
        }
        AudioPlayerManager.addListener(this)
        onChanged()
        nowPlaying.resumeTicker()
        if (hasAudio() && (!scanned || System.currentTimeMillis() - lastScan > 120_000L)) scan()
    }

    override fun onStop() {
        AudioPlayerManager.removeListener(this)
        if (::nowPlaying.isInitialized) nowPlaying.pauseTicker()
        AudioPlayerManager.savePosition()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (nowPlaying.isOpen) {
            nowPlaying.hide()
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
        if ((tab == TAB_FAV || tab == TAB_RECENT) && noDrill) refresh() else rowAdapter.notifyDataSetChanged()
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

    // ---------------- UI ----------------
    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val root = FrameLayout(this)
        root.setBackgroundColor(Theme.bg)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(8), 0, 0)

        // header: Kasturi Music + Settings
        val header = LinearLayout(this)
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

        // search + sort + rescan
        val searchRow = LinearLayout(this)
        searchRow.orientation = LinearLayout.HORIZONTAL
        searchRow.gravity = Gravity.CENTER_VERTICAL
        searchRow.setPadding(dp(16), dp(4), dp(8), dp(8))
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
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, wrap, 1f))
        searchRow.addView(actionText("Sort") { showSortDialog() })
        searchRow.addView(actionText("Rescan") { if (hasAudio()) scan() else onEmptyClick() })
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
        content.addView(listView, LinearLayout.LayoutParams(match, 0, 1f))

        emptyView = TextView(this)
        emptyView.textSize = 15f
        emptyView.setTextColor(Theme.accent)
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32))
        emptyView.visibility = View.GONE
        emptyView.setOnClickListener { onEmptyClick() }
        content.addView(emptyView, LinearLayout.LayoutParams(match, 0, 1f))

        root.addView(content, FrameLayout.LayoutParams(match, match))

        // mini player (glass, hamesha dark)
        mini = LinearLayout(this)
        mini.orientation = LinearLayout.HORIZONTAL
        mini.gravity = Gravity.CENTER_VERTICAL
        mini.setPadding(dp(8), dp(8), dp(8), dp(8))
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
        Ui.pressable(miniPlay)
        mini.addView(miniPlay, LinearLayout.LayoutParams(dp(44), dp(44)))
        val miniNext = Ui.iconButton(this, R.drawable.ic_next, Color.WHITE, 10)
        miniNext.setOnClickListener { AudioPlayerManager.next() }
        Ui.pressable(miniNext)
        mini.addView(miniNext, LinearLayout.LayoutParams(dp(44), dp(44)))

        miniCard = FrameLayout(this)
        miniCard.setBackgroundColor(Ui.GRAPHITE)
        Ui.round(miniCard, dp(18).toFloat())
        miniCard.visibility = View.GONE
        miniBlur = ImageView(this)
        miniBlur.scaleType = ImageView.ScaleType.CENTER_CROP
        miniCard.addView(miniBlur, FrameLayout.LayoutParams(match, match))
        val scrim = View(this)
        scrim.setBackgroundColor(Color.argb(110, 0, 0, 0))
        miniCard.addView(scrim, FrameLayout.LayoutParams(match, match))
        miniCard.addView(mini, FrameLayout.LayoutParams(match, match))
        val edge = View(this)
        val eg = GradientDrawable()
        eg.setColor(Color.TRANSPARENT)
        eg.cornerRadius = dp(18).toFloat()
        eg.setStroke(dp(1), Color.argb(60, 255, 255, 255))
        edge.background = eg
        miniCard.addView(edge, FrameLayout.LayoutParams(match, match))
        miniCard.setOnClickListener { nowPlaying.show() }
        Ui.pressable(miniCard)
        val mplp = FrameLayout.LayoutParams(match, dp(64))
        mplp.gravity = Gravity.BOTTOM
        mplp.setMargins(dp(10), 0, dp(10), dp(10))
        root.addView(miniCard, mplp)

        nowPlaying = NowPlayingView(this) { nowPlaying.hide() }
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

    private fun selectTab(i: Int) {
        tab = i
        clearDrill()
        refresh()
        styleTabs()
        listView.setSelection(0)
    }

    private fun clearDrill() {
        drillSongs = null
        drillPlaylist = null
        drillTitle = null
    }

    private fun goBack() {
        clearDrill()
        refresh()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------- row clicks ----------------
    private fun onRowClick(r: Row?) {
        if (r == null) return
        if (r.isNew) {
            createPlaylist(null)
        } else if (r.song != null) {
            val songs = rowAdapter.rows.mapNotNull { it.song }
            AudioPlayerManager.playQueue(songs, songs.indexOf(r.song))
        } else if (r.playlist != null) {
            drillPlaylist = r.playlist
            drillTitle = r.title
            query = ""
            searchBox.setText("")
            refresh()
            listView.setSelection(0)
        } else if (r.group != null) {
            drillTitle = r.title
            drillSongs = r.group
            query = ""
            searchBox.setText("")
            refresh()
            listView.setSelection(0)
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
        dialog().setTitle(s.title).setItems(labels.toTypedArray()) { _, which -> acts[which]() }.show()
    }

    // ---------------- data -> rows ----------------
    private fun sortSongs(list: List<Song>): List<Song> = when (SettingsManager.sort) {
        1 -> list.sortedWith(compareBy<Song>({ it.artist.lowercase() }, { it.title.lowercase() }))
        2 -> list.sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.title.lowercase() }))
        3 -> list.sortedBy { it.durationMs }
        else -> list.sortedBy { it.title.lowercase() }
    }

    private fun buildRows(): List<Row> {
        val pl = drillPlaylist
        val q = query.trim().lowercase()
        if (tab == TAB_PLAYLISTS && pl == null && drillSongs == null) {
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
        val songList = pl != null || drillSongs != null || tab == TAB_SONGS || tab == TAB_FAV || tab == TAB_RECENT
        if (songList) {
            val keepOrder = pl != null || (tab == TAB_RECENT && drillSongs == null)
            val sorted = if (keepOrder) filtered else sortSongs(filtered)
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
        titleView.text = drillTitle ?: "Kasturi Music"
        backView.visibility = if (drillSongs != null || drillPlaylist != null) View.VISIBLE else View.GONE
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
            h.title.setTextColor(if (playing || r.isNew) Theme.accent else Theme.text)
            h.title.setTypeface(null, if (playing || r.isNew) Typeface.BOLD else Typeface.NORMAL)
            h.sub.text = r.sub
            Ui.bindArt(h.art, r.artSong, 160)
            return v
        }
    }
}
