package com.lexia.player.player

import android.graphics.Color
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.lexia.player.data.ContentType
import com.lexia.player.data.Http

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var overlay: TextView
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }

    private val isLive: Boolean
        get() = PlayQueue.items.getOrNull(PlayQueue.index)?.type == ContentType.LIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayQueue.items.isEmpty()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
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
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showOverlay("Lecture impossible (${error.errorCodeName}). Essayez une autre chaîne ou changez le format du direct.", 8000)
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Enchaîne automatiquement l'épisode suivant.
                if (state == Player.STATE_ENDED && !isLive && PlayQueue.index + 1 < PlayQueue.items.size) {
                    play(PlayQueue.index + 1)
                }
            }
        })
        exo.playWhenReady = true
        player = exo
        playerView.player = exo
        playerView.requestFocus()
        play(PlayQueue.index)
    }

    private fun releasePlayer() {
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
        val builder = MediaItem.Builder().setUri(url)
        if (url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        player?.run {
            setMediaItem(builder.build())
            prepare()
            play()
        }
        showOverlay(if (item.type == ContentType.LIVE) "${i + 1}.  ${item.name}" else item.name, 3500)
    }

    private fun showOverlay(text: String, durationMs: Long) {
        overlay.text = text
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, durationMs)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // En direct, haut/bas (ou CH+/CH-) changent de chaîne quand les commandes sont masquées.
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
}
