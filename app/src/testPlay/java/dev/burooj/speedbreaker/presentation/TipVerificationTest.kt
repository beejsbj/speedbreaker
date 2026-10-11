package dev.burooj.speedbreaker.presentation

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class TipVerificationTest {
    @Test fun emptyOrMalformedKeyDisablesTipsAndRejectsReceipts() {
        for (key in listOf("", "not-a-key")) {
            val verifier = TipVerification(key)
            assertFalse(verifier.configured)
            assertFalse(verifier.verify("{}", "signature"))
        }
    }

    @Test fun exactSignedReceiptPassesButTamperingAndWrongKeyFail() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val receipt = """{"packageName":"dev.burooj.speedbreaker","productId":"speedbreaker_tip_small"}"""
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(pair.private)
            update(receipt.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        val verifier = TipVerification(Base64.getEncoder().encodeToString(pair.public.encoded))
        assertTrue(verifier.configured)
        assertTrue(verifier.verify(receipt, signature))
        assertFalse(verifier.verify(receipt.replace("small", "large"), signature))
        assertFalse(verifier.verify(receipt + " ", signature))
        assertFalse(verifier.verify(receipt, "malformed"))
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(TipVerification(Base64.getEncoder().encodeToString(other.public.encoded))
            .verify(receipt, signature))
    }
}
