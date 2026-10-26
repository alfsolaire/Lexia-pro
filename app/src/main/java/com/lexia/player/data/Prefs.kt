package com.lexia.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LoginConfig(
    val mode: String,
    val server: String,
    val username: String,
    val password: String,
    val m3uUrl: String,
) {
    fun defaultName(): String =
        if (mode == MODE_M3U) "Playlist M3U"
        else "$username @ ${server.removePrefix("http://").removePrefix("https://").substringBefore('/')}"

    fun sameSource(other: LoginConfig): Boolean =
        mode == other.mode &&
            if (mode == MODE_M3U) m3uUrl == other.m3uUrl else server == other.server && username == other.username

    companion object {
        const val MODE_XTREAM = "xtream"
        const val MODE_M3U = "m3u"
    }
}

data class Account(val id: String, val name: String, val config: LoginConfig)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lexia_player", Context.MODE_PRIVATE)
    private val resume = context.getSharedPreferences("lexia_resume", Context.MODE_PRIVATE)

    init {
        migrateSingleAccount()
    }

    // ---------- Comptes ----------

    fun accounts(): List<Account> {
        val array = runCatching { JSONArray(sp.getString("accounts", "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { o ->
            Account(
                id = o.optString("id"),
                name = o.optString("name"),
                config = LoginConfig(
                    mode = o.optString("mode", LoginConfig.MODE_XTREAM),
                    server = o.optString("server"),
                    username = o.optString("username"),
                    password = o.optString("password"),
                    m3uUrl = o.optString("m3u_url"),
                ),
            )
        }
    }

    private fun saveAccounts(list: List<Account>) {
        val array = JSONArray()
        list.forEach { a ->
            array.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("mode", a.config.mode)
                    .put("server", a.config.server)
                    .put("username", a.config.username)
                    .put("password", a.config.password)
                    .put("m3u_url", a.config.m3uUrl),
            )
        }
        sp.edit().putString("accounts", array.toString()).apply()
    }

    val currentAccount: Account?
        get() {
            val id = sp.getString("current", null) ?: return null
            return accounts().find { it.id == id }
        }

    val loggedIn: Boolean get() = sp.getBoolean("logged_in", false) && currentAccount != null

    fun load(): LoginConfig? = currentAccount?.config

    /** Ajoute le compte (ou met à jour celui qui a la même source) et le rend actif. */
    fun saveAccount(name: String, config: LoginConfig): Account {
        val list = accounts().toMutableList()
        val index = list.indexOfFirst { it.config.sameSource(config) }
        val account = if (index >= 0) {
            list[index].copy(name = name, config = config).also { list[index] = it }
        } else {
            Account(System.currentTimeMillis().toString(), name, config).also { list.add(it) }
        }
        saveAccounts(list)
        switchTo(account.id)
        return account
    }

    fun switchTo(id: String) {
        sp.edit().putString("current", id).putBoolean("logged_in", true).apply()
    }

    fun removeAccount(id: String) {
        saveAccounts(accounts().filter { it.id != id })
        val editor = sp.edit().remove("fav:$id")
        ContentType.entries.forEach { editor.remove("recent:$id:$it").remove("pinned:$id:$it") }
        if (sp.getString("current", null) == id) editor.remove("current").putBoolean("logged_in", false)
        editor.apply()
    }

    private val accountId: String get() = sp.getString("current", null) ?: "none"

    // ---------- Réglages ----------

    /** "ts" (MPEG-TS) ou "m3u8" (HLS) pour les chaînes en direct Xtream. */
    var liveFormat: String
        get() = sp.getString("live_format", "ts") ?: "ts"
        set(value) = sp.edit().putString("live_format", value).apply()

    // ---------- Favoris et historique (par compte) ----------

    fun favorites(): Set<String> = sp.getStringSet("fav:$accountId", emptySet())?.toSet() ?: emptySet()

    fun toggleFavorite(key: String): Set<String> {
        val current = favorites().toMutableSet()
        if (!current.remove(key)) current.add(key)
        sp.edit().putStringSet("fav:$accountId", current).apply()
        return current
    }

    /** Clés ([favoriteKey]) des derniers contenus ouverts, du plus récent au plus ancien. */
    fun recents(type: ContentType): List<String> =
        sp.getString("recent:$accountId:$type", "").orEmpty().split('\n').filter { it.isNotEmpty() }

    fun addRecent(type: ContentType, key: String) {
        val list = (listOf(key) + recents(type).filter { it != key }).take(MAX_RECENTS)
        sp.edit().putString("recent:$accountId:$type", list.joinToString("\n")).apply()
    }

    // ---------- Classement des catégories ----------

    /** Catégories épinglées en haut de la liste, dans l'ordre où elles ont été épinglées. */
    fun pinnedCategories(type: ContentType): List<String> =
        sp.getString("pinned:$accountId:$type", "").orEmpty().split('\n').filter { it.isNotEmpty() }

    fun togglePinned(type: ContentType, categoryId: String): List<String> {
        val current = pinnedCategories(type)
        val updated = if (categoryId in current) current - categoryId else current + categoryId
        sp.edit().putString("pinned:$accountId:$type", updated.joinToString("\n")).apply()
        return updated
    }

    /** true : catégories triées de A à Z ; false : ordre du serveur. */
    var sortCategoriesAlpha: Boolean
        get() = sp.getBoolean("sort_alpha", false)
        set(value) = sp.edit().putBoolean("sort_alpha", value).apply()

    // ---------- Reprise de lecture (clé : URL du flux) ----------

    fun resumePosition(url: String): Long? = resume.getString(url, null)?.substringBefore('/')?.toLongOrNull()

    fun saveResume(url: String, positionMs: Long, durationMs: Long) {
        resume.edit().putString(url, "$positionMs/$durationMs").apply()
    }

    fun clearResume(url: String) {
        resume.edit().remove(url).apply()
    }

    /** Avancement (0..1) de chaque film ou épisode commencé. */
    fun resumeProgress(): Map<String, Float> = resume.all.mapNotNull { (url, value) ->
        val text = value as? String ?: return@mapNotNull null
        val position = text.substringBefore('/').toFloatOrNull()
        val duration = text.substringAfter('/').toFloatOrNull()
        if (position == null || duration == null || duration <= 0f) null
        else url to (position / duration).coerceIn(0f, 1f)
    }.toMap()

    /** Reprend l'ancien format à compte unique (version 1.0). */
    private fun migrateSingleAccount() {
        if (sp.contains("accounts")) return
        val mode = sp.getString("mode", null)
        if (mode == null) {
            sp.edit().putString("accounts", "[]").apply()
            return
        }
        val config = LoginConfig(
            mode = mode,
            server = sp.getString("server", "").orEmpty(),
            username = sp.getString("username", "").orEmpty(),
            password = sp.getString("password", "").orEmpty(),
            m3uUrl = sp.getString("m3u_url", "").orEmpty(),
        )
        val id = "1"
        saveAccounts(listOf(Account(id, config.defaultName(), config)))
        val editor = sp.edit().putString("current", id)
        sp.getStringSet("favorites", null)?.let { editor.putStringSet("fav:$id", it.toSet()) }
        editor.remove("mode").remove("server").remove("username").remove("password")
            .remove("m3u_url").remove("favorites")
            .apply()
    }

    companion object {
        private const val MAX_RECENTS = 30
    }
}
