package com.lexia.player.billing

import android.content.Context

/** Version installée directement : aucun achat requis. */
object BillingBackend {
    const val REQUIRES_PURCHASE = false
    const val SKU_LIFETIME = "lifetime"
    const val SKU_YEARLY = "yearly"

    fun init(
        context: Context,
        onEntitlement: (lifetime: Boolean, yearly: Boolean) -> Unit,
        onPrice: (sku: String, price: String) -> Unit,
        onMessage: (String) -> Unit,
    ) = Unit

    fun purchase(sku: String) = Unit

    fun restore() = Unit
}
