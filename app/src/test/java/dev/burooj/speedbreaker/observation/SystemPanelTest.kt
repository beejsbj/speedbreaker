package dev.burooj.speedbreaker.observation

import android.graphics.Rect
import android.graphics.Region
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dev.burooj.speedbreaker.enforcement.Choice
import dev.burooj.speedbreaker.enforcement.Effect
import dev.burooj.speedbreaker.enforcement.EnforcementEngine
import dev.burooj.speedbreaker.enforcement.Observation
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.Settings
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SystemPanelTest {
    private val screen = Rect(0, 0, 1080, 2400)
    private val settings = Settings(
        consentAccepted = true, apps = mapOf("target.app" to AppPolicy()),
    )

    @Test fun trustedFullDisplayNativePanelOwnsInput() {
        assertTrue(panel(Region(screen)))
    }

    @Test fun fullscreenApplicationRecentsAndOtherDisplaysCannotSuspendPresentation() {
        assertFalse(panel(Region(screen), type = AccessibilityWindowInfo.TYPE_APPLICATION))
        assertFalse(panel(Region(screen), packageName = "other.app"))
        assertFalse(panel(Region(screen), sameDisplay = false))
        assertFalse(panel(Region(screen), packageName = null))
    }

    @Test fun disconnectedSystemBarsAreNotAFullscreenPanel() {
        val bars = Region(0, 0, 1080, 128)
        bars.op(Rect(0, 2337, 1080, 2400), Region.Op.UNION)
        assertEquals(screen, bars.bounds)
        assertFalse(panel(bars))
    }

    @Test fun fullscreenBoundingBoxWithTouchableTargetHoleCannotSuspendPresentation() {
        val withHole = Region(screen)
        withHole.op(Rect(100, 300, 980, 2000), Region.Op.DIFFERENCE)
        assertEquals(screen, withHole.bounds)
        assertFalse(panel(withHole))
    }

    @Test fun smallSystemPopupsAndCollapsedBarsCannotSuspendPresentation() {
        assertFalse(panel(Region(0, 0, 1080, 160)))
        assertFalse(panel(Region(700, 600, 1080, 1800)))
        assertFalse(panel(null, bounds = Rect(0, 0, 1080, 128)))
    }

    @Test fun olderBoundsFallbackRequiresFullCoverageAndAValidDisplay() {
        assertTrue(panel(null))
        assertFalse(systemPanelOwnsDisplay(
            AccessibilityWindowInfo.TYPE_SYSTEM, "com.android.systemui", true,
            Rect(), screen, null,
        ))
    }

    @Test fun closingPanelResumesOriginalBreakerAndEightSecondDeadline() {
        val engine = EnforcementEngine()
        val original = engine.update(settings, observation(0)).breaker
        assertNotNull(original)
        assertEquals(original, engine.update(settings, observation(1_000, emptySet())).breaker)
        assertEquals(original, engine.update(settings, observation(3_000)).breaker)
        assertEquals(original, engine.choose(Choice.Leave, settings, observation(7_999)).breaker)
        val leave = engine.choose(Choice.Leave, settings, observation(8_000))
        assertNull(leave.breaker)
        assertTrue(Effect.GoHome in leave.effects)
    }

    @Test fun nativeSettingsCancelsRetainedBreakerAndCannotRestoreItsOldDeadline() {
        val engine = EnforcementEngine()
        val original = engine.update(settings, observation(0)).breaker
        engine.update(settings, observation(1_000, emptySet()))
        assertNull(engine.update(
            settings, observation(2_000, setOf("com.android.settings")).copy(safetyUiVisible = true),
        ).breaker)
        val next = engine.update(settings, observation(3_000)).breaker
        assertNotNull(next)
        assertNotEquals(original, next)
        assertEquals(3_000L, next!!.startedElapsedMs)
    }

    @Test fun backWhileNativePanelOwnsDisplayDoesNotCancelOrResetTheBreath() {
        val engine = EnforcementEngine()
        val original = engine.update(settings, observation(0)).breaker
        // The panel hides the target; Back is judged against a fresh panel snapshot.
        val back = navigationAwayForBack(panelOwnsDisplay = true, latched = false)
        assertFalse(back)
        engine.update(settings, observation(1_000, emptySet()).copy(navigationAway = back))
        assertEquals(original, engine.update(settings, observation(3_000)).breaker)
        // The original deadline still holds and the breath is not suppressed at 8 seconds.
        assertEquals(original, engine.choose(Choice.Leave, settings, observation(7_999)).breaker)
        assertNotNull(engine.update(settings, observation(8_000)).breaker)
    }

    @Test fun backWithoutNativePanelIsGenuineNavigationAndKeepsAnExistingLatch() {
        assertTrue(navigationAwayForBack(panelOwnsDisplay = false, latched = false))
        assertTrue(navigationAwayForBack(panelOwnsDisplay = false, latched = true))
        // A latch set by earlier real navigation is not erased by a panel-owned Back.
        assertTrue(navigationAwayForBack(panelOwnsDisplay = true, latched = true))
        // Unguarded Back with the target hidden is remembered and ends the breath at 8s.
        val engine = EnforcementEngine()
        engine.update(settings, observation(0))
        engine.update(settings, observation(1_000, emptySet()).copy(navigationAway = true))
        assertNull(engine.update(settings, observation(8_000, emptySet())).breaker)
    }

    @Test fun onlyWindowAdditionsRecordTypesForQueuedRemovalAttribution() {
        val added = AccessibilityEvent.WINDOWS_CHANGE_ADDED
        val windows = AccessibilityEvent.TYPE_WINDOWS_CHANGED
        assertTrue(shouldRecordWindowTypes(windows, added, 35))
        assertTrue(shouldRecordWindowTypes(windows, added or AccessibilityEvent.WINDOWS_CHANGE_TITLE, 28))
        assertFalse(shouldRecordWindowTypes(windows, AccessibilityEvent.WINDOWS_CHANGE_REMOVED, 35))
        assertFalse(shouldRecordWindowTypes(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, added, 35))
        // API 26-27 do not expose windowChanges, so every window-set change records.
        assertTrue(shouldRecordWindowTypes(windows, 0, 27))
        assertFalse(shouldRecordWindowTypes(windows, 0, 28))
    }

    @Test fun briefRecentsWindowRecordedOnAdditionStillLatchesNavigation() {
        val history = WindowTypeHistory()
        history.record(mapOf(9 to AccessibilityWindowInfo.TYPE_APPLICATION), 100)
        history.record(emptyMap(), 300) // snapshot after the window disappeared
        assertTrue(history.isApplicationTransition(9, 400))
    }

    @Test fun windowDisplayIsOnlyComparedWhereAndroidExposesIt() {
        assertTrue(windowOnDisplay(26, 5, 0))
        assertTrue(windowOnDisplay(29, 5, 0))
        assertFalse(windowOnDisplay(30, 5, 0))
        assertTrue(windowOnDisplay(35, 0, 0))
    }

    private fun panel(
        region: Region?,
        type: Int = AccessibilityWindowInfo.TYPE_SYSTEM,
        packageName: String? = "com.android.systemui",
        sameDisplay: Boolean = true,
        bounds: Rect = screen,
    ) = systemPanelOwnsDisplay(type, packageName, sameDisplay, screen, bounds, region)

    private fun observation(time: Long, packages: Set<String> = setOf("target.app")) = Observation(
        visiblePackages = packages, interactive = true, safetyUiVisible = false,
        elapsedMs = time, epochMs = 1_800_000_000_000L + time, zoneId = ZoneId.of("UTC"),
    )
}
