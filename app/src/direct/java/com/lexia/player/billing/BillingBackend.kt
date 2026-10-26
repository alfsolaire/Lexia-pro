package com.lexia.player.billing

import android.content.Context

/** Version installée directement : aucun achat requis. */
object BillingBackend {
    const val REQUIRES_PURCHASE = false

    fun init(context: Context, onUnlocked: (Boolean) -> Unit, onPrice: (String) -> Unit, onMessage: (String) -> Unit) = Unit

    fun purchase() = Unit

    fun restore() = Unit
}
