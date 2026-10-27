package com.lexia.player.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lexia.player.data.Http

/** Construction commune des lecteurs (plein écran et aperçu). */
@OptIn(UnstableApi::class)
fun buildExoPlayer(context: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Http.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
        .build()
}

fun mediaItemFor(url: String): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    if (url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    return builder.build()
}

/**
 * Lecteur de l'aperçu (fenêtre en haut à droite de la liste des chaînes).
 * Il est arrêté avant d'ouvrir le plein écran : beaucoup d'abonnements n'autorisent qu'une connexion.
 */
object PreviewPlayer {
    private var player: ExoPlayer? = null
    private var currentUrl: String? = null

    fun get(context: Context): ExoPlayer =
        player ?: buildExoPlayer(context.applicationContext).also {
            it.volume = 1f
            player = it
        }

    fun play(context: Context, url: String) {
        val p = get(context)
        if (url == currentUrl && p.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            p.play()
            return
        }
        currentUrl = url
        p.setMediaItem(mediaItemFor(url))
        p.prepare()
        p.playWhenReady = true
    }

    fun stop() {
        currentUrl = null
        player?.stop()
        player?.clearMediaItems()
    }

    fun release() {
        currentUrl = null
        player?.release()
        player = null
    }
}
