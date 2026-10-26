package com.lexia.player.billing

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Essai gratuit puis déblocage par achat (version Appstore uniquement). */
object Licensing {
    const val TRIAL_DAYS = 7
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val CHECKING = "Vérification de vos achats…"

    var unlocked by mutableStateOf(false)
        private set
    var price by mutableStateOf("4,99 €")
        private set
    var message by mutableStateOf<String?>(null)

    private var trialStart = 0L
    private var initialized = false

    val requiresPurchase: Boolean get() = BillingBackend.REQUIRES_PURCHASE

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val sp = context.getSharedPreferences("license", Context.MODE_PRIVATE)
        unlocked = sp.getBoolean("unlocked", false)
        // La date d'installation résiste à l'effacement des données de l'application.
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(System.currentTimeMillis())
        val stored = sp.getLong("trial_start", 0L)
        trialStart = if (stored > 0) minOf(stored, installed) else installed
        sp.edit().putLong("trial_start", trialStart).apply()

        BillingBackend.init(
            context.applicationContext,
            onUnlocked = { value ->
                unlocked = value
                sp.edit().putBoolean("unlocked", value).apply()
                if (message == CHECKING) message = if (value) "Achat retrouvé : l'application est débloquée." else "Aucun achat trouvé pour ce compte Amazon."
            },
            onPrice = { price = it },
            onMessage = { message = it },
        )
    }

    fun daysLeft(): Int {
        val elapsed = System.currentTimeMillis() - trialStart
        val left = TRIAL_DAYS - (elapsed / DAY_MS).toInt()
        return left.coerceIn(0, TRIAL_DAYS)
    }

    /** true quand l'essai est terminé et que l'application n'a pas été achetée. */
    val locked: Boolean get() = requiresPurchase && !unlocked && daysLeft() == 0

    fun purchase() {
        message = null
        BillingBackend.purchase()
    }

    fun restore() {
        message = CHECKING
        BillingBackend.restore()
    }
}
