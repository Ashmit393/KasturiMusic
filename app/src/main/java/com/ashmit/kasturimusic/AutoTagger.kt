package com.ashmit.kasturimusic

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Auto-tag: gaano ke naam / artist / album / cover apne aap sahi karta hai (MusicBrainz + Cover Art Archive,
 * 1 request/second). Sirf pakka match apne aap lagta hai; baaki Review mein. Original audio files nahi badalti.
 */
object AutoTagger {
    class Review(val song: Song, val options: List<Suggestion>)

    private lateinit var p: SharedPreferences
    val review = ArrayList<Review>()
    val fixedLog = ArrayList<String>()

    @Volatile var running = false
    @Volatile var cancelFlag = false
    @Volatile var finished = false
    @Volatile var total = 0
    @Volatile var checked = 0
    @Volatile var fixed = 0
    @Volatile var covers = 0
    @Volatile var alreadyOk = 0
    @Volatile var noMatch = 0
    @Volatile var error: String? = null

    fun init(ctx: Context) {
        if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("kasturi_autotag", Context.MODE_PRIVATE)
    }

    private fun loadIds(key: String): MutableSet<Long> {
        val out = HashSet<Long>()
        val s = p.getString(key, "") ?: ""
        if (s.isNotEmpty()) for (x in s.split(",")) x.toLongOrNull()?.let { out.add(it) }
        return out
    }

    private fun saveIds(key: String, ids: Set<Long>) {
        p.edit().putString(key, ids.joinToString(",")).apply()
    }

    fun reviewCount(): Int = synchronized(review) { review.size }
    fun reviewList(): List<Review> = synchronized(review) { ArrayList(review) }
    fun fixedList(): List<String> = synchronized(fixedLog) { ArrayList(fixedLog) }

    /** Naam mein kachra ya artist/album missing. */
    fun needsFix(s: Song): Boolean {
        if (s.artist == "Unknown artist" || s.album == "Unknown album") return true
        val t = s.title
        if (t.contains("www.", true) || t.contains(".com", true) || t.contains("_")) return true
        if (t.contains("official", true) || t.contains("lyric", true) || t.contains("video", true)) return true
        if (t.contains("kbps", true) || t.contains("audio", true)) return true
        if (Regex("^\\s*\\d{1,3}\\s*[.\\-_]+").containsMatchIn(t)) return true
        return false
    }

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097f]+"), "")

    /** Auto-apply: naam aur artist dono milne chahiye (artist unknown ho to kabhi auto-apply nahi). */
    private fun matches(song: Song, sug: Suggestion): Boolean {
        val lt = norm(AutoFix.cleanTitle(song.title))
        val st = norm(sug.title)
        if (lt.isEmpty() || st.isEmpty()) return false
        val titleOk = lt == st || lt.contains(st) || st.contains(lt)
        val la = norm(AutoFix.firstArtist(song.artist))
        if (la.isEmpty()) return false
        val sa = norm(sug.artist)
        val artistOk = sa.contains(la) || la.contains(sa)
        return titleOk && artistOk
    }

    fun start(
        ctx: Context, songs: List<Song>, onlyMessy: Boolean, skipChecked: Boolean,
        fetchArt: Boolean, replaceArt: Boolean
    ) {
        if (running) return
        val app = ctx.applicationContext
        init(app)
        MetadataManager.init(app)
        val doneBefore = loadIds("done")
        val todo = songs.filter { s ->
            (!onlyMessy || needsFix(s)) && (!skipChecked || !doneBefore.contains(s.id))
        }
        running = true
        cancelFlag = false
        finished = false
        error = null
        total = todo.size
        checked = 0
        fixed = 0
        covers = 0
        alreadyOk = 0
        noMatch = 0
        synchronized(review) { review.clear() }
        synchronized(fixedLog) { fixedLog.clear() }
        Thread {
            var errors = 0
            val applied = loadIds("applied")
            val done = loadIds("done")
            for (s in todo) {
                if (cancelFlag) break
                val list = AutoFix.lookup(s)
                if (list == null) {
                    errors++
                    if (errors >= 3) {
                        error = "Internet ya MusicBrainz mein problem hai. Baad mein try karo."
                        break
                    }
                } else {
                    errors = 0
                    val best = list.firstOrNull()
                    if (best != null && best.score >= 95 && matches(s, best)) {
                        val newAlbum = if (best.album.isBlank()) s.album else best.album
                        val changed = best.title != s.title || best.artist != s.artist || newAlbum != s.album
                        if (changed) {
                            MetadataManager.set(s.id, best.title, best.artist, newAlbum)
                            applied.add(s.id)
                            fixed++
                            synchronized(fixedLog) { fixedLog.add(s.title + " \u2192 " + best.title + " \u2022 " + best.artist) }
                        } else {
                            alreadyOk++
                        }
                        if (fetchArt && best.releaseId.isNotBlank() && (replaceArt || !ArtworkLoader.hasOwnArt(app, s))) {
                            if (fetchCover(app, s.id, best.releaseId)) {
                                covers++
                                applied.add(s.id)
                            }
                        }
                    } else if (list.isNotEmpty()) {
                        synchronized(review) { review.add(Review(s, list.take(4))) }
                    } else {
                        noMatch++
                    }
                    done.add(s.id)
                }
                checked++
                try {
                    Thread.sleep(1100)
                } catch (e: InterruptedException) {
                    break
                }
            }
            saveIds("applied", applied)
            saveIds("done", done)
            MusicRepository.songs = MetadataManager.apply(MusicRepository.songs)
            MusicRepository.changed = true
            ArtworkLoader.resetCaches()
            Handler(Looper.getMainLooper()).post {
                AudioPlayerManager.updateSongs(MusicRepository.songs.associateBy { it.id })
            }
            running = false
            finished = true
        }.start()
    }

    /** Review se user ne ek suggestion chuni. */
    fun applyManual(ctx: Context, song: Song, sug: Suggestion) {
        init(ctx)
        val newAlbum = if (sug.album.isBlank()) song.album else sug.album
        MetadataManager.set(song.id, sug.title, sug.artist, newAlbum)
        val applied = loadIds("applied")
        applied.add(song.id)
        saveIds("applied", applied)
        fixed++
        synchronized(fixedLog) { fixedLog.add(song.title + " \u2192 " + sug.title + " \u2022 " + sug.artist) }
        synchronized(review) { review.removeAll { it.song.id == song.id } }
        MusicRepository.songs = MetadataManager.apply(MusicRepository.songs)
        MusicRepository.changed = true
        AudioPlayerManager.updateSongs(MusicRepository.songs.associateBy { it.id })
    }

    /** Review ke saare gaano mein pehli (sabse achhi) suggestion laga do. Undo se wapas ho sakta hai. */
    fun acceptAllBest(ctx: Context): Int {
        init(ctx)
        val items = reviewList()
        val applied = loadIds("applied")
        var n = 0
        for (r in items) {
            val x = r.options.firstOrNull() ?: continue
            val newAlbum = if (x.album.isBlank()) r.song.album else x.album
            MetadataManager.set(r.song.id, x.title, x.artist, newAlbum)
            applied.add(r.song.id)
            fixed++
            n++
            synchronized(fixedLog) { fixedLog.add(r.song.title + " \u2192 " + x.title + " \u2022 " + x.artist) }
        }
        saveIds("applied", applied)
        synchronized(review) { review.clear() }
        MusicRepository.songs = MetadataManager.apply(MusicRepository.songs)
        MusicRepository.changed = true
        AudioPlayerManager.updateSongs(MusicRepository.songs.associateBy { it.id })
        return n
    }

    fun skipReview(song: Song) {
        synchronized(review) { review.removeAll { it.song.id == song.id } }
    }

    /** Auto-tag ke saare badlav wapas (original naam aur cover wapas aate hain). */
    fun undoAll(ctx: Context) {
        init(ctx)
        MetadataManager.init(ctx)
        val applied = loadIds("applied")
        for (id in applied) {
            MetadataManager.reset(id)
            try { File(File(ctx.filesDir, "covers"), "$id.jpg").delete() } catch (e: Exception) { }
        }
        p.edit().remove("applied").remove("done").apply()
        synchronized(review) { review.clear() }
        synchronized(fixedLog) { fixedLog.clear() }
        fixed = 0
        covers = 0
        alreadyOk = 0
        noMatch = 0
        checked = 0
        total = 0
        MusicRepository.rescan = true
        ArtworkLoader.resetCaches()
    }

    fun resetChecked() {
        if (::p.isInitialized) p.edit().remove("done").apply()
    }

    private fun fetchCover(ctx: Context, songId: Long, releaseId: String): Boolean {
        try {
            val dir = File(ctx.filesDir, "covers")
            dir.mkdirs()
            val c = URL("https://coverartarchive.org/release/$releaseId/front-500").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8000
                c.readTimeout = 15000
                c.setRequestProperty("User-Agent", "KasturiMusicPlayer/1.8 (https://github.com/Ashmit393/KasturiMusic)")
                if (c.responseCode != 200) return false
                val tmp = File(dir, "$songId.part")
                c.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                val f = File(dir, "$songId.jpg")
                if (f.exists()) f.delete()
                return tmp.renameTo(f)
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            return false
        }
    }
}
