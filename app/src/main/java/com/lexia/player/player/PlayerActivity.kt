package com.lexia.player.player

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.lexia.player.billing.Licensing
import com.lexia.player.data.Content
import com.lexia.player.data.ContentType
import com.lexia.player.data.Prefs
import com.lexia.player.data.Session
import com.lexia.player.data.favoriteKey
import com.lexia.player.ui.timeRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var overlay: TextView
    private var player: ExoPlayer? = null
    private lateinit var prefs: Prefs
    private var current: Content? = null
    private var epgJob: Job? = null
    private var pausedAt = 0L
    private var lastLiveRetry = 0L
    private var recorder: LiveRecorder? = null
    private var inTimeshift = false
    private var timeshiftDelayMs = 0L
    private val startRecorder = Runnable { recorder?.start() }
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }
    private val periodicSave = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    private val isLive: Boolean
        get() = PlayQueue.items.getOrNull(PlayQueue.index)?.type == ContentType.LIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayQueue.items.isEmpty() || Licensing.locked) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setKeepContentOnPlayerReset(true) // garde l'image figée pendant la pause du direct
            controllerShowTimeoutMs = 4000
            keepScreenOn = true
        }
        overlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(36, 20, 36, 20)
            setBackgroundColor(0xB0000000.toInt())
            visibility = View.GONE
        }
        val root = FrameLayout(this)
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            overlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
                .apply { setMargins(56, 48, 56, 0) },
        )
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        if (::playerView.isInitialized) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initPlayer() {
        if (player != null) return
        PreviewPlayer.stop()
        val exo = buildExoPlayer(this, pauseBuffer = true)
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // En direct (ou en différé), une coupure renvoie au direct, une fois toutes les 20 s au plus.
                val now = System.currentTimeMillis()
                if (isLive && now - lastLiveRetry > 20_000) {
                    lastLiveRetry = now
                    showOverlay("Reconnexion au direct…", 3000)
                    play(PlayQueue.index)
                    return
                }
                showOverlay("Lecture impossible (${error.errorCodeName}). Essayez une autre chaîne ou changez le format du direct.", 8000)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isLive) handleLivePause(playWhenReady)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && inTimeshift) {
                    play(PlayQueue.index)
                    showOverlay("▶  Fin du différé : retour au direct", 3500)
                    return
                }
                if (state != Player.STATE_ENDED || isLive) return
                current?.url?.let { prefs.clearResume(it) }
                current = null
                // Enchaîne automatiquement l'épisode suivant.
                if (PlayQueue.index + 1 < PlayQueue.items.size) play(PlayQueue.index + 1)
            }
        })
        exo.playWhenReady = true
        player = exo
        playerView.player = exo
        playerView.requestFocus()
        play(PlayQueue.index)
        handler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
    }

    private fun releasePlayer() {
        stopTimeshift()
        saveProgress()
        epgJob?.cancel()
        handler.removeCallbacks(periodicSave)
        handler.removeCallbacks(hideOverlay)
        player?.release()
        player = null
        if (::playerView.isInitialized) playerView.player = null
    }

    private fun play(index: Int) {
        val items = PlayQueue.items
        if (items.isEmpty()) return
        val i = ((index % items.size) + items.size) % items.size
        PlayQueue.index = i
        val item = items[i]
        val url = item.url ?: return
        stopTimeshift()
        saveProgress()
        current = item
        pausedAt = 0
        // Les épisodes sont mémorisés via leur série (au moment de l'ouvrir).
        if (item.type != ContentType.SERIES) prefs.addRecent(item.type, favoriteKey(item))

        val resumeAt = if (item.type == ContentType.LIVE) null else prefs.resumePosition(url)
        player?.run {
            if (resumeAt != null) setMediaItem(mediaItemFor(url), resumeAt) else setMediaItem(mediaItemFor(url))
            prepare()
            play()
        }

        epgJob?.cancel()
        if (item.type == ContentType.LIVE) {
            val title = "${i + 1}.  ${item.name}"
            showOverlay(title, 4000)
            val provider = Session.provider ?: return
            epgJob = lifecycleScope.launch {
                val epg = try {
                    provider.shortEpg(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val now = System.currentTimeMillis()
                val nowShowing = epg.firstOrNull { it.start <= now && it.end > now }
                val next = epg.firstOrNull { it.start >= (nowShowing?.end ?: now) }
                if (nowShowing == null && next == null) return@launch
                val text = buildString {
                    append(title)
                    nowShowing?.let { append("\nEn cours : ${it.title}  (${timeRange(it)})") }
                    next?.let { append("\nEnsuite : ${it.title}  (${timeRange(it)})") }
                }
                showOverlay(text, 6000)
            }
        } else if (resumeAt != null) {
            showOverlay("${item.name}\nReprise à ${formatTime(resumeAt)}", 4000)
        } else {
            showOverlay(item.name, 3500)
        }
    }

    /** Mémorise la position d'un film ou d'un épisode pour reprendre plus tard. */
    private fun saveProgress() {
        val item = current ?: return
        if (item.type == ContentType.LIVE) return
        val url = item.url ?: return
        val p = player ?: return
        val duration = p.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val position = p.currentPosition
        when {
            position >= duration * 0.95 -> prefs.clearResume(url)
            position > MIN_RESUME_MS -> prefs.saveResume(url, position, duration)
        }
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun showOverlay(text: String, durationMs: Long) {
        overlay.text = text
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, durationMs)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // En direct, haut/bas (ou CH+/CH-) changent de chaîne quand les commandes sont masquées.
        if (inTimeshift && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            play(PlayQueue.index)
            showOverlay("▶  Retour au direct", 2500)
            return true
        }
        if (isLive && event.action == KeyEvent.ACTION_DOWN && ::playerView.isInitialized && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    play(PlayQueue.index + 1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    play(PlayQueue.index - 1)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Pause du direct. Pour un flux MPEG-TS, la chaîne est enregistrée sur l'appareil pendant la pause
     * puis relue en différé. Pour un flux HLS, on s'appuie sur la mémoire tampon du lecteur.
     */
    private fun handleLivePause(playWhenReady: Boolean) {
        val p = player ?: return
        val url = current?.url ?: return
        if (!playWhenReady) {
            pausedAt = System.currentTimeMillis()
            if (!inTimeshift && recorder == null && !url.contains(".m3u8", ignoreCase = true)) {
                val maxBytes = minOf(MAX_TIMESHIFT_BYTES, cacheDir.usableSpace - 300L * 1024 * 1024)
                if (maxBytes > 50L * 1024 * 1024) {
                    // On libère la connexion du lecteur (beaucoup d'abonnements n'en autorisent qu'une)
                    // puis on enregistre la suite du direct.
                    p.stop()
                    recorder = LiveRecorder(url, File(cacheDir, "timeshift.ts"), maxBytes)
                    handler.postDelayed(startRecorder, 1_500)
                }
            }
            showOverlay("⏸  En pause\nAppuyez sur ⏯ pour reprendre là où vous en étiez", 600_000)
            return
        }
        if (pausedAt == 0L) return
        val pausedFor = System.currentTimeMillis() - pausedAt
        pausedAt = 0
        val rec = recorder
        when {
            rec != null && !inTimeshift -> {
                handler.removeCallbacks(startRecorder)
                if (!rec.running && rec.bytesWritten.get() == 0L) {
                    // L'enregistrement n'a pas démarré (pause très courte ou connexion refusée).
                    play(PlayQueue.index)
                    return
                }
                inTimeshift = true
                timeshiftDelayMs = pausedFor
                val source = ProgressiveMediaSource.Factory { GrowingFileDataSource(rec) }
                    .createMediaSource(MediaItem.fromUri(Uri.fromFile(rec.file)))
                p.setMediaSource(source)
                p.prepare()
                p.play()
                showOverlay("▶  Différé : ${formatTime(timeshiftDelayMs)} de retard\n⏩ ou ▲/▼ pour revenir au direct", 5000)
            }
            inTimeshift -> {
                timeshiftDelayMs += pausedFor
                showOverlay("▶  Différé : ${formatTime(timeshiftDelayMs)} de retard\n⏩ ou ▲/▼ pour revenir au direct", 5000)
            }
            else -> showOverlay("▶  Reprise", 2000)
        }
    }

    private fun stopTimeshift() {
        handler.removeCallbacks(startRecorder)
        recorder?.delete()
        recorder = null
        inTimeshift = false
        timeshiftDelayMs = 0
    }

    companion object {
        private const val MAX_TIMESHIFT_BYTES = 2L * 1024 * 1024 * 1024
        private const val SAVE_INTERVAL_MS = 15_000L
        private const val MIN_RESUME_MS = 30_000L
    }
}
