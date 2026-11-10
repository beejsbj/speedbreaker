package dev.burooj.speedbreaker.observation

/**
 * What the app sent someone into Android settings to do, so the service can bring them back the
 * moment it is done instead of leaving them to find their own way. Held in memory only: if the
 * process is restarted in between, nobody is brought back and Back still works.
 */
internal object SetupReturn {
    enum class Waiting { ServiceOn, PhoneAllowed }

    @Volatile private var waiting: Waiting? = null
    @Volatile private var sinceElapsedMs = 0L

    fun expect(what: Waiting, nowElapsedMs: Long) {
        sinceElapsedMs = nowElapsedMs
        waiting = what
    }

    /** They came back on their own, or went elsewhere: do not pull them in later. */
    fun cancel() {
        waiting = null
    }

    fun isWaitingFor(what: Waiting, nowElapsedMs: Long): Boolean =
        waiting == what && nowElapsedMs - sinceElapsedMs in 0..WINDOW_MS

    /** True once, when [what] was asked for recently enough that returning is still expected. */
    fun take(what: Waiting, nowElapsedMs: Long): Boolean {
        if (!isWaitingFor(what, nowElapsedMs)) return false
        waiting = null
        return true
    }

    // Long enough to read Android's warning and find the switch; short enough that an unrelated
    // visit to settings later in the day does not open Speedbreaker.
    private const val WINDOW_MS = 5 * 60_000L
}
