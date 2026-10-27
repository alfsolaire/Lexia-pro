package com.lexia.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "LexiaPlayer/1.0 (Linux; Android) ExoPlayer"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Lit la réponse ligne par ligne, sans la charger entièrement en mémoire (grosses playlists). */
    suspend fun <T> readLines(url: String, block: (Sequence<String>) -> T): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Le serveur a répondu HTTP ${response.code}")
            val body = response.body ?: throw IOException("Réponse vide")
            body.charStream().buffered().useLines(block)
        }
    }

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Le serveur a répondu HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }
}
