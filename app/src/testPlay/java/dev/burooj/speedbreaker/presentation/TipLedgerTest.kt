package dev.burooj.speedbreaker.presentation

import org.junit.Assert.*
import org.junit.Test

/** Scenarios at the retry/checkout boundary; no Android or Billing service required. */
class TipLedgerTest {
    @Test fun rapidTapsAndDelayedPriceQueryLaunchOnlyOnce() {
        val ledger = TipLedger()
        assertTrue(ledger.beginCheckout())
        assertFalse(ledger.beginCheckout())
        // Foreground/query events before launch must not release the preparing checkout.
        ledger.billingReturned()
        assertFalse(ledger.beginCheckout())
        ledger.launched()
        ledger.billingReturned()
        assertTrue(ledger.beginCheckout())
    }

    @Test fun cancelAllowsAnotherTipWithoutSuccessOrConsumption() {
        val ledger = TipLedger()
        ledger.beginCheckout()
        ledger.launched()
        ledger.billingReturned()
        assertFalse(ledger.blocked)
        assertTrue(ledger.beginCheckout())
    }

    @Test fun pendingBlocksAnotherPaymentUntilPlayReportsCancellation() {
        val ledger = TipLedger()
        ledger.pending("pending-token")
        assertFalse(ledger.beginCheckout())
        ledger.reconcile(setOf("pending-token"))
        assertTrue(ledger.blocked)
        ledger.reconcile(emptySet())
        assertTrue(ledger.beginCheckout())
    }

    @Test fun pendingCanBecomePurchasedAndConsumedExactlyOnce() {
        val ledger = TipLedger()
        ledger.pending("token")
        assertTrue(ledger.beginConsume("token"))
        assertFalse(ledger.beginConsume("token"))
        assertTrue(ledger.consumed("token", true))
        assertFalse(ledger.blocked)
        assertTrue(ledger.alreadyHandled("token"))
        assertFalse(ledger.beginConsume("token"))
        assertFalse(ledger.consumed("token", true))
    }

    @Test fun consumeFailurePreventsNewCheckoutAndAllowsRecovery() {
        val ledger = TipLedger()
        assertTrue(ledger.beginConsume("token"))
        assertFalse(ledger.consumed("token", false))
        assertFalse(ledger.beginCheckout())
        ledger.reconcile(setOf("token"))
        assertTrue(ledger.beginConsume("token"))
        assertTrue(ledger.consumed("token", true))
        assertTrue(ledger.beginCheckout())
    }

    @Test fun queryRacingConsumptionDoesNotReleasePaymentLock() {
        val ledger = TipLedger()
        ledger.beginConsume("token")
        ledger.reconcile(emptySet())
        assertFalse(ledger.beginCheckout())
        ledger.consumed("token", true)
        assertTrue(ledger.beginCheckout())
    }

    @Test fun restartRecoversUnconsumedReceiptFromPlayWithoutLocalJournal() {
        val beforeRestart = TipLedger()
        beforeRestart.beginConsume("owned-token")
        beforeRestart.consumed("owned-token", false)
        val afterRestart = TipLedger()
        afterRestart.reconcile(setOf("owned-token"))
        assertTrue(afterRestart.beginConsume("owned-token"))
        assertTrue(afterRestart.consumed("owned-token", true))
        assertFalse(afterRestart.blocked)
    }

    @Test fun oneCompletedTipDoesNotClearAnotherPendingTip() {
        val ledger = TipLedger()
        ledger.pending("later")
        ledger.beginConsume("now")
        ledger.consumed("now", true)
        assertTrue(ledger.blocked)
    }
}
