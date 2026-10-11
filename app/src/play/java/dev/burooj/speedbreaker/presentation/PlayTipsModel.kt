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
internal class PlayTipsModel(
    application: Application,
    billingFactory: ((Int, List<Purchase>) -> Unit, () -> Unit) -> TipBilling,
    publicKey: String,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        { purchases, disconnected -> PlayTipBilling(application, purchases, disconnected) },
        application.getString(R.string.play_billing_public_key),
    )

    private val verification = TipVerification(publicKey)
    private val ledger = TipLedger()
    private val mutableState = MutableStateFlow(TipsUiState())
    val state = mutableState.asStateFlow()
    private var refreshing = false
    private var recovered = false
    private var retryJob: Job? = null
    // A return remains due until an authoritative query succeeds for this checkout.
    private var returnRecoveryGeneration: Long? = null
    // Purchase updates/disconnects supersede queries that started before those events.
    private var billingRevision = 0L
    private val billing = billingFactory(
        { code, purchases ->
            viewModelScope.launch {
                // A replayed old success callback must not release a newer checkout's lock.
                if (code == BillingResponseCode.OK && purchases.isNotEmpty() &&
                    purchases.all { ledger.alreadyHandled(it.purchaseToken) }
                ) return@launch
                billingRevision++
                ledger.invalidatePreparation()
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
        {
            viewModelScope.launch {
                billingRevision++
                ledger.invalidatePreparation()
                recovered = false
                publish("Google Play disconnected. Tips are temporarily unavailable.", clearOffers = true)
                retryLater()
            }
        },
    )

    fun resume() {
        // Record before refresh's single-flight guard: a foreground event can arrive mid-query.
        viewModelScope.launch {
            if (ledger.awaitingPlayReturn) returnRecoveryGeneration = ledger.checkoutGeneration
            refresh()
        }
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

    private suspend fun refresh() {
        if (!verification.configured || refreshing) return
        val recoverCheckout = returnRecoveryGeneration
        val queryGeneration = ledger.checkoutGeneration
        val queryRevision = billingRevision
        ledger.invalidatePreparation()
        refreshing = true
        recovered = false
        publish("Checking availability with Google Play…", clearOffers = true)
        try {
            val connection = billing.connect()
            if (queryRevision != billingRevision || queryGeneration != ledger.checkoutGeneration) {
                retryLater()
                return
            }
            if (connection != BillingResponseCode.OK) {
                publish("Google Play is unavailable. You can retry when it is connected.")
                retryLater()
                return
            }
            val owned = billing.purchases()
            if (queryRevision != billingRevision || queryGeneration != ledger.checkoutGeneration) {
                retryLater()
                return
            }
            if (owned.code != BillingResponseCode.OK) {
                publish("Could not check earlier tips. Retry before starting another checkout.")
                retryLater()
                return
            }
            if (recoverCheckout != null) {
                ledger.billingReturned(recoverCheckout)
                if (returnRecoveryGeneration == recoverCheckout) returnRecoveryGeneration = null
            }
            ledger.reconcile(owned.value.filter { it.products.any(tipProductIds::contains) }
                .map { it.purchaseToken }.toSet())
            recovered = true
            process(owned.value)
            val offers = billing.offers()
            if (queryRevision != billingRevision || queryGeneration != ledger.checkoutGeneration) return
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
            if (queryRevision != billingRevision || queryGeneration != ledger.checkoutGeneration) return
            recovered = false
            publish("Google Play did not finish the check. Please retry.", clearOffers = true)
            retryLater()
        } finally {
            refreshing = false
            publish(mutableState.value.message)
            if (returnRecoveryGeneration != null && ledger.awaitingPlayReturn) retryLater()
        }
    }

    fun buy(activity: Activity, selected: PlayTipOffer) {
        if (!mutableState.value.canBuy || !ledger.beginCheckout()) return
        val generation = ledger.checkoutGeneration
        val preparationRevision = billingRevision
        publish("Checking the current price…")
        viewModelScope.launch {
            try {
                // Never launch with cached ProductDetails: price/eligibility can change while chooser is open.
                val fresh = billing.offers()
                // An intervening callback may have canceled this preparation and published
                // pending/error state. Neither a late result nor its exception may replace it.
                if (!recovered || !ledger.preparing(generation)) return@launch
                val current = fresh.value.firstOrNull { it.details.productId == selected.details.productId }
                if (fresh.code != BillingResponseCode.OK || current == null) {
                    ledger.checkoutReturned(generation)
                    publish("This tip is unavailable. Retry for current prices.", clearOffers = true)
                    recovered = false
                    publish(mutableState.value.message)
                } else if (current.price != selected.price || current.token != selected.token) {
                    ledger.checkoutReturned(generation)
                    mutableState.value = mutableState.value.copy(offers = fresh.value)
                    publish("The available price changed. Please review it and choose again.")
                } else {
                    if (!ledger.launched(generation)) return@launch
                    val launchRevision = billingRevision
                    val code = billing.launch(activity, current)
                    if (launchRevision != billingRevision || !ledger.ownsCheckout(generation)) return@launch
                    if (code != BillingResponseCode.OK) {
                        ledger.checkoutReturned(generation)
                        recovered = false
                        publish("Checkout could not open. Retry to check Google Play.", clearOffers = true)
                        retryLater()
                    } else {
                        publish("Complete or cancel the tip in Google Play.")
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (preparationRevision != billingRevision || !ledger.ownsCheckout(generation)) return@launch
                ledger.checkoutReturned(generation)
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
