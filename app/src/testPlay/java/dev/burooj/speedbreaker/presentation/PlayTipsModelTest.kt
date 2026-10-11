package dev.burooj.speedbreaker.presentation

import android.app.Activity
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Duration
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Real ViewModel + real Play receipt parsing/verification, with controlled Billing boundaries. */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34])
class PlayTipsModelTest {
    private val store = ViewModelStore()
    private lateinit var model: PlayTipsModel
    private lateinit var billing: ControlledBilling
    private val activity = Activity()
    private val offer = fixtureOffer()

    @Before fun setUp() {
        model = PlayTipsModel(RuntimeEnvironment.getApplication(), { purchases, disconnected ->
            ControlledBilling(purchases, disconnected, offer).also { billing = it }
        }, Base64.getEncoder().encodeToString(keyPair.public.encoded))
        store.put("tips", model)
        model.resume()
        drain()
        assertTrue(model.state.value.canBuy)
    }

    @After fun tearDown() {
        store.clear()
        drain()
        assertTrue(billing.closed)
    }

    @Test fun pendingDifferentProductDuringPriceQueryPreventsLaunchAndPreservesWarning() {
        val price = billing.holdOffers()
        buy()
        billing.deliver(receipt(pending = true))
        drain()
        val warning = model.state.value.message
        assertTrue(warning.contains("Tip pending"))
        price.complete(BillingReply(BillingResponseCode.OK, listOf(offer)))
        drain()
        assertEquals(0, billing.launches)
        assertEquals(warning, model.state.value.message)
        assertFalse(model.state.value.canBuy)
        assertTrue(billing.consumedTokens.isEmpty())
    }

    @Test fun latePriceExceptionDoesNotReplacePendingWarning() {
        val price = billing.holdOffers()
        buy()
        billing.deliver(receipt(pending = true))
        drain()
        val warning = model.state.value.message
        price.completeExceptionally(IllegalStateException("controlled price failure"))
        drain()
        assertEquals(0, billing.launches)
        assertEquals(warning, model.state.value.message)
    }

    @Test fun disconnectDuringPreparationPreventsLaunchAndPreservesError() {
        val price = billing.holdOffers()
        buy()
        billing.disconnect()
        drain()
        val error = model.state.value.message
        assertTrue(error.contains("disconnected"))
        price.complete(BillingReply(BillingResponseCode.OK, listOf(offer)))
        drain()
        assertEquals(0, billing.launches)
        assertEquals(error, model.state.value.message)
        assertFalse(model.state.value.canBuy)
        assertFalse(model.state.value.busy)
    }

    @Test fun consumptionDuringPreparationBlocksLaunchAndDeduplicatesCallbacks() {
        val price = billing.holdOffers()
        val consumption = billing.holdConsume()
        buy()
        val purchase = receipt()
        billing.deliver(purchase)
        billing.deliver(purchase)
        drain()
        assertEquals(listOf("fixture-token"), billing.consumedTokens)
        assertTrue(model.state.value.message.contains("Finishing"))
        price.complete(BillingReply(BillingResponseCode.OK, listOf(offer)))
        drain()
        assertEquals(0, billing.launches)
        assertFalse(model.state.value.canBuy)
        consumption.complete(BillingResponseCode.OK)
        drain()
        assertTrue(model.state.value.canBuy)
        assertTrue(model.state.value.message.contains("Thank you"))
    }

    @Test fun consumeFailureDuringPreparationPreservesDoNotPayAgainError() {
        val price = billing.holdOffers()
        val consumption = billing.holdConsume()
        buy()
        billing.deliver(receipt())
        drain()
        consumption.complete(BillingResponseCode.ERROR)
        drain()
        val error = model.state.value.message
        assertTrue(error.contains("please do not pay again"))
        price.complete(BillingReply(BillingResponseCode.OK, listOf(offer)))
        drain()
        assertEquals(0, billing.launches)
        assertEquals(error, model.state.value.message)
        assertFalse(model.state.value.canBuy)
    }

    @Test fun successfulConsumptionCannotReviveAnInvalidatedPreparation() {
        val price = billing.holdOffers()
        buy()
        billing.deliver(receipt())
        drain()
        assertTrue(model.state.value.canBuy)
        // A recovered ledger alone is insufficient: the old preparation was invalidated.
        price.complete(BillingReply(BillingResponseCode.OK, listOf(offer)))
        drain()
        assertEquals(0, billing.launches)
        assertTrue(model.state.value.message.contains("Thank you"))
        buy()
        assertEquals(1, billing.launches)
    }

    @Test fun canceledPreparationCannotReleaseOrLaunchOverNewCheckout() {
        val oldPrice = billing.holdOffers()
        buy()
        billing.deliverCode(BillingResponseCode.USER_CANCELED)
        drain()
        assertTrue(model.state.value.canBuy)
        buy()
        assertEquals(1, billing.launches)
        val checkout = model.state.value
        oldPrice.complete(BillingReply(BillingResponseCode.OK, listOf(offer)))
        drain()
        assertEquals(1, billing.launches)
        assertEquals(checkout, model.state.value)
        assertFalse(model.state.value.canBuy)
    }

    @Test fun failedFirstReturnQueryThenManualRetryEnablesCheckout() {
        buy()
        billing.purchaseCode = BillingResponseCode.ERROR
        model.resume()
        drain()
        assertFalse(model.state.value.canBuy)
        billing.purchaseCode = BillingResponseCode.OK
        model.retry()
        drain()
        assertTrue(model.state.value.canBuy)
        assertFalse(model.state.value.busy)
        buy()
        assertEquals(2, billing.launches)
    }

    @Test fun failedFirstReturnConnectionThenAutomaticRetryEnablesCheckout() {
        buy()
        billing.connectionCode = BillingResponseCode.SERVICE_UNAVAILABLE
        model.resume()
        drain()
        assertFalse(model.state.value.canBuy)
        billing.connectionCode = BillingResponseCode.OK
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(model.state.value.canBuy)
        assertFalse(model.state.value.busy)
        buy()
        assertEquals(2, billing.launches)
    }

    @Test fun thrownFirstReturnQueryThenRetryEnablesCheckout() {
        buy()
        val query = billing.holdPurchases()
        model.resume()
        drain()
        query.completeExceptionally(IllegalStateException("controlled query failure"))
        drain()
        assertFalse(model.state.value.canBuy)
        model.retry()
        drain()
        assertTrue(model.state.value.canBuy)
    }

    @Test fun retryWithoutReturnDoesNotUnlockAnOpenCheckout() {
        buy()
        model.retry()
        drain()
        assertFalse(model.state.value.canBuy)
        buy()
        assertEquals(1, billing.launches)
        model.resume()
        drain()
        assertTrue(model.state.value.canBuy)
    }

    @Test fun staleReturnGenerationQueryCannotUnlockNewerCheckout() {
        buy()
        // A plain query is already running when resume records the first checkout's return.
        val firstQuery = billing.holdPurchases()
        model.retry()
        drain()
        model.resume()
        drain()
        firstQuery.complete(BillingReply(BillingResponseCode.OK, emptyList()))
        drain()
        assertFalse(model.state.value.canBuy)
        billing.deliverCode(BillingResponseCode.USER_CANCELED)
        drain()
        assertTrue(model.state.value.canBuy)
        buy()
        assertEquals(2, billing.launches)
        // The scheduled recovery still carries generation 1, while checkout 2 is open.
        val staleRecoveryQuery = billing.holdPurchases()
        val queriesBeforeRetry = billing.purchaseQueries
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals("The stale generation must actually reach an ownership query",
            queriesBeforeRetry + 1, billing.purchaseQueries)
        staleRecoveryQuery.complete(BillingReply(BillingResponseCode.OK, emptyList()))
        drain()
        assertFalse(model.state.value.canBuy)
        assertTrue(model.state.value.busy)
        buy()
        assertEquals(2, billing.launches)
        model.resume()
        drain()
        assertTrue(model.state.value.canBuy)
    }

    @Test fun staleEmptyOwnershipQueryCannotEraseNewPendingCallback() {
        buy()
        val query = billing.holdPurchases()
        model.resume()
        drain()
        billing.deliver(receipt(pending = true))
        drain()
        val pending = model.state.value.message
        query.complete(BillingReply(BillingResponseCode.OK, emptyList()))
        drain()
        assertEquals(pending, model.state.value.message)
        assertFalse(model.state.value.canBuy)
        buy()
        assertEquals(1, billing.launches)
    }

    @Test fun duplicateHandledReceiptDoesNotReleaseNewCheckoutDuringReturnQuery() {
        val completed = receipt()
        billing.deliver(completed)
        drain()
        buy()
        val query = billing.holdPurchases()
        model.resume()
        drain()
        billing.deliver(completed)
        drain()
        assertEquals(listOf("fixture-token"), billing.consumedTokens)
        assertFalse(model.state.value.canBuy)
        query.complete(BillingReply(BillingResponseCode.OK, emptyList()))
        drain()
        assertTrue(model.state.value.canBuy)
        buy()
        assertEquals(2, billing.launches)
        billing.deliver(completed)
        drain()
        assertFalse(model.state.value.canBuy)
        assertEquals(2, billing.launches)
    }

    @Test fun rapidTapsAndResumeDuringPreparationNeverLaunchTheStalePrice() {
        val price = billing.holdOffers()
        buy()
        buy()
        model.resume()
        drain()
        assertTrue(model.state.value.canBuy)
        buy()
        assertEquals(1, billing.launches)
        price.complete(BillingReply(BillingResponseCode.OK, listOf(offer)))
        drain()
        assertEquals(1, billing.launches)
        assertFalse(model.state.value.canBuy)
    }

    private fun buy() {
        model.buy(activity, offer)
        drain()
    }

    private fun drain() { shadowOf(Looper.getMainLooper()).idle() }

    private fun receipt(pending: Boolean = false): Purchase {
        // Existing medium product differs from the small offer being prepared. Test-only key.
        val json = JSONObject().put("productId", tipProductIds[1])
            .put("packageName", RuntimeEnvironment.getApplication().packageName)
            .put("purchaseToken", "fixture-token").put("quantity", 1)
            .put("purchaseState", if (pending) 4 else 0).toString()
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(keyPair.private)
            update(json.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        return Purchase(json, signature)
    }

    private class ControlledBilling(
        private val onPurchases: (Int, List<Purchase>) -> Unit,
        private val onDisconnected: () -> Unit,
        private val offer: PlayTipOffer,
    ) : TipBilling {
        var launches = 0
        var closed = false
        var connectionCode = BillingResponseCode.OK
        var purchaseCode = BillingResponseCode.OK
        var purchaseQueries = 0
        val consumedTokens = mutableListOf<String>()
        private var nextOffers: CompletableDeferred<BillingReply<List<PlayTipOffer>>>? = null
        private var nextPurchases: CompletableDeferred<BillingReply<List<Purchase>>>? = null
        private var nextConsume: CompletableDeferred<Int>? = null

        fun holdOffers() = CompletableDeferred<BillingReply<List<PlayTipOffer>>>().also { nextOffers = it }
        fun holdPurchases() = CompletableDeferred<BillingReply<List<Purchase>>>().also { nextPurchases = it }
        fun holdConsume() = CompletableDeferred<Int>().also { nextConsume = it }
        fun deliver(purchase: Purchase) = onPurchases(BillingResponseCode.OK, listOf(purchase))
        fun deliverCode(code: Int) = onPurchases(code, emptyList())
        fun disconnect() = onDisconnected()

        override suspend fun connect() = connectionCode
        override suspend fun offers(): BillingReply<List<PlayTipOffer>> {
            val held = nextOffers
            nextOffers = null
            return held?.await() ?: BillingReply(BillingResponseCode.OK, listOf(offer))
        }
        override suspend fun purchases(): BillingReply<List<Purchase>> {
            purchaseQueries++
            val held = nextPurchases
            nextPurchases = null
            return held?.await() ?: BillingReply(purchaseCode, emptyList())
        }
        override suspend fun consume(token: String): Int {
            consumedTokens.add(token)
            val held = nextConsume
            nextConsume = null
            return held?.await() ?: BillingResponseCode.OK
        }
        override fun launch(activity: Activity, offer: PlayTipOffer): Int {
            launches++
            return BillingResponseCode.OK
        }
        override fun close() { closed = true }
    }

    private companion object {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        fun fixtureOffer(): PlayTipOffer {
            // Billing 9.1 exposes no public ProductDetails builder; parse its real JSON constructor.
            val json = JSONObject().put("productId", tipProductIds[0]).put("type", "inapp")
                .put("title", "Fixture tip").put("name", "Fixture tip").put("description", "Test only")
                .toString()
            val details = ProductDetails::class.java.getDeclaredConstructor(String::class.java)
                .apply { isAccessible = true }.newInstance(json)
            return PlayTipOffer(details, null, "fixture price")
        }
    }
}
