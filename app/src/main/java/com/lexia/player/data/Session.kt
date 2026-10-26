package com.lexia.player.data

object Session {
    var provider: IptvProvider? = null

    fun build(config: LoginConfig, prefs: Prefs): IptvProvider = when (config.mode) {
        LoginConfig.MODE_M3U -> M3uProvider(config.m3uUrl)
        else -> XtreamProvider(config.server, config.username, config.password, prefs.liveFormat)
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
