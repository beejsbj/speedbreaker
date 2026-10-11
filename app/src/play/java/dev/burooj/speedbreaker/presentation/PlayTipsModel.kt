package dev.burooj.speedbreaker.presentation

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import dev.burooj.speedbreaker.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class TipsUiState(
    val offers: List<PlayTipOffer> = emptyList(),
    val busy: Boolean = false,
    val canBuy: Boolean = false,
    val message: String = "Tips are not available yet. All features remain free.",
)

/** Activity-scoped, survives rotation and Play's payment Activity; closed on owner destruction. */
internal class PlayTipsModel(application: Application) : AndroidViewModel(application) {
    private val verification = TipVerification(application.getString(R.string.play_billing_public_key))
    private val ledger = TipLedger()
    private val mutableState = MutableStateFlow(TipsUiState())
    val state = mutableState.asStateFlow()
    private var refreshing = false
    private var recovered = false
    private var retryJob: Job? = null
    private val billing = PlayTipBilling(application,
        onPurchases = { code, purchases ->
            viewModelScope.launch {
                // A replayed old success callback must not release a newer checkout's lock.
                if (code == BillingResponseCode.OK && purchases.isNotEmpty() &&
                    purchases.all { ledger.alreadyHandled(it.purchaseToken) }
                ) return@launch
                ledger.billingReturned()
                when (code) {
                    BillingResponseCode.OK -> process(purchases)
                    BillingResponseCode.USER_CANCELED -> publish("Tip canceled. All features remain free.")
                    BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                    else -> {
                        recovered = false
                        publish("Checkout could not finish. Retry to check with Google Play.")
                        retryLater()
                    }
                }
            }
        },
        onDisconnected = {
            viewModelScope.launch {
                recovered = false
                publish("Google Play disconnected. Tips are temporarily unavailable.", clearOffers = true)
                retryLater()
            }
        },
    )

    fun resume() {
        viewModelScope.launch { refresh(returnedFromCheckout = true) }
    }

    fun retry() { viewModelScope.launch { refresh() } }

    private fun publish(message: String, clearOffers: Boolean = false) {
        mutableState.value = mutableState.value.copy(
            message = message,
            offers = if (clearOffers) emptyList() else mutableState.value.offers,
            busy = refreshing || ledger.checkoutOpen,
            canBuy = verification.configured && recovered && !refreshing && !ledger.blocked,
        )
    }

    private suspend fun refresh(returnedFromCheckout: Boolean = false) {
        if (!verification.configured || refreshing) return
        val recoverCheckout = returnedFromCheckout && ledger.awaitingPlayReturn
        refreshing = true
        recovered = false
        publish("Checking availability with Google Play…", clearOffers = true)
        try {
            if (billing.connect() != BillingResponseCode.OK) {
                publish("Google Play is unavailable. You can retry when it is connected.")
                retryLater()
                return
            }
            val owned = billing.purchases()
            if (owned.code != BillingResponseCode.OK) {
                publish("Could not check earlier tips. Retry before starting another checkout.")
                retryLater()
                return
            }
            if (recoverCheckout) ledger.billingReturned()
            ledger.reconcile(owned.value.filter { it.products.any(tipProductIds::contains) }
                .map { it.purchaseToken }.toSet())
            recovered = true
            process(owned.value)
            val offers = billing.offers()
            mutableState.value = mutableState.value.copy(
                offers = if (offers.code == BillingResponseCode.OK) offers.value else emptyList(),
            )
            if (!ledger.blocked && mutableState.value.message != THANKS) {
                publish(if (offers.code == BillingResponseCode.OK && offers.value.isNotEmpty())
                    "Choose a one-time tip. Nothing is unlocked; all features remain free."
                else "Tips are currently unavailable from Google Play. Please try again later.")
            }
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            recovered = false
            publish("Google Play did not finish the check. Please retry.", clearOffers = true)
            retryLater()
        } finally {
            refreshing = false
            publish(mutableState.value.message)
        }
    }

    fun buy(activity: Activity, selected: PlayTipOffer) {
        if (!mutableState.value.canBuy || !ledger.beginCheckout()) return
        publish("Checking the current price…")
        viewModelScope.launch {
            try {
                // Never launch with cached ProductDetails: price/eligibility can change while chooser is open.
                val fresh = billing.offers()
                val current = fresh.value.firstOrNull { it.details.productId == selected.details.productId }
                if (fresh.code != BillingResponseCode.OK || current == null) {
                    ledger.checkoutReturned()
                    publish("This tip is unavailable. Retry for current prices.", clearOffers = true)
                    recovered = false
                    publish(mutableState.value.message)
                } else if (current.price != selected.price || current.token != selected.token) {
                    ledger.checkoutReturned()
                    mutableState.value = mutableState.value.copy(offers = fresh.value)
                    publish("The available price changed. Please review it and choose again.")
                } else {
                    ledger.launched()
                    val code = billing.launch(activity, current)
                    if (code != BillingResponseCode.OK) {
                        ledger.checkoutReturned()
                        recovered = false
                        publish("Checkout could not open. Retry to check Google Play.", clearOffers = true)
                        retryLater()
                    } else {
                        publish("Complete or cancel the tip in Google Play.")
                    }
                }
            } catch (error: Exception) {
                ledger.checkoutReturned()
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                recovered = false
                publish("Checkout is unavailable. Please retry.", clearOffers = true)
                retryLater()
            }
        }
    }

    private suspend fun process(purchases: List<Purchase>) {
        for (purchase in purchases) {
            val token = purchase.purchaseToken
            val receipt = TipReceipt(
                state = when (purchase.purchaseState) {
                    Purchase.PurchaseState.PENDING -> TipReceiptState.Pending
                    Purchase.PurchaseState.PURCHASED -> TipReceiptState.Purchased
                    else -> TipReceiptState.Other
                },
                products = purchase.products,
                packageName = purchase.packageName,
                token = token,
                quantity = purchase.quantity,
                suspended = purchase.isSuspended,
                json = purchase.originalJson,
                signature = purchase.signature,
            )
            when (decideTipReceipt(receipt, getApplication<Application>().packageName, verification::verify)) {
                TipDecision.Ignore -> Unit
                TipDecision.Pending -> {
                    ledger.pending(token)
                    publish("Tip pending in Google Play. It has not completed. Check again later.")
                }
                TipDecision.Reject -> {
                    ledger.pending(token)
                    publish("This tip could not be verified. It has not been handled. Please retry.")
                }
                TipDecision.Consume -> {
                    if (!ledger.beginConsume(token)) continue
                    publish("Finishing your tip with Google Play…")
                    val ok = try {
                        billing.consume(token) == BillingResponseCode.OK
                    } catch (error: Exception) {
                        if (error is CancellationException && error !is TimeoutCancellationException) {
                            ledger.consumed(token, false)
                            throw error
                        }
                        false
                    }
                    // Consumption acknowledges the purchase. ITEM_NOT_OWNED is not a success receipt.
                    if (ledger.consumed(token, ok)) publish(THANKS)
                    else if (!ok) {
                        publish("Your tip has not finished processing. Retry; please do not pay again.")
                        retryLater()
                    }
                }
            }
        }
        if (ledger.blocked && mutableState.value.message == THANKS) {
            publish("A tip still needs processing. Please retry before sending another.")
        }
    }

    private fun retryLater() {
        if (!verification.configured || retryJob?.isActive == true) return
        retryJob = viewModelScope.launch {
            for (waitMs in listOf(2_000L, 4_000L, 8_000L)) {
                delay(waitMs)
                refresh()
                if (recovered && !ledger.blocked) break
            }
        }
    }

    override fun onCleared() {
        billing.close()
        super.onCleared()
    }

    private companion object {
        const val THANKS = "Thank you for supporting Speedbreaker. Your tip is complete."
    }
}
