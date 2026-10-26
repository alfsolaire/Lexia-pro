package com.lexia.player.data

import android.content.Context

data class LoginConfig(
    val mode: String,
    val server: String,
    val username: String,
    val password: String,
    val m3uUrl: String,
) {
    companion object {
        const val MODE_XTREAM = "xtream"
        const val MODE_M3U = "m3u"
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lexia_player", Context.MODE_PRIVATE)

    val loggedIn: Boolean get() = sp.getBoolean("logged_in", false)

    fun load(): LoginConfig? {
        val mode = sp.getString("mode", null) ?: return null
        return LoginConfig(
            mode = mode,
            server = sp.getString("server", "").orEmpty(),
            username = sp.getString("username", "").orEmpty(),
            password = sp.getString("password", "").orEmpty(),
            m3uUrl = sp.getString("m3u_url", "").orEmpty(),
        )
    }

    fun save(config: LoginConfig) {
        sp.edit()
            .putString("mode", config.mode)
            .putString("server", config.server)
            .putString("username", config.username)
            .putString("password", config.password)
            .putString("m3u_url", config.m3uUrl)
            .putBoolean("logged_in", true)
            .apply()
    }

    fun logout() {
        sp.edit().putBoolean("logged_in", false).apply()
    }

    /** "ts" (MPEG-TS) ou "m3u8" (HLS) pour les chaînes en direct Xtream. */
    var liveFormat: String
        get() = sp.getString("live_format", "ts") ?: "ts"
        set(value) = sp.edit().putString("live_format", value).apply()

    fun favorites(): Set<String> = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun toggleFavorite(key: String): Set<String> {
        val current = favorites().toMutableSet()
        if (!current.remove(key)) current.add(key)
        sp.edit().putStringSet("favorites", current).apply()
        return current
    }
}
