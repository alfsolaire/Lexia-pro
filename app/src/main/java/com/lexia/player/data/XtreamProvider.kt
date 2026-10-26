package com.lexia.player.data

import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder

/** Client de l'API « player_api.php » des panneaux Xtream Codes. */
class XtreamProvider(
    server: String,
    private val username: String,
    private val password: String,
    private val liveFormat: String,
) : IptvProvider {

    private val base = normalizeServer(server)
    private val user = Uri.encode(username)
    private val pass = Uri.encode(password)
    private val mutex = Mutex()
    private val cache = mutableMapOf<ContentType, List<Content>>()

    private fun api(action: String? = null, extra: String = ""): String {
        val u = URLEncoder.encode(username, "UTF-8")
        val p = URLEncoder.encode(password, "UTF-8")
        val actionPart = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=$u&password=$p$actionPart$extra"
    }

    override suspend fun accountInfo(): AccountInfo {
        val json = JSONTokener(Http.get(api())).nextValue() as? JSONObject
            ?: throw IOException("Réponse invalide : vérifiez l'adresse du serveur")
        val info = json.optJSONObject("user_info") ?: throw IOException("Réponse invalide : vérifiez l'adresse du serveur")
        if (info.optInt("auth", 1) == 0) throw IOException("Identifiants refusés par le serveur")
        return AccountInfo(
            username = info.optString("username", username),
            status = info.str("status"),
            expiry = info.str("exp_date")?.toLongOrNull(),
            activeConnections = info.str("active_cons"),
            maxConnections = info.str("max_connections"),
        )
    }

    override suspend fun categories(type: ContentType): List<Category> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        return parseArray(Http.get(api(action))).objects().map {
            Category(it.optString("category_id"), it.optString("category_name", "Sans nom"))
        }.distinctBy { it.id }
    }

    override suspend fun items(type: ContentType): List<Content> = mutex.withLock {
        cache[type] ?: load(type).also { cache[type] = it }
    }

    private suspend fun load(type: ContentType): List<Content> = when (type) {
        ContentType.LIVE -> parseArray(Http.get(api("get_live_streams"))).objects().map {
            val id = it.optString("stream_id")
            Content(id, it.optString("name"), it.str("stream_icon"), it.optString("category_id"), type,
                "$base/live/$user/$pass/$id.$liveFormat")
        }
        ContentType.MOVIE -> parseArray(Http.get(api("get_vod_streams"))).objects().map {
            val id = it.optString("stream_id")
            val ext = it.str("container_extension") ?: "mp4"
            Content(id, it.optString("name"), it.str("stream_icon"), it.optString("category_id"), type,
                "$base/movie/$user/$pass/$id.$ext")
        }
        ContentType.SERIES -> parseArray(Http.get(api("get_series"))).objects().map {
            Content(it.optString("series_id"), it.optString("name"), it.str("cover"), it.optString("category_id"), type, null)
        }
    }

    override suspend fun seriesDetails(series: Content): SeriesDetails {
        val json = JSONTokener(Http.get(api("get_series_info", "&series_id=${series.id}"))).nextValue() as? JSONObject
            ?: return SeriesDetails(null, null, emptyMap())
        val info = json.optJSONObject("info")
        val seasons = sortedMapOf<Int, List<Episode>>()
        val episodes = json.optJSONObject("episodes")
        if (episodes != null) {
            for (key in episodes.keys()) {
                val list = episodes.optJSONArray(key) ?: continue
                val season = key.toIntOrNull() ?: continue
                seasons[season] = list.objects().map { ep ->
                    val id = ep.optString("id")
                    val ext = ep.str("container_extension") ?: "mp4"
                    val epInfo = ep.optJSONObject("info")
                    Episode(
                        id = id,
                        title = ep.str("title") ?: "Épisode",
                        season = season,
                        number = ep.optInt("episode_num", 0),
                        url = "$base/series/$user/$pass/$id.$ext",
                        cover = epInfo?.str("movie_image"),
                        plot = epInfo?.str("plot"),
                    )
                }.sortedBy { it.number }
            }
        }
        return SeriesDetails(info?.str("plot"), info?.str("cover"), seasons)
    }

    companion object {
        fun normalizeServer(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            return s
        }
    }
}

private fun parseArray(text: String): JSONArray =
    JSONTokener(text).nextValue() as? JSONArray ?: JSONArray()

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

/** Valeur texte non vide, ou null (l'API renvoie souvent null, "" ou "null"). */
private fun JSONObject.str(name: String): String? {
    if (isNull(name)) return null
    val v = optString(name, "").trim()
    return if (v.isEmpty() || v == "null") null else v
}
