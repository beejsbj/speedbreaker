package dev.burooj.speedbreaker.presentation

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

internal data class PlayTipOffer(val details: ProductDetails, val token: String?, val price: String)
internal data class BillingReply<T>(val code: Int, val value: T)

/** Small boundary for deterministic ViewModel callback and suspension tests. */
internal interface TipBilling {
    suspend fun connect(): Int
    suspend fun offers(): BillingReply<List<PlayTipOffer>>
    suspend fun purchases(): BillingReply<List<Purchase>>
    fun launch(activity: Activity, offer: PlayTipOffer): Int
    suspend fun consume(token: String): Int
    fun close()
}

/** Callback-only Billing artifact: no billing-ktx dependency. Never logs billing responses. */
internal class PlayTipBilling(
    context: Context,
    onPurchases: (Int, List<Purchase>) -> Unit,
    onDisconnected: () -> Unit,
) : TipBilling {
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchases(result.responseCode, purchases.orEmpty()) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    private val disconnected = onDisconnected

    override suspend fun connect(): Int {
        if (client.isReady) return BillingClient.BillingResponseCode.OK
        return withTimeout(15_000) {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) continuation.resume(result.responseCode)
                    }
                    override fun onBillingServiceDisconnected() { disconnected() }
                })
            }
        }
    }

    override suspend fun offers(): BillingReply<List<PlayTipOffer>> = withTimeout(15_000) {
        suspendCancellableCoroutine { continuation ->
            val products = tipProductIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder().setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP).build()
            }
            client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(products).build(),
            ) { result, query ->
                // Only ordinary buy offers: no rentals, preorders, promotions, or invented prices.
                val offers = query.productDetailsList.filter { it.productId in tipProductIds }
                    .mapNotNull { details ->
                        val eligible = details.oneTimePurchaseOfferDetailsList
                            ?: listOfNotNull(details.oneTimePurchaseOfferDetails)
                        val offer = eligible.firstOrNull {
                            it.rentalDetails == null && it.preorderDetails == null && it.offerId == null
                        } ?: return@mapNotNull null
                        PlayTipOffer(details, offer.offerToken, offer.formattedPrice)
                    }.sortedBy { tipProductIds.indexOf(it.details.productId) }
                if (continuation.isActive) continuation.resume(BillingReply(result.responseCode, offers))
            }
        }
    }

    override suspend fun purchases(): BillingReply<List<Purchase>> = withTimeout(15_000) {
        suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                if (continuation.isActive) continuation.resume(BillingReply(result.responseCode, purchases))
            }
        }
    }

    override fun launch(activity: Activity, offer: PlayTipOffer): Int = client.launchBillingFlow(
        activity,
        BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(offer.details).apply {
                    // Legacy one-time products can have no offer token; don't invent one.
                    offer.token?.let { setOfferToken(it) }
                }.build(),
        )).build(),
    ).responseCode

    override suspend fun consume(token: String): Int = withTimeout(15_000) {
        suspendCancellableCoroutine { continuation ->
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build()) { result, returnedToken ->
                if (continuation.isActive) continuation.resume(
                    if (returnedToken == token) result.responseCode else BillingClient.BillingResponseCode.ERROR,
                )
            }
        }
    }

    override fun close() { client.endConnection() }
}
