package dev.burooj.speedbreaker.observation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo

internal data class WindowSnapshot(
    val applicationPackages: Set<String>,
    val systemPanelOwnsDisplay: Boolean,
    val systemUiApplicationVisible: Boolean,
)

/** Bars, small system popups and application windows cannot suspend the overlay. */
internal fun systemPanelOwnsDisplay(
    type: Int,
    packageName: String?,
    displayMatches: Boolean,
    screen: Rect,
    bounds: Rect,
    touchRegion: Region?,
): Boolean {
    if (type != AccessibilityWindowInfo.TYPE_SYSTEM ||
        packageName != "com.android.systemui" || !displayMatches || screen.isEmpty
    ) return false
    if (touchRegion == null) return bounds.contains(screen)
    // A bounding rectangle can conceal holes or disconnected status/navigation bars.
    val uncovered = Region(screen)
    uncovered.op(touchRegion, Region.Op.DIFFERENCE)
    return uncovered.isEmpty
}

/** Reads every sighted-user-interactive application window, including PiP and split screen. */
internal class WindowObservationReader(
    private val service: AccessibilityService,
) {
    private var lastNonEmpty: Set<String> = emptySet()
    private var lastNonEmptyElapsedMs: Long = 0L

    @Suppress("DEPRECATION")
    fun snapshot(nowElapsedMs: Long): WindowSnapshot {
        val display = service.getSystemService(WindowManager::class.java).defaultDisplay
        val metrics = DisplayMetrics().also(display::getRealMetrics)
        val screen = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val packages = mutableSetOf<String>()
        var panelOwnsDisplay = false
        service.windows.forEach { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.type != AccessibilityWindowInfo.TYPE_SYSTEM
            ) return@forEach
            // Disable descendant prefetch where Android exposes that option: only
            // the root package identity and window metadata are needed.
            val root = if (Build.VERSION.SDK_INT >= 33) window.getRoot(0) else window.root
            val packageName = root?.packageName?.toString()
            root?.recycle()
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                packageName?.let(packages::add)
            } else if (packageName == "com.android.systemui") {
                val bounds = Rect().also(window::getBoundsInScreen)
                val region = if (Build.VERSION.SDK_INT >= 30) {
                    Region().also(window::getRegionInScreen)
                } else null
                panelOwnsDisplay = panelOwnsDisplay || systemPanelOwnsDisplay(
                    window.type, packageName,
                    Build.VERSION.SDK_INT < 30 || window.displayId == display.displayId,
                    screen, bounds, region,
                )
            }
        }
        val systemUiApplicationVisible = "com.android.systemui" in packages
        if (packages.isNotEmpty()) {
            lastNonEmpty = packages
            lastNonEmptyElapsedMs = nowElapsedMs
        }

        // Window roots can be unavailable for a frame during transitions. A short hold avoids
        // treating the target's keyboard or transient window as an app departure.
        val visible = if (packages.isNotEmpty()) packages else if (
            nowElapsedMs - lastNonEmptyElapsedMs <= EMPTY_WINDOW_GRACE_MS
        ) {
            lastNonEmpty
        } else {
            emptySet()
        }
        return WindowSnapshot(visible, panelOwnsDisplay, systemUiApplicationVisible)
    }

    private companion object {
        const val EMPTY_WINDOW_GRACE_MS = 750L
    }
}
