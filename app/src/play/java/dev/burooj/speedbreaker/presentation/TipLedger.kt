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
    val blocked: Boolean get() = checkoutOpen || unresolved.isNotEmpty()

    fun beginCheckout(): Boolean {
        if (blocked) return false
        checkoutOpen = true
        return true
    }

    fun launched() { awaitingPlayReturn = true }

    fun checkoutReturned() {
        checkoutOpen = false
        awaitingPlayReturn = false
    }

    fun billingReturned() {
        if (awaitingPlayReturn) checkoutReturned()
    }

    fun alreadyHandled(token: String): Boolean = token in handled

    fun pending(token: String) { unresolved.add(token) }

    fun beginConsume(token: String): Boolean {
        unresolved.add(token)
        if (token in handled) {
            unresolved.remove(token)
            return false
        }
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
