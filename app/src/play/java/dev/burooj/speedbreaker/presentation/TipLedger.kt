package dev.burooj.speedbreaker.presentation

/** No entitlements. Play owns the durable queue; this ledger only prevents concurrent handling. */
internal class TipLedger {
    private val consuming = mutableSetOf<String>()
    private val handled = mutableSetOf<String>()
    private val unresolved = mutableSetOf<String>()
    var checkoutOpen = false
        private set
    var awaitingPlayReturn = false
        private set
    var checkoutGeneration = 0L
        private set
    val blocked: Boolean get() = checkoutOpen || unresolved.isNotEmpty()

    fun beginCheckout(): Boolean {
        if (blocked) return false
        checkoutGeneration++
        checkoutOpen = true
        return true
    }

    /** This preparation owns the checkout lock, but never bypasses unresolved receipts. */
    fun ownsCheckout(generation: Long): Boolean = checkoutOpen && checkoutGeneration == generation

    fun preparing(generation: Long): Boolean = ownsCheckout(generation) &&
        !awaitingPlayReturn && unresolved.isEmpty()

    fun launched(generation: Long = checkoutGeneration): Boolean {
        if (!preparing(generation)) return false
        awaitingPlayReturn = true
        return true
    }

    fun invalidatePreparation() {
        if (!awaitingPlayReturn) checkoutReturned()
    }

    fun checkoutReturned(generation: Long = checkoutGeneration) {
        if (generation != checkoutGeneration) return
        checkoutOpen = false
        awaitingPlayReturn = false
    }

    fun billingReturned(generation: Long = checkoutGeneration) {
        if (awaitingPlayReturn) checkoutReturned(generation)
    }

    fun alreadyHandled(token: String): Boolean = token in handled

    fun pending(token: String) {
        invalidatePreparation()
        unresolved.add(token)
    }

    fun beginConsume(token: String): Boolean {
        if (token in handled) return false
        invalidatePreparation()
        unresolved.add(token)
        return consuming.add(token)
    }

    fun consumed(token: String, ok: Boolean): Boolean {
        consuming.remove(token)
        if (!ok) return false
        unresolved.remove(token)
        return handled.add(token)
    }

    /** A successful authoritative query removes canceled pending purchases, not active consumes. */
    fun reconcile(ownedTokens: Set<String>) {
        unresolved.retainAll(ownedTokens + consuming)
    }
}
