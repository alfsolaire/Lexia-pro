package com.lexia.player.billing

import android.content.Context
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.UserDataResponse

/**
 * Achat intégré Amazon (IAP) : un « entitlement » qui débloque l'application à vie.
 * Le SKU doit être créé à l'identique dans la console développeur Amazon.
 */
object BillingBackend : PurchasingListener {
    const val REQUIRES_PURCHASE = true
    const val SKU_UNLOCK = "com.alftv.pro.unlock"

    private var onUnlocked: (Boolean) -> Unit = {}
    private var onPrice: (String) -> Unit = {}
    private var onMessage: (String) -> Unit = {}
    private var foundInUpdates = false

    fun init(context: Context, onUnlocked: (Boolean) -> Unit, onPrice: (String) -> Unit, onMessage: (String) -> Unit) {
        this.onUnlocked = onUnlocked
        this.onPrice = onPrice
        this.onMessage = onMessage
        try {
            PurchasingService.registerListener(context, this)
            PurchasingService.getProductData(setOf(SKU_UNLOCK))
            restore()
        } catch (e: Exception) {
            onMessage("Les achats Amazon sont indisponibles sur cet appareil.")
        }
    }

    fun purchase() {
        try {
            PurchasingService.purchase(SKU_UNLOCK)
        } catch (e: Exception) {
            onMessage("Impossible d'ouvrir l'achat Amazon.")
        }
    }

    fun restore() {
        foundInUpdates = false
        try {
            PurchasingService.getPurchaseUpdates(true)
        } catch (e: Exception) {
            onMessage("Impossible de vérifier vos achats.")
        }
    }

    override fun onUserDataResponse(response: UserDataResponse) = Unit

    override fun onProductDataResponse(response: ProductDataResponse) {
        if (response.requestStatus != ProductDataResponse.RequestStatus.SUCCESSFUL) return
        response.productData[SKU_UNLOCK]?.price?.let { onPrice(it) }
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                response.receipt?.let { PurchasingService.notifyFulfillment(it.receiptId, FulfillmentResult.FULFILLED) }
                onUnlocked(true)
                onMessage("Merci ! L'application est débloquée.")
            }
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> {
                onUnlocked(true)
                onMessage("Achat déjà effectué : l'application est débloquée.")
            }
            PurchaseResponse.RequestStatus.PENDING -> onMessage("Achat en attente de validation par Amazon.")
            else -> onMessage("L'achat n'a pas abouti.")
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) return
        response.receipts
            .filter { it.sku == SKU_UNLOCK && !it.isCanceled }
            .forEach {
                foundInUpdates = true
                PurchasingService.notifyFulfillment(it.receiptId, FulfillmentResult.FULFILLED)
            }
        if (response.hasMore()) {
            PurchasingService.getPurchaseUpdates(false)
        } else {
            onUnlocked(foundInUpdates)
        }
    }
}
