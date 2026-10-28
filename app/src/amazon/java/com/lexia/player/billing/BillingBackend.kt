package com.lexia.player.billing

import com.lexia.player.i18n.S
import android.content.Context
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.ProductType
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.Receipt
import com.amazon.device.iap.model.UserDataResponse

/**
 * Achats intégrés Amazon (IAP), à créer à l'identique dans la console développeur :
 * - [SKU_LIFETIME] : « Entitlement », débloque l'application à vie ;
 * - [SKU_YEARLY] : durée (« term SKU ») annuelle de l'abonnement [SKU_SUBSCRIPTION].
 */
object BillingBackend : PurchasingListener {
    const val REQUIRES_PURCHASE = true
    const val SKU_LIFETIME = "com.alftv.pro.unlock"
    const val SKU_SUBSCRIPTION = "com.alftv.pro.subscription"
    const val SKU_YEARLY = "com.alftv.pro.yearly"

    private var onEntitlement: (Boolean, Boolean) -> Unit = { _, _ -> }
    private var onPrice: (String, String) -> Unit = { _, _ -> }
    private var onMessage: (String) -> Unit = {}
    private var foundLifetime = false
    private var foundYearly = false

    fun init(
        context: Context,
        onEntitlement: (lifetime: Boolean, yearly: Boolean) -> Unit,
        onPrice: (sku: String, price: String) -> Unit,
        onMessage: (String) -> Unit,
    ) {
        this.onEntitlement = onEntitlement
        this.onPrice = onPrice
        this.onMessage = onMessage
        try {
            PurchasingService.registerListener(context, this)
            PurchasingService.getProductData(setOf(SKU_LIFETIME, SKU_YEARLY))
            restore()
        } catch (e: Exception) {
            onMessage(S.iapUnavailable)
        }
    }

    fun purchase(sku: String) {
        try {
            PurchasingService.purchase(sku)
        } catch (e: Exception) {
            onMessage(S.purchaseOpenFailed)
        }
    }

    fun restore() {
        foundLifetime = false
        foundYearly = false
        try {
            PurchasingService.getPurchaseUpdates(true)
        } catch (e: Exception) {
            onMessage(S.purchaseCheckFailed)
        }
    }

    private fun isLifetime(r: Receipt) = r.sku == SKU_LIFETIME

    private fun isYearly(r: Receipt) =
        r.productType == ProductType.SUBSCRIPTION ||
            r.sku == SKU_SUBSCRIPTION || r.sku == SKU_YEARLY || r.termSku == SKU_YEARLY

    override fun onUserDataResponse(response: UserDataResponse) = Unit

    override fun onProductDataResponse(response: ProductDataResponse) {
        if (response.requestStatus != ProductDataResponse.RequestStatus.SUCCESSFUL) return
        response.productData.forEach { (sku, product) -> product.price?.let { onPrice(sku, it) } }
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                val receipt = response.receipt
                if (receipt != null) {
                    PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED)
                    if (isLifetime(receipt)) foundLifetime = true
                    if (isYearly(receipt)) foundYearly = true
                    onEntitlement(foundLifetime || isLifetime(receipt), foundYearly || isYearly(receipt))
                }
                onMessage(S.thanksUnlocked)
            }
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> {
                onMessage(S.alreadyPurchased)
                restore()
            }
            PurchaseResponse.RequestStatus.PENDING -> onMessage(S.purchasePending)
            else -> onMessage(S.purchaseFailed)
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) return
        response.receipts.filter { !it.isCanceled }.forEach { receipt ->
            if (isLifetime(receipt)) foundLifetime = true
            if (isYearly(receipt)) foundYearly = true
            if (isLifetime(receipt) || isYearly(receipt)) {
                PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED)
            }
        }
        if (response.hasMore()) {
            PurchasingService.getPurchaseUpdates(false)
        } else {
            onEntitlement(foundLifetime, foundYearly)
        }
    }
}
