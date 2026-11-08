package com.ashmit.kasturimusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.SystemClock

/** Background playback + notification + lock-screen controls. Player ka kaam AudioPlayerManager karta hai. */
class PlaybackService : Service(), AudioPlayerManager.Listener {

    companion object {
        const val CHANNEL = "kasturi_playback"
        const val NOTIF_ID = 11
        const val A_TOGGLE = "com.ashmit.kasturimusic.TOGGLE"
        const val A_NEXT = "com.ashmit.kasturimusic.NEXT"
        const val A_PREV = "com.ashmit.kasturimusic.PREV"
        const val A_STOP = "com.ashmit.kasturimusic.STOP"
    }

    private lateinit var session: MediaSession
    private var foreground = false
    private var lastArtKey = ""
    private var shakeOn = false

    private val shakeListener = object : SensorEventListener {
        private var last = 0L
        override fun onSensorChanged(e: SensorEvent) {
            val x = e.values[0]
            val y = e.values[1]
            val z = e.values[2]
            val g = Math.sqrt((x * x + y * y + z * z).toDouble()) / 9.81
            if (g > 2.6) {
                val now = SystemClock.elapsedRealtime()
                if (now - last > 1200L) {
                    last = now
                    AudioPlayerManager.next()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** Shake sensor sirf tab chalta hai jab setting on ho aur gaana baj raha ho (battery bachane ke liye). */
    private fun updateShake() {
        val want = SettingsManager.shake && AudioPlayerManager.isPlaying
        if (want == shakeOn) return
        val sm = getSystemService(SensorManager::class.java) ?: return
        if (want) {
            val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(shakeListener, acc, SensorManager.SENSOR_DELAY_UI)
            shakeOn = true
        } else {
            sm.unregisterListener(shakeListener)
            shakeOn = false
        }
    }

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) AudioPlayerManager.pause()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AudioPlayerManager.init(this)
        AudioPlayerManager.serviceActive = true
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)

        session = MediaSession(this, "KasturiMusic")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { AudioPlayerManager.play() }
            override fun onPause() { AudioPlayerManager.pause() }
            override fun onStop() { AudioPlayerManager.pause() }
            override fun onSkipToNext() { AudioPlayerManager.next() }
            override fun onSkipToPrevious() { AudioPlayerManager.prev() }
            override fun onSeekTo(pos: Long) { AudioPlayerManager.seekTo(pos.toInt()) }
        })
        session.isActive = true
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        AudioPlayerManager.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            A_TOGGLE -> AudioPlayerManager.toggle()
            A_NEXT -> AudioPlayerManager.next()
            A_PREV -> AudioPlayerManager.prev()
            A_STOP -> {
                AudioPlayerManager.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                foreground = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        update()
        return START_NOT_STICKY
    }

    override fun onChanged() { update() }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!AudioPlayerManager.isPlaying) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        AudioPlayerManager.serviceActive = false
        AudioPlayerManager.removeListener(this)
        try { getSystemService(SensorManager::class.java)?.unregisterListener(shakeListener) } catch (e: Exception) { }
        try { unregisterReceiver(noisy) } catch (e: Exception) { }
        session.release()
        super.onDestroy()
    }

    private fun action(icon: Int, title: String, act: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, code, Intent(this, PlaybackService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun update() {
        val s = AudioPlayerManager.current
        val playing = AudioPlayerManager.isPlaying
        var art: Bitmap? = null
        if (s != null) {
            art = ArtworkLoader.cached(s, 256)
            val key = ArtworkLoader.key(s, 256)
            if (art == null && key != lastArtKey) {
                lastArtKey = key
                ArtworkLoader.load(this, s, 256) { update() }
            }
            val md = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs)
            if (art != null) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
            session.setMetadata(md.build())
        }
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    AudioPlayerManager.position().toLong(), 1f
                )
                .build()
        )

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(s?.title ?: "Kasturi Music")
            .setContentText(s?.artist ?: "")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (art != null) b.setLargeIcon(art)
        b.addAction(action(R.drawable.ic_prev, "Previous", A_PREV, 1))
        b.addAction(action(if (playing) R.drawable.ic_pause else R.drawable.ic_play, "Play/Pause", A_TOGGLE, 2))
        b.addAction(action(R.drawable.ic_next, "Next", A_NEXT, 3))
        b.addAction(action(R.drawable.ic_close, "Close", A_STOP, 4))
        b.setStyle(
            Notification.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        val n = b.build()

        if (!foreground) {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, n)
            }
            foreground = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        }
        updateShake()
        if (s == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
        }
    }
}
