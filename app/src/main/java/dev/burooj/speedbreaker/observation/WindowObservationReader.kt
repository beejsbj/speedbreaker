package dev.burooj.speedbreaker.observation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo

/** Reads every sighted-user-interactive application window, including PiP and split screen. */
internal class WindowObservationReader(
    private val service: AccessibilityService,
) {
    private var lastNonEmpty: Set<String> = emptySet()
    private var lastNonEmptyElapsedMs: Long = 0L

    fun visiblePackages(nowElapsedMs: Long): Set<String> {
        val packages = buildSet {
            service.windows.forEach { window ->
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
                window.root?.packageName?.toString()?.let(::add)
            }
        }
        if (packages.isNotEmpty()) {
            lastNonEmpty = packages
            lastNonEmptyElapsedMs = nowElapsedMs
            return packages
        }

        // Window roots can be unavailable for a frame during transitions. A short hold avoids
        // treating the target's keyboard or transient window as an app departure.
        return if (nowElapsedMs - lastNonEmptyElapsedMs <= EMPTY_WINDOW_GRACE_MS) {
            lastNonEmpty
        } else {
            emptySet()
        }
    }

    private companion object {
        const val EMPTY_WINDOW_GRACE_MS = 750L
    }
}
