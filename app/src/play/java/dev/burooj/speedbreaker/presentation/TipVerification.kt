package dev.burooj.speedbreaker.presentation

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal val tipProductIds = listOf(
    "speedbreaker_tip_small", "speedbreaker_tip_medium", "speedbreaker_tip_large",
)

/** Verifies the exact Play-signed JSON bytes, before trusting any receipt fields. */
internal class TipVerification(encodedPublicKey: String) {
    private val key: PublicKey? = runCatching {
        val clean = encodedPublicKey.filterNot(Char::isWhitespace)
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(clean)))
    }.getOrNull()
    val configured: Boolean get() = key != null

    fun verify(json: String, signature: String): Boolean = runCatching {
        val publicKey = key ?: return false
        Signature.getInstance("SHA1withRSA").run {
            initVerify(publicKey)
            update(json.toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(signature))
        }
    }.getOrDefault(false)
}

internal enum class TipReceiptState { Pending, Purchased, Other }
internal enum class TipDecision { Ignore, Pending, Reject, Consume }
internal data class TipReceipt(
    val state: TipReceiptState,
    val products: List<String>,
    val packageName: String,
    val token: String,
    val quantity: Int,
    val suspended: Boolean,
    val json: String,
    val signature: String,
)

/** Pure trust boundary, including no consumption of pending or unknown receipts. */
internal fun decideTipReceipt(
    receipt: TipReceipt,
    expectedPackage: String,
    verify: (String, String) -> Boolean,
): TipDecision {
    if (receipt.products.none(tipProductIds::contains)) return TipDecision.Ignore
    if (receipt.state == TipReceiptState.Pending) return TipDecision.Pending
    if (receipt.state != TipReceiptState.Purchased) return TipDecision.Ignore
    return if (verify(receipt.json, receipt.signature) && receipt.packageName == expectedPackage &&
        receipt.products.size == 1 && receipt.products.single() in tipProductIds &&
        receipt.token.isNotBlank() && receipt.quantity == 1 && !receipt.suspended
    ) TipDecision.Consume else TipDecision.Reject
}
