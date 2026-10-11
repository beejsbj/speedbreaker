package dev.burooj.speedbreaker.observation

import android.graphics.Rect
import android.graphics.Region
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
