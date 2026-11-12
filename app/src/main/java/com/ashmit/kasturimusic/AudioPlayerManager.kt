package com.ashmit.kasturimusic

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager

object AudioPlayerManager {
    interface Listener {
        fun onChanged()
    }

    private val listeners = ArrayList<Listener>()
    private lateinit var app: Context
    private var player: MediaPlayer? = null
    private var prepared = false
    private var pendingStart = false
    private var pendingSeek = 0
    private var errorCount = 0
    private var originalQueue: List<Song> = emptyList()
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocus = false

    var queue: List<Song> = emptyList()
        private set
    var index: Int = -1
        private set
    var shuffle: Boolean = false
        private set
    var repeat: Int = 0 // 0 = off, 1 = all, 2 = one
        private set
    var isPlaying: Boolean = false
        private set
    var serviceActive: Boolean = false

    // ---------- app screen par hai ya nahi (floating player ke liye) ----------
    private var visibleCount = 0
    val appVisible: Boolean
        get() = visibleCount > 0

    fun activityStarted() {
        visibleCount++
        notifyChanged()
    }

    fun activityStopped() {
        visibleCount = maxOf(0, visibleCount - 1)
        notifyChanged()
    }

    // ---------- sleep timer ----------
    private val handler = Handler(Looper.getMainLooper())
    private var sleepAt = 0L
    private val sleepRunnable = Runnable {
        sleepAt = 0L
        pause()
    }

    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            sleepAt = 0L
        } else {
            sleepAt = SystemClock.elapsedRealtime() + minutes * 60_000L
            handler.postDelayed(sleepRunnable, minutes * 60_000L)
        }
        notifyChanged()
    }

    fun sleepRemainingMs(): Long =
        if (sleepAt == 0L) 0L else maxOf(0L, sleepAt - SystemClock.elapsedRealtime())

    // ---------- fade between songs (fade-out phir fade-in, overlap nahi) ----------
    private var duck = 1f
    private var fade = 1f
    private var playStartedAt = 0L
    private val fadeTick = object : Runnable {
        override fun run() {
            if (!isPlaying || SettingsManager.crossfadeSec <= 0) {
                fade = 1f
                applyVol()
                return
            }
            updateFade()
            handler.postDelayed(this, 200)
        }
    }

    private fun updateFade() {
        val ms = SettingsManager.crossfadeSec * 1000L
        val dur = current?.durationMs ?: 0L
        val remaining = dur - position().toLong()
        var f = 1f
        val since = SystemClock.elapsedRealtime() - playStartedAt
        val inLen = maxOf(1000L, ms / 2)
        if (since < inLen) f = minOf(f, since.toFloat() / inLen)
        if (dur > 0 && remaining < ms) f = minOf(f, remaining.toFloat() / ms)
        fade = f.coerceIn(0f, 1f)
        applyVol()
    }

    private fun applyVol() {
        val v = duck * fade
        try { player?.setVolume(v, v) } catch (e: Exception) { }
    }

    private fun startFade() {
        handler.removeCallbacks(fadeTick)
        if (SettingsManager.crossfadeSec > 0 && isPlaying) {
            handler.post(fadeTick)
        } else {
            fade = 1f
            applyVol()
        }
    }

    /** Settings mein fade badalne par call hota hai. */
    fun applyFadeSetting() {
        startFade()
    }

    val current: Song?
        get() = queue.getOrNull(index)

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        SettingsManager.init(app)
        MusicRepository.init(app)
        MetadataManager.init(app)
        PlaylistManager.init(app)
        EqualizerManager.init(app)
        shuffle = SettingsManager.shuffle
        repeat = SettingsManager.repeat
    }

    fun addListener(l: Listener) { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }
    fun notifyChanged() { for (l in ArrayList(listeners)) l.onChanged() }

    // ---------- audio focus ----------
    private val audioManager: AudioManager
        get() = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun attrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocus = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) { resumeOnFocus = true; pauseInternal() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> setVol(0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                setVol(1f)
                if (resumeOnFocus) { resumeOnFocus = false; play() }
            }
        }
    }

    private fun setVol(v: Float) {
        duck = v
        applyVol()
    }

    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs())
            .setOnAudioFocusChangeListener(focusListener)
            .build().also { focusRequest = it }
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun ensureService() {
        try {
            app.startForegroundService(Intent(app, PlaybackService::class.java))
        } catch (e: Exception) {
            // background se start allowed nahi: ignore
        }
    }

    /** Metadata badalne ya rescan ke baad queue ke gaanon ko naye naam de deta hai. */
    fun updateSongs(map: Map<Long, Song>) {
        queue = queue.map { map[it.id] ?: it }
        originalQueue = originalQueue.map { map[it.id] ?: it }
        notifyChanged()
    }

    // ---------- queue ----------
    fun playQueue(songs: List<Song>, start: Int) {
        if (songs.isEmpty() || start !in songs.indices) return
        originalQueue = songs
        if (shuffle) {
            val first = songs[start]
            queue = listOf(first) + songs.filterIndexed { i, _ -> i != start }.shuffled()
            playAt(0)
        } else {
            queue = songs
            playAt(start)
        }
    }

    fun restore(songs: List<Song>, i: Int, posMs: Int) {
        if (i !in songs.indices) return
        originalQueue = songs
        queue = songs
        playAt(i, false, posMs)
    }

    fun playNext(s: Song) {
        if (queue.isEmpty()) { playQueue(listOf(s), 0); return }
        val cur = current
        val q = queue.toMutableList()
        q.add(index + 1, s)
        queue = q
        val o = originalQueue.toMutableList()
        val ci = o.indexOfFirst { it.id == cur?.id }
        o.add(if (ci >= 0) ci + 1 else o.size, s)
        originalQueue = o
        notifyChanged()
    }

    fun enqueue(s: Song) {
        if (queue.isEmpty()) { playQueue(listOf(s), 0); return }
        queue = queue + s
        originalQueue = originalQueue + s
        notifyChanged()
    }

    fun setShuffle(on: Boolean) {
        if (on == shuffle) return
        shuffle = on
        SettingsManager.shuffle = on
        val cur = current
        if (on) {
            if (cur != null) {
                val rest = queue.filterIndexed { i, _ -> i != index }.shuffled()
                queue = listOf(cur) + rest
                index = 0
            }
        } else {
            queue = originalQueue
            index = if (cur == null) -1 else queue.indexOfFirst { it.id == cur.id }
        }
        notifyChanged()
    }

    fun cycleRepeat() {
        repeat = (repeat + 1) % 3
        SettingsManager.repeat = repeat
        notifyChanged()
    }

    // ---------- playback ----------
    fun playAt(i: Int, autoStart: Boolean = true, seekMs: Int = 0) {
        if (i !in queue.indices) return
        index = i
        val s = queue[i]
        releasePlayer()
        pendingStart = autoStart
        pendingSeek = seekMs
        isPlaying = autoStart
        SettingsManager.lastId = s.id
        SettingsManager.lastPos = seekMs
        if (autoStart) {
            MusicRepository.addRecent(s.id)
            MusicRepository.addPlay(s.id)
        }
        val mp = MediaPlayer()
        player = mp
        prepared = false
        EqualizerManager.attach(mp.audioSessionId)
        try {
            mp.setAudioAttributes(attrs())
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(app, s.uri)
            mp.setOnPreparedListener { onPrepared(it) }
            mp.setOnCompletionListener { onCompleted() }
            mp.setOnErrorListener { _, _, _ -> onError(); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            onError()
            return
        }
        notifyChanged()
    }

    private fun onPrepared(mp: MediaPlayer) {
        if (mp !== player) return
        prepared = true
        errorCount = 0
        if (pendingSeek > 0) {
            try { mp.seekTo(pendingSeek) } catch (e: Exception) { }
        }
        pendingSeek = 0
        if (pendingStart && requestFocus()) {
            try {
                playStartedAt = SystemClock.elapsedRealtime()
                if (SettingsManager.crossfadeSec > 0) fade = 0f
                applyVol()
                mp.start()
                isPlaying = true
                startFade()
                if (!serviceActive) ensureService()
            } catch (e: Exception) {
                isPlaying = false
            }
        } else {
            isPlaying = false
        }
        notifyChanged()
    }

    private fun onCompleted() {
        if (repeat == 2) playAt(index) else next(true)
    }

    private fun onError() {
        errorCount++
        if (errorCount > 5 || queue.size < 2) {
            releasePlayer()
            notifyChanged()
            return
        }
        next(true)
    }

    fun play() {
        if (current == null) return
        val mp = player
        if (mp == null) { playAt(index); return }
        if (!prepared) { pendingStart = true; isPlaying = true; notifyChanged(); return }
        if (!requestFocus()) return
        try { mp.start() } catch (e: Exception) { return }
        isPlaying = true
        startFade()
        if (!serviceActive) ensureService()
        notifyChanged()
    }

    fun pause() {
        pauseInternal()
        abandonFocus()
    }

    private fun pauseInternal() {
        pendingStart = false
        val mp = player
        if (mp != null && prepared) {
            try { if (mp.isPlaying) mp.pause() } catch (e: Exception) { }
        }
        isPlaying = false
        handler.removeCallbacks(fadeTick)
        fade = 1f
        applyVol()
        savePosition()
        notifyChanged()
    }

    fun toggle() { if (isPlaying) pause() else play() }

    fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return
        val n = index + 1
        when {
            n < queue.size -> playAt(n)
            repeat == 1 || !auto -> playAt(0)
            else -> playAt(0, false) // queue khatam: pehla gaana ready, paused
        }
    }

    fun prev() {
        if (queue.isEmpty()) return
        if (position() > 3000) { seekTo(0); return }
        playAt(if (index > 0) index - 1 else queue.size - 1)
    }

    fun seekTo(ms: Int) {
        val mp = player
        if (mp != null && prepared) {
            try { mp.seekTo(ms) } catch (e: Exception) { }
        } else {
            pendingSeek = ms
        }
        notifyChanged()
    }

    fun position(): Int {
        val mp = player
        return if (mp != null && prepared) {
            try { mp.currentPosition } catch (e: Exception) { 0 }
        } else pendingSeek
    }

    fun savePosition() {
        val s = current ?: return
        SettingsManager.lastId = s.id
        SettingsManager.lastPos = position()
    }

    private fun releasePlayer() {
        EqualizerManager.release()
        val mp = player
        player = null
        prepared = false
        isPlaying = false
        if (mp != null) {
            try {
                mp.setOnPreparedListener(null)
                mp.setOnCompletionListener(null)
                mp.setOnErrorListener(null)
                mp.release()
            } catch (e: Exception) { }
        }
    }
}
