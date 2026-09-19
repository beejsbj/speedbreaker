package dev.burooj.speedbreaker.enforcement

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementEngineTest {
    @Test
    fun `leave unlocks at exactly eight seconds while continue waits for total breath`() {
        val settings = settings(breathSeconds = 12)
        val leaveEngine = EnforcementEngine()
        leaveEngine.update(settings, observation(elapsedMs = 0))

        val tooEarly = leaveEngine.choose(Choice.Leave, settings, observation(elapsedMs = 7_999))
        assertEquals(TARGET, tooEarly.breaker?.packageName)
        assertTrue(tooEarly.effects.isEmpty())

        val atEight = leaveEngine.choose(Choice.Leave, settings, observation(elapsedMs = 8_000))
        assertNull(atEight.breaker)
        assertEquals(listOf(Effect.GoHome, Effect.Acknowledge), atEight.effects)

        val continueEngine = EnforcementEngine()
        continueEngine.update(settings, observation(elapsedMs = 0))
        assertEquals(
            TARGET,
            continueEngine.choose(
                Choice.Continue,
                settings,
                observation(elapsedMs = 11_999),
            ).breaker?.packageName,
        )
        assertNull(
            continueEngine.choose(
                Choice.Continue,
                settings,
                observation(elapsedMs = 12_000),
            ).breaker,
        )
    }

    @Test
    fun `continuous interval triggers at its exact boundary`() {
        val settings = settings(continuousSeconds = 8)
        val engine = continuedEngine(settings, continuedAtMs = 12_000)

        assertNull(engine.update(settings, observation(elapsedMs = 19_999)).breaker)
        val update = engine.update(settings, observation(elapsedMs = 20_000))

        assertEquals(Trigger.CONTINUOUS, update.breaker?.trigger)
        assertEquals(20_000L, update.breaker?.startedElapsedMs)
    }

    @Test
    fun `absence of exactly sixty seconds resumes without counting the gap`() {
        val settings = settings(continuousSeconds = 10)
        val engine = continuedEngine(settings, continuedAtMs = 12_000)
        engine.update(settings, observation(elapsedMs = 17_000))
        engine.update(settings, observation(visible = emptySet(), elapsedMs = 18_000))

        assertNull(engine.update(settings, observation(elapsedMs = 78_000)).breaker)
        assertNull(engine.update(settings, observation(elapsedMs = 82_999)).breaker)
        assertEquals(
            Trigger.CONTINUOUS,
            engine.update(settings, observation(elapsedMs = 83_000)).breaker?.trigger,
        )
    }

    @Test
    fun `absence longer than sixty seconds ends the session and reopens`() {
        val settings = settings(continuousSeconds = 10)
        val engine = continuedEngine(settings, continuedAtMs = 12_000)
        engine.update(settings, observation(elapsedMs = 17_000))
        engine.update(settings, observation(visible = emptySet(), elapsedMs = 18_000))

        val update = engine.update(settings, observation(elapsedMs = 78_001))

        assertEquals(Trigger.OPENING, update.breaker?.trigger)
        assertEquals(78_001L, update.breaker?.startedElapsedMs)
    }

    @Test
    fun `non-interactive state ends the session without background accounting`() {
        val settings = settings(continuousSeconds = 10)
        val engine = continuedEngine(settings, continuedAtMs = 12_000)
        engine.update(settings, observation(elapsedMs = 17_000))
        engine.update(settings, observation(interactive = false, elapsedMs = 22_000))

        assertEquals(
            Trigger.OPENING,
            engine.update(settings, observation(elapsedMs = 27_000)).breaker?.trigger,
        )
    }

    @Test
    fun `overnight window belongs to its start day and ends exclusively`() {
        val schedule = WeeklySchedule(mapOf(1 to TimeWindow(22 * 60, 2 * 60)))
        val settings = settings(schedule = schedule)

        assertNull(
            EnforcementEngine().update(
                settings,
                observation(at = "2026-09-14T21:59:00Z"),
            ).breaker,
        )
        assertEquals(
            Trigger.OPENING,
            EnforcementEngine().update(
                settings,
                observation(at = "2026-09-14T22:00:00Z"),
            ).breaker?.trigger,
        )
        assertEquals(
            Trigger.OPENING,
            EnforcementEngine().update(
                settings,
                observation(at = "2026-09-15T01:59:00Z"),
            ).breaker?.trigger,
        )
        assertNull(
            EnforcementEngine().update(
                settings,
                observation(at = "2026-09-15T02:00:00Z"),
            ).breaker,
        )
    }

    @Test
    fun `empty schedule disables enforcement and activation while visible opens immediately`() {
        val disabled = settings(schedule = WeeklySchedule())
        assertNull(EnforcementEngine().update(disabled, observation()).breaker)

        val schedule = WeeklySchedule(mapOf(1 to TimeWindow(22 * 60, 23 * 60)))
        val scheduled = settings(schedule = schedule)
        val engine = EnforcementEngine()
        assertNull(
            engine.update(scheduled, observation(at = "2026-09-14T21:59:00Z")).breaker,
        )
        assertEquals(
            Trigger.OPENING,
            engine.update(scheduled, observation(at = "2026-09-14T22:00:00Z", elapsedMs = 60_000)).breaker?.trigger,
        )
    }

    @Test
    fun `full-day per-app schedule replaces restricted global schedule across midnight`() {
        val globallyRestricted = WeeklySchedule(mapOf(1 to TimeWindow(9 * 60, 10 * 60)))
        val alwaysActive = WeeklySchedule((1..7).associateWith { TimeWindow(0, 24 * 60) })
        val settings = settings(
            schedule = globallyRestricted,
            apps = mapOf(TARGET to AppPolicy(schedule = alwaysActive)),
        )

        assertEquals(
            Trigger.OPENING,
            EnforcementEngine().update(
                settings,
                observation(at = "2026-09-14T23:59:00Z"),
            ).breaker?.trigger,
        )
        assertEquals(
            Trigger.OPENING,
            EnforcementEngine().update(
                settings,
                observation(at = "2026-09-15T00:00:00Z"),
            ).breaker?.trigger,
        )
    }

    @Test
    fun `active pause crosses midnight while its token count resets`() {
        val start = epoch("2026-09-13T23:59:00Z")
        val settings = settings(breathSeconds = 8)
        val engine = EnforcementEngine()
        engine.update(settings, observation(epochMs = start, elapsedMs = 0))
        val paused = engine.choose(
            Choice.Pause,
            settings,
            observation(epochMs = start + 8_000, elapsedMs = 8_000),
        )
        assertEquals(1, paused.pauses.getValue(TARGET).used)

        val afterMidnight = engine.update(
            settings,
            observation(
                epochMs = epoch("2026-09-14T00:00:00Z"),
                elapsedMs = 68_000,
            ),
        )

        assertNull(afterMidnight.breaker)
        assertEquals("2026-09-14", afterMidnight.pauses.getValue(TARGET).day)
        assertEquals(0, afterMidnight.pauses.getValue(TARGET).used)
        assertEquals(start + 8_000 + FIFTEEN_MINUTES_MS, afterMidnight.pauses.getValue(TARGET).untilEpochMs)
    }

    @Test
    fun `pause expiry and end now trigger immediately only when visible`() {
        val now = epoch("2026-09-14T12:00:00Z")
        val activePause = PauseState("2026-09-14", used = 1, untilEpochMs = now + 1_000)
        val settings = settings()

        val expiryEngine = EnforcementEngine(mapOf(TARGET to activePause))
        assertNull(expiryEngine.update(settings, observation(epochMs = now)).breaker)
        assertNull(
            expiryEngine.update(
                settings,
                observation(visible = emptySet(), epochMs = now + 1_000, elapsedMs = 1_000),
            ).breaker,
        )
        assertEquals(
            Trigger.OPENING,
            expiryEngine.update(
                settings,
                observation(epochMs = now + 2_000, elapsedMs = 2_000),
            ).breaker?.trigger,
        )

        val endNowEngine = EnforcementEngine(mapOf(TARGET to activePause))
        endNowEngine.update(settings, observation(epochMs = now))
        val ended = endNowEngine.endPause(
            TARGET,
            settings,
            observation(epochMs = now, elapsedMs = 500),
        )
        assertEquals(Trigger.OPENING, ended.breaker?.trigger)
        assertEquals(0, ended.pauses.getValue(TARGET).untilEpochMs)
        assertEquals(1, ended.pauses.getValue(TARGET).used)
    }

    @Test
    fun `end now never overwrites a breaker chosen for another visible target`() {
        val now = epoch("2026-09-14T12:00:00Z")
        val activePause = PauseState("2026-09-14", used = 1, untilEpochMs = now + 60_000)
        val apps = mapOf(TARGET to AppPolicy(), "a.app" to AppPolicy())
        val settings = settings(apps = apps)
        val engine = EnforcementEngine(mapOf(TARGET to activePause))
        val visible = apps.keys
        assertEquals(
            "a.app",
            engine.update(settings, observation(visible = visible, epochMs = now)).breaker?.packageName,
        )

        val ended = engine.endPause(
            TARGET,
            settings,
            observation(visible = visible, epochMs = now, elapsedMs = 1),
        )

        assertEquals("a.app", ended.breaker?.packageName)
        assertEquals(0, ended.pauses.getValue(TARGET).untilEpochMs)
    }

    @Test
    fun `pause stays disabled after two tokens and choice is revalidated`() {
        val now = epoch("2026-09-14T12:00:00Z")
        val exhausted = PauseState("2026-09-14", used = 2)
        val settings = settings(breathSeconds = 8)
        val engine = EnforcementEngine(mapOf(TARGET to exhausted))
        engine.update(settings, observation(epochMs = now))

        val update = engine.choose(
            Choice.Pause,
            settings,
            observation(epochMs = now + 8_000, elapsedMs = 8_000),
        )

        assertEquals(TARGET, update.breaker?.packageName)
        assertEquals(2, update.pauses.getValue(TARGET).used)
    }

    @Test
    fun `each app can consume exactly two fifteen minute pauses per local day`() {
        val now = epoch("2026-09-14T12:00:00Z")
        val settings = settings(breathSeconds = 8)
        val engine = EnforcementEngine()
        engine.update(settings, observation(epochMs = now))

        val first = engine.choose(
            Choice.Pause,
            settings,
            observation(epochMs = now + 8_000, elapsedMs = 8_000),
        )
        assertEquals(now + 8_000 + FIFTEEN_MINUTES_MS, first.pauses.getValue(TARGET).untilEpochMs)
        engine.endPause(
            TARGET,
            settings,
            observation(epochMs = now + 9_000, elapsedMs = 9_000),
        )

        val second = engine.choose(
            Choice.Pause,
            settings,
            observation(epochMs = now + 17_000, elapsedMs = 17_000),
        )
        assertEquals(2, second.pauses.getValue(TARGET).used)
        engine.endPause(
            TARGET,
            settings,
            observation(epochMs = now + 18_000, elapsedMs = 18_000),
        )

        val exhausted = engine.choose(
            Choice.Pause,
            settings,
            observation(epochMs = now + 26_000, elapsedMs = 26_000),
        )
        assertEquals(TARGET, exhausted.breaker?.packageName)
        assertEquals(2, exhausted.pauses.getValue(TARGET).used)
    }

    @Test
    fun `redirect must be a configured non-target destination`() {
        val configured = listOf("one", "two", "three", "four")
        val settings = settings(breathSeconds = 8, redirects = configured)
        val engine = EnforcementEngine()
        engine.update(settings, observation())
        assertEquals(
            TARGET,
            engine.choose(Choice.Redirect("other"), settings, observation(elapsedMs = 8_000)).breaker?.packageName,
        )

        val redirected = engine.choose(
            Choice.Redirect("two"),
            settings,
            observation(elapsedMs = 8_000),
        )
        assertNull(redirected.breaker)
        assertEquals(listOf(Effect.LaunchApp("two"), Effect.Acknowledge), redirected.effects)

        val targetRedirectSettings = settings(
            breathSeconds = 8,
            apps = mapOf(TARGET to AppPolicy(), "two" to AppPolicy()),
            redirects = configured,
        )
        val targetEngine = EnforcementEngine()
        targetEngine.update(targetRedirectSettings, observation())
        assertEquals(
            TARGET,
            targetEngine.choose(
                Choice.Redirect("two"),
                targetRedirectSettings,
                observation(elapsedMs = 8_000),
            ).breaker?.packageName,
        )
    }

    @Test
    fun `choice handler revalidates visibility and configured breath`() {
        val settings = settings(breathSeconds = 60)
        val engine = EnforcementEngine()
        engine.update(settings, observation())

        val absent = engine.choose(
            Choice.Continue,
            settings,
            observation(visible = emptySet(), elapsedMs = 60_000),
        )
        assertEquals(TARGET, absent.breaker?.packageName)
        assertTrue(absent.effects.isEmpty())

        val visibleAgain = engine.update(settings, observation(elapsedMs = 60_001))
        assertEquals(Trigger.OPENING, visibleAgain.breaker?.trigger)
        assertEquals(60, visibleAgain.breaker?.breathSeconds)
        assertNull(
            engine.choose(
                Choice.Continue,
                settings,
                observation(elapsedMs = 60_001),
            ).breaker,
        )
    }

    @Test
    fun `safety yields immediately and navigation yields at exactly eight seconds`() {
        val settings = settings()
        val safetyEngine = EnforcementEngine()
        safetyEngine.update(settings, observation())
        assertNull(
            safetyEngine.update(
                settings,
                observation(safety = true, elapsedMs = 1),
            ).breaker,
        )

        val navigationEngine = EnforcementEngine()
        navigationEngine.update(settings, observation())
        assertEquals(
            TARGET,
            navigationEngine.update(
                settings,
                observation(navigationAway = true, elapsedMs = 7_999),
            ).breaker?.packageName,
        )
        assertNull(
            navigationEngine.update(
                settings,
                observation(navigationAway = true, elapsedMs = 8_000),
            ).breaker,
        )
        assertNull(navigationEngine.update(settings, observation(elapsedMs = 8_001)).breaker)
    }

    @Test
    fun `ordinary navigation cannot escape hard lock by hiding the target`() {
        val settings = settings()
        val engine = EnforcementEngine()
        engine.update(settings, observation())

        assertEquals(
            TARGET,
            engine.update(
                settings,
                observation(
                    visible = emptySet(),
                    navigationAway = true,
                    elapsedMs = 2_000,
                ),
            ).breaker?.packageName,
        )
        assertEquals(
            TARGET,
            engine.update(
                settings,
                observation(visible = emptySet(), elapsedMs = 7_999),
            ).breaker?.packageName,
        )
        assertNull(
            engine.update(
                settings,
                observation(visible = emptySet(), elapsedMs = 8_000),
            ).breaker,
        )
    }

    @Test
    fun `consent withdrawal suppresses all enforcement immediately`() {
        val enabled = settings()
        val engine = EnforcementEngine()
        engine.update(enabled, observation())

        val disabled = enabled.copy(consentAccepted = false)
        assertNull(engine.update(disabled, observation(elapsedMs = 1)).breaker)
        assertNull(engine.update(disabled, observation(elapsedMs = 10_000)).breaker)
    }

    @Test
    fun `settings changes clear inactive sessions and apply a shorter interval immediately`() {
        val initial = settings(continuousSeconds = 20)
        val engine = continuedEngine(initial, continuedAtMs = 12_000)
        engine.update(initial, observation(elapsedMs = 22_000))

        val shortened = settings(continuousSeconds = 10)
        assertEquals(
            Trigger.CONTINUOUS,
            engine.update(shortened, observation(elapsedMs = 22_000)).breaker?.trigger,
        )

        val disabled = settings(
            continuousSeconds = 10,
            schedule = WeeklySchedule(),
        )
        assertNull(engine.update(disabled, observation(elapsedMs = 22_001)).breaker)
        assertNull(engine.update(shortened, observation(elapsedMs = 22_002)).breaker?.takeIf { it.trigger == Trigger.CONTINUOUS })
        assertEquals(Trigger.OPENING, engine.update(shortened, observation(elapsedMs = 22_002)).breaker?.trigger)
    }

    @Test
    fun `multiple visible targets are each mediated once in stable order`() {
        val apps = mapOf("z.app" to AppPolicy(), "a.app" to AppPolicy())
        val settings = settings(breathSeconds = 8, apps = apps)
        val engine = EnforcementEngine()

        assertEquals(
            "a.app",
            engine.update(
                settings,
                observation(visible = apps.keys, elapsedMs = 0),
            ).breaker?.packageName,
        )
        engine.choose(
            Choice.Continue,
            settings,
            observation(visible = apps.keys, elapsedMs = 8_000),
        )

        assertEquals(
            "z.app",
            engine.update(
                settings,
                observation(visible = apps.keys, elapsedMs = 8_001),
            ).breaker?.packageName,
        )
        engine.choose(
            Choice.Continue,
            settings,
            observation(visible = apps.keys, elapsedMs = 16_001),
        )
        assertNull(
            engine.update(
                settings,
                observation(visible = apps.keys, elapsedMs = 16_002),
            ).breaker,
        )
    }

    private fun continuedEngine(settings: Settings, continuedAtMs: Long): EnforcementEngine =
        EnforcementEngine().also { engine ->
            engine.update(settings, observation(elapsedMs = 0))
            val update = engine.choose(
                Choice.Continue,
                settings,
                observation(elapsedMs = continuedAtMs),
            )
            assertNull(update.breaker)
        }

    private fun settings(
        breathSeconds: Int = 12,
        continuousSeconds: Int? = 600,
        schedule: WeeklySchedule? = null,
        redirects: List<String> = emptyList(),
        apps: Map<String, AppPolicy> = mapOf(
            TARGET to AppPolicy(continuousSeconds = continuousSeconds),
        ),
    ) = Settings(
        consentAccepted = true,
        breathSeconds = breathSeconds,
        apps = apps,
        redirects = redirects,
        schedule = schedule,
    )

    private fun observation(
        visible: Set<String> = setOf(TARGET),
        interactive: Boolean = true,
        safety: Boolean = false,
        navigationAway: Boolean = false,
        elapsedMs: Long = 0,
        epochMs: Long = epoch(DEFAULT_TIME),
        at: String? = null,
    ) = Observation(
        visiblePackages = visible,
        interactive = interactive,
        safetyUiVisible = safety,
        navigationAway = navigationAway,
        elapsedMs = elapsedMs,
        epochMs = at?.let(::epoch) ?: epochMs,
        zoneId = UTC,
    )

    private fun epoch(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    private companion object {
        const val TARGET = "target.app"
        const val DEFAULT_TIME = "2026-09-14T12:00:00Z"
        const val FIFTEEN_MINUTES_MS = 15 * 60 * 1_000L
        val UTC: ZoneId = ZoneId.of("UTC")
    }
}
