package dev.burooj.speedbreaker.observation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/** Retain type attribution briefly when a transition removes its source window. */
internal class WindowTypeHistory(private val graceMs: Long = 750L) {
    private data class Seen(val type: Int, val atElapsedMs: Long)
    private val seen = mutableMapOf<Int, Seen>()

    fun record(types: Map<Int, Int>, nowElapsedMs: Long) {
        seen.entries.removeAll { nowElapsedMs - it.value.atElapsedMs > graceMs }
        types.forEach { (id, type) -> if (id >= 0) seen[id] = Seen(type, nowElapsedMs) }
    }

    fun isApplicationTransition(id: Int, nowElapsedMs: Long): Boolean {
        val previous = seen[id] ?: return false
        return id >= 0 && nowElapsedMs - previous.atElapsedMs in 0..graceMs &&
            previous.type == AccessibilityWindowInfo.TYPE_APPLICATION
    }
}

/**
 * Launchers and SystemUI application windows (Recents) are navigation. SystemUI popups
 * such as the volume panel or heads-up notifications are SYSTEM windows and are not,
 * or the breath would be released at eight seconds before its choices appeared.
 * Cheap checks run first so events without a breath cost no window-list read.
 */
internal inline fun navigationEvent(
    hasBreaker: Boolean,
    windowTransition: Boolean,
    isNavigationPackage: () -> Boolean,
    applicationTransition: () -> Boolean,
): Boolean = hasBreaker && windowTransition && isNavigationPackage() && applicationTransition()

/** Only window-set changes can introduce a window whose type is worth remembering. */
internal fun shouldRecordWindowTypes(eventType: Int, windowChanges: Int, sdkInt: Int): Boolean {
    if (eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return false
    // windowChanges does not exist before API 28; record on every window-set change there.
    return sdkInt < 28 || windowChanges and AccessibilityEvent.WINDOWS_CHANGE_ADDED != 0
}

/** Reads every sighted-user-interactive application window, including PiP and split screen. */
internal class WindowObservationReader(
    private val service: AccessibilityService,
) {
    private var lastNonEmpty: Set<String> = emptySet()
    private var lastNonEmptyElapsedMs: Long = 0L
    private val typeHistory = WindowTypeHistory()

    fun isApplicationTransition(windowId: Int, nowElapsedMs: Long): Boolean {
        noteWindows(nowElapsedMs)
        return typeHistory.isApplicationTransition(windowId, nowElapsedMs)
    }

    /** Remember a window's type when it is added, before a fast removal can hide it. */
    fun noteWindows(nowElapsedMs: Long) {
        typeHistory.record(service.windows.associate { it.id to it.type }, nowElapsedMs)
    }

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
