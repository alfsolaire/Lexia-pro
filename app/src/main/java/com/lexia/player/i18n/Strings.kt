package com.lexia.player.i18n

import java.util.Locale

/**
 * Textes de l'application. La langue suit celle de la Fire TV (français par défaut).
 * Chaque langue implémente toute l'interface : une traduction manquante empêche la compilation.
 */
interface Strings {
    val htmlLang: String

    // Général
    val appSubtitle: String
    val loading: String
    val errorTitle: String
    val retry: String
    val errUnknownHost: String
    val errTimeout: String
    val errBadResponse: String
    val errInvalidAddress: String
    fun errHttp(code: Int): String
    val errEmptyResponse: String
    val errInvalidServer: String
    val errBadCredentials: String
    val errEmptyPlaylist: String
    val m3uPlaylist: String
    fun m3uEntries(count: Int): String
    val noCategory: String
    val untitled: String
    val noName: String
    val episodeDefault: String

    // Connexion
    val fillAllFields: String
    val qrDescription: String
    val quickLogin: String
    val qrInstructions: String
    val qrUnavailable: String
    val playerOnly: String
    val m3uLink: String
    val fieldAccountName: String
    val fieldServer: String
    val fieldUsername: String
    val fieldPassword: String
    val fieldM3uUrl: String
    val connecting: String
    val connect: String

    // Accueil
    fun accountLabel(name: String): String
    fun trialDaysLeft(days: Int): String
    fun yearlyButton(price: String): String
    fun lifetimeButton(price: String): String
    val liveTv: String
    val movies: String
    val series: String
    fun liveFormat(label: String): String
    val refreshLists: String
    val accounts: String
    fun expiresOn(date: String): String
    fun connections(active: String, max: String): String

    // Navigation
    fun loadingTitle(title: String): String
    val recentChannels: String
    val recentlyWatched: String
    val all: String
    val favorites: String
    fun itemsHint(count: Int): String
    val noFavorites: String
    val nothingRecent: String
    val noContent: String
    val search: String
    val sortAlpha: String
    val sortServer: String
    val pinHint: String
    val previewHint: String
    val guideXtreamOnly: String
    val loadingGuide: String
    val noGuide: String
    val now: String
    val next: String
    val preview: String

    // Séries
    val loadingSeries: String
    fun season(number: Int, count: Int): String
    val noEpisodes: String
    fun episode(number: Int, title: String): String

    // Comptes
    val accountsIntro: String
    val currentAccount: String
    val confirmDelete: String
    val delete: String
    val addAccount: String

    // Offres
    fun trialEnded(days: Int): String
    val chooseOffer: String
    val yearly: String
    fun perYear(price: String): String
    val yearlyDetail: String
    val lifetimeBest: String
    val lifetimeDetail: String
    val restorePurchases: String
    val checkingPurchases: String
    val purchaseRestored: String
    val noPurchaseFound: String
    val iapUnavailable: String
    val purchaseOpenFailed: String
    val purchaseCheckFailed: String
    val thanksUnlocked: String
    val alreadyPurchased: String
    val purchasePending: String
    val purchaseFailed: String

    // Lecteur
    val reconnecting: String
    fun playbackError(code: String): String
    val timeshiftEnded: String
    fun nowOverlay(title: String, range: String): String
    fun nextOverlay(title: String, range: String): String
    fun resumeAt(time: String): String
    val backToLive: String
    val paused: String
    fun timeshiftDelay(time: String): String
    val resumed: String

    // Page web du téléphone
    val phoneIntro: String
    val phoneNamePlaceholder: String
    val phoneSend: String
    val phoneSent: String
    val phoneSentDetail: String
    val phoneExpired: String
    val phoneRescan: String
    val phoneMissing: String
    val phoneMissingDetail: String
    val phoneInvalid: String
}

/** Langue courante (celle de l'appareil). */
val S: Strings
    get() = when (Locale.getDefault().language) {
        "en" -> En
        "es" -> Es
        "it" -> It
        "de" -> De
        "pt" -> Pt
        "ar" -> Ar
        else -> Fr
    }
