package com.lexia.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** Lit une playlist M3U / M3U8 étendue (#EXTINF avec tvg-logo, group-title…). */
class M3uProvider(private val url: String) : IptvProvider {

    private val mutex = Mutex()
    private var entries: List<Content>? = null

    private suspend fun all(): List<Content> = mutex.withLock {
        entries ?: withContext(Dispatchers.Default) { parse(Http.get(url)) }.also {
            if (it.isEmpty()) throw IOException("La playlist est vide ou n'est pas au format M3U")
            entries = it
        }
    }

    override suspend fun accountInfo(): AccountInfo {
        val list = all()
        return AccountInfo("Playlist M3U", "${list.size} entrées", null, null, null)
    }

    override suspend fun categories(type: ContentType): List<Category> =
        all().asSequence().filter { it.type == type }.map { it.categoryId }.distinct()
            .map { Category(it, it) }.toList()

    override suspend fun items(type: ContentType): List<Content> = all().filter { it.type == type }

    override suspend fun seriesDetails(series: Content) = SeriesDetails(null, null, emptyMap())

    companion object {
        private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
        private const val NO_GROUP = "Sans catégorie"

        fun parse(text: String): List<Content> {
            val out = ArrayList<Content>()
            var name: String? = null
            var logo: String? = null
            var group: String? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        val lastQuote = line.lastIndexOf('"')
                        val comma = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
                        name = (if (comma >= 0) line.substring(comma + 1).trim() else "")
                            .ifEmpty { attrs["tvg-name"] ?: "Sans nom" }
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                        group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) ->
                        if (group == null) group = line.substringAfter(':').trim().ifEmpty { null }
                    line.isEmpty() || line.startsWith("#") -> Unit
                    else -> {
                        val type = when {
                            line.contains("/movie/") -> ContentType.MOVIE
                            line.contains("/series/") -> ContentType.SERIES
                            else -> ContentType.LIVE
                        }
                        out += Content(line, name ?: line.substringAfterLast('/'), logo, group ?: NO_GROUP, type, line)
                        name = null; logo = null; group = null
                    }
                }
            }
            return out
        }
    }
}
