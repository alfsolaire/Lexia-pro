package com.lexia.player.data

object Session {
    var provider: IptvProvider? = null

    fun build(config: LoginConfig, prefs: Prefs): IptvProvider {
        if (config.mode == LoginConfig.MODE_M3U) {
            // Un lien « get.php?username=…&password=… » est un abonnement Xtream : l'API est bien
            // plus rapide que le téléchargement d'une énorme playlist M3U.
            val xtream = xtreamFromM3u(config.m3uUrl)
            return if (xtream != null) {
                XtreamProvider(xtream.server, xtream.username, xtream.password, prefs.liveFormat)
            } else {
                M3uProvider(config.m3uUrl)
            }
        }
        return XtreamProvider(config.server, config.username, config.password, prefs.liveFormat)
    }

    fun xtreamFromM3u(url: String): LoginConfig? {
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return null
        if (uri.path?.endsWith("/get.php") != true) return null
        val params = uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
            it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val user = params["username"]?.takeIf { it.isNotBlank() } ?: return null
        val pass = params["password"]?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val server = "${uri.scheme ?: "http"}://${uri.host}$port"
        return LoginConfig(LoginConfig.MODE_XTREAM, server, user, pass, "")
    }

    /** Recrée le fournisseur à partir des identifiants enregistrés. */
    fun restore(prefs: Prefs): Boolean {
        if (provider != null) return true
        if (!prefs.loggedIn) return false
        val config = prefs.load() ?: return false
        provider = build(config, prefs)
        return true
    }
}
