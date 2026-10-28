package com.lexia.player.billing

import com.lexia.player.i18n.S
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Essai gratuit puis abonnement annuel ou achat à vie (version Appstore uniquement). */
object Licensing {
    const val TRIAL_DAYS = 7
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    var lifetime by mutableStateOf(false)
        private set
    var yearly by mutableStateOf(false)
        private set
    var priceYearly by mutableStateOf("4,99 €")
        private set
    var priceLifetime by mutableStateOf("19,99 €")
        private set
    var message by mutableStateOf<String?>(null)

    val unlocked: Boolean get() = lifetime || yearly

    private var trialStart = 0L
    private var initialized = false

    val requiresPurchase: Boolean get() = BillingBackend.REQUIRES_PURCHASE

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val sp = context.getSharedPreferences("license", Context.MODE_PRIVATE)
        // Ancienne clé « unlocked » (achat unique de la version précédente) = à vie.
        lifetime = sp.getBoolean("lifetime", sp.getBoolean("unlocked", false))
        yearly = sp.getBoolean("yearly", false)
        // La date d'installation résiste à l'effacement des données de l'application.
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(System.currentTimeMillis())
        val stored = sp.getLong("trial_start", 0L)
        trialStart = if (stored > 0) minOf(stored, installed) else installed
        sp.edit().putLong("trial_start", trialStart).apply()

        BillingBackend.init(
            context.applicationContext,
            onEntitlement = { hasLifetime, hasYearly ->
                lifetime = hasLifetime
                yearly = hasYearly
                sp.edit().putBoolean("lifetime", hasLifetime).putBoolean("yearly", hasYearly).apply()
                if (message == S.checkingPurchases) {
                    message = if (hasLifetime || hasYearly) S.purchaseRestored
                    else S.noPurchaseFound
                }
            },
            onPrice = { sku, price ->
                when (sku) {
                    BillingBackend.SKU_YEARLY -> priceYearly = price
                    BillingBackend.SKU_LIFETIME -> priceLifetime = price
                }
            },
            onMessage = { message = it },
        )
    }

    fun daysLeft(): Int {
        val elapsed = System.currentTimeMillis() - trialStart
        val left = TRIAL_DAYS - (elapsed / DAY_MS).toInt()
        return left.coerceIn(0, TRIAL_DAYS)
    }

    /** true quand l'essai est terminé et qu'aucune offre n'est active. */
    val locked: Boolean get() = requiresPurchase && !unlocked && daysLeft() == 0

    fun buyYearly() {
        message = null
        BillingBackend.purchase(BillingBackend.SKU_YEARLY)
    }

    fun buyLifetime() {
        message = null
        BillingBackend.purchase(BillingBackend.SKU_LIFETIME)
    }

    fun restore() {
        message = S.checkingPurchases
        BillingBackend.restore()
    }
}
