package dev.burooj.speedbreaker.presentation

import org.junit.Assert.*
import org.junit.Test

class TipReceiptTest {
    private val receipt = TipReceipt(TipReceiptState.Purchased, listOf("speedbreaker_tip_small"),
        "dev.burooj.speedbreaker", "token", 1, false, "signed-json", "signature")
    private fun decision(value: TipReceipt, verified: Boolean = true) =
        decideTipReceipt(value, "dev.burooj.speedbreaker") { _, _ -> verified }

    @Test fun pendingIsNeverVerifiedOrConsumedEvenWithValidSignature() {
        assertEquals(TipDecision.Pending, decideTipReceipt(
            receipt.copy(state = TipReceiptState.Pending), "dev.burooj.speedbreaker",
        ) { _, _ -> error("Pending must not enter the consumption boundary") })
    }

    @Test fun purchasedNeedsSignaturePackageTokenProductAndQuantityChecks() {
        assertEquals(TipDecision.Consume, decision(receipt))
        assertEquals(TipDecision.Reject, decision(receipt, verified = false))
        for (invalid in listOf(
            receipt.copy(packageName = "another.app"),
            receipt.copy(token = ""),
            receipt.copy(products = listOf("speedbreaker_tip_small", "another_product")),
            receipt.copy(quantity = 2),
            receipt.copy(suspended = true),
        )) assertEquals(TipDecision.Reject, decision(invalid))
    }

    @Test fun unrelatedOrUnspecifiedPurchasesAreIgnored() {
        assertEquals(TipDecision.Ignore, decision(receipt.copy(products = listOf("another_product"))))
        assertEquals(TipDecision.Ignore, decision(receipt.copy(state = TipReceiptState.Other)))
    }
}
