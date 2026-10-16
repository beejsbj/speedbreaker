package dev.burooj.speedbreaker.enforcement

import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine explains itself through [EngineDecision]s. These tests pin the explanations;
 * [EnforcementEngineTest] is untouched and pins that the behavior behind them did not move.
 */
class EngineDecisionsTest {
    private val queued = OpeningFacts(
        consentAccepted = true,
        interactive = true,
        safetyUiVisible = false,
        paused = false,
        scheduleActive = true,
        triggerQueued = true,
        inSession = false,
        suppressedUntilInactive = false,
        previouslyActive = false,
        otherBreakerActive = false,
        navigationAway = false,
    )

    // --- the pure reason function -------------------------------------------------------

    @Test
    fun `an eligible target with a queued opening is not skipped`() {
        assertEquals(null, openingSkipReason(queued))
    }

    @Test
    fun `each skip reason is reported when it alone applies`() {
        val unqueued = queued.copy(triggerQueued = false)
        assertEquals(OpeningSkip.CONSENT_OFF, openingSkipReason(queued.copy(consentAccepted = false)))
        assertEquals(OpeningSkip.NONINTERACTIVE, openingSkipReason(queued.copy(interactive = false)))
        assertEquals(OpeningSkip.SAFETY, openingSkipReason(queued.copy(safetyUiVisible = true)))
        assertEquals(OpeningSkip.PAUSED, openingSkipReason(queued.copy(paused = true)))
        assertEquals(OpeningSkip.SCHEDULE_INACTIVE, openingSkipReason(queued.copy(scheduleActive = false)))
        assertEquals(OpeningSkip.IN_SESSION, openingSkipReason(unqueued.copy(inSession = true)))
        assertEquals(
            OpeningSkip.SUPPRESSED_UNTIL_INACTIVE,
            openingSkipReason(unqueued.copy(suppressedUntilInactive = true)),
        )
        assertEquals(OpeningSkip.PREVIOUSLY_ACTIVE, openingSkipReason(unqueued.copy(previouslyActive = true)))
        assertEquals(OpeningSkip.OTHER_BREAKER_ACTIVE, openingSkipReason(queued.copy(otherBreakerActive = true)))
        assertEquals(OpeningSkip.NAVIGATION_PENDING, openingSkipReason(queued.copy(navigationAway = true)))
    }

    @Test
    fun `ineligibility outranks session and suppression so a capture rules out the cheap causes first`() {
        val everything = OpeningFacts(
            consentAccepted = false,
            interactive = false,
            safetyUiVisible = true,
            paused = true,
            scheduleActive = false,
            triggerQueued = false,
            inSession = true,
            suppressedUntilInactive = true,
            previouslyActive = true,
            otherBreakerActive = true,
            navigationAway = true,
        )
        assertEquals(OpeningSkip.CONSENT_OFF, openingSkipReason(everything))
        assertEquals(OpeningSkip.NONINTERACTIVE, openingSkipReason(everything.copy(consentAccepted = true)))
        assertEquals(
            OpeningSkip.SAFETY,
            openingSkipReason(everything.copy(consentAccepted = true, interactive = true)),
        )
        assertEquals(
            OpeningSkip.PAUSED,
            openingSkipReason(everything.copy(consentAccepted = true, interactive = true, safetyUiVisible = false)),
        )
        assertEquals(
            OpeningSkip.SCHEDULE_INACTIVE,
            openingSkipReason(
                everything.copy(
                    consentAccepted = true,
                    interactive = true,
                    safetyUiVisible = false,
                    paused = false,
                ),
            ),
        )
        val eligible = everything.copy(
            consentAccepted = true,
            interactive = true,
            safetyUiVisible = false,
            paused = false,
            scheduleActive = true,
        )
        assertEquals(OpeningSkip.IN_SESSION, openingSkipReason(eligible))
        assertEquals(OpeningSkip.SUPPRESSED_UNTIL_INACTIVE, openingSkipReason(eligible.copy(inSession = false)))
        assertEquals(
            OpeningSkip.PREVIOUSLY_ACTIVE,
            openingSkipReason(eligible.copy(inSession = false, suppressedUntilInactive = false)),
        )
    }

    @Test
    fun `a queued check-in in a session is not reported as in session`() {
        assertEquals(null, openingSkipReason(queued.copy(inSession = true, previouslyActive = true)))
    }

    // --- decisions from the engine ------------------------------------------------------

    @Test
    fun `a new sighting starts a breath and says so once`() {
        val engine = EnforcementEngine()
        engine.update(settings(), observation(elapsedMs = 0))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerStarted(TARGET, Trigger.OPENING, 12)),
            engine.takeDecisions(),
        )
        engine.update(settings(), observation(elapsedMs = 250))
        engine.update(settings(), observation(elapsedMs = 500))
        assertTrue(engine.takeDecisions().isEmpty())
    }

    @Test
    fun `continuing ends the breath with its age and then stays quiet while the target is in view`() {
        val settings = settings()
        val engine = EnforcementEngine()
        engine.update(settings, observation(elapsedMs = 0))
        engine.takeDecisions()

        engine.choose(Choice.Continue, settings, observation(elapsedMs = 12_000))
        engine.update(settings, observation(elapsedMs = 12_250))
        engine.update(settings, observation(elapsedMs = 12_500))

        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerEnded(TARGET, BreakerEnd.CHOICE_CONTINUE, 12_000)),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `returning within the grace is reported once as in session with absence and session age`() {
        val settings = settings()
        val engine = continuedEngine(settings, continuedAtMs = 12_000)
        engine.takeDecisions()

        engine.update(settings, observation(visible = emptySet(), elapsedMs = 18_000))
        engine.update(settings, observation(visible = emptySet(), elapsedMs = 18_250))
        engine.update(settings, observation(elapsedMs = 24_000))
        engine.update(settings, observation(elapsedMs = 24_250))
        engine.update(settings, observation(elapsedMs = 24_500))

        assertEquals(
            listOf(
                EngineDecision.TargetHidden(TARGET),
                EngineDecision.OpeningSkipped(
                    TARGET,
                    OpeningSkip.IN_SESSION,
                    absenceMs = 6_000,
                    sessionAgeMs = 12_000,
                ),
            ),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `returning after the grace starts a new breath instead of a skip`() {
        val settings = settings()
        val engine = continuedEngine(settings, continuedAtMs = 12_000)
        engine.takeDecisions()

        engine.update(settings, observation(visible = emptySet(), elapsedMs = 18_000))
        engine.update(settings, observation(elapsedMs = 78_001))

        assertEquals(
            listOf(
                EngineDecision.TargetHidden(TARGET),
                EngineDecision.BreakerStarted(TARGET, Trigger.OPENING, 12),
            ),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `a check-in is a breath start and not a skip`() {
        val settings = settings(continuousSeconds = 8)
        val engine = continuedEngine(settings, continuedAtMs = 12_000)
        engine.takeDecisions()

        engine.update(settings, observation(elapsedMs = 20_000))

        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerStarted(TARGET, Trigger.CONTINUOUS, 12)),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `leaving suppresses quietly while in view and the target is reported gone once it leaves`() {
        val settings = settings()
        val engine = EnforcementEngine()
        engine.update(settings, observation(elapsedMs = 0))
        engine.takeDecisions()

        engine.choose(Choice.Leave, settings, observation(elapsedMs = 8_000))
        engine.update(settings, observation(elapsedMs = 8_250))
        engine.update(settings, observation(elapsedMs = 8_500))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerEnded(TARGET, BreakerEnd.CHOICE_LEAVE, 8_000)),
            engine.takeDecisions(),
        )

        engine.update(settings, observation(visible = emptySet(), elapsedMs = 9_000))
        engine.update(settings, observation(elapsedMs = 20_000))
        assertEquals(
            listOf(
                EngineDecision.TargetHidden(TARGET),
                EngineDecision.BreakerStarted(TARGET, Trigger.OPENING, 12),
            ),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `a target that never leaves view after leaving leaves no hidden line`() {
        // The signature of a stale window list: the breath ended, the target stayed "visible",
        // and every later opening is suppressed. The capture shows no TargetHidden between.
        val settings = settings()
        val engine = EnforcementEngine()
        engine.update(settings, observation(elapsedMs = 0))
        engine.choose(Choice.Leave, settings, observation(elapsedMs = 8_000))
        engine.takeDecisions()

        repeat(40) { engine.update(settings, observation(elapsedMs = 9_000L + it * 250)) }

        assertTrue(engine.takeDecisions().isEmpty())
    }

    @Test
    fun `pause and redirect are named as the way the breath ended`() {
        val pause = EnforcementEngine()
        val pauseSettings = settings()
        pause.update(pauseSettings, observation(elapsedMs = 0))
        pause.choose(Choice.Pause, pauseSettings, observation(elapsedMs = 8_000))
        assertTrue(
            EngineDecision.BreakerEnded(TARGET, BreakerEnd.CHOICE_PAUSE, 8_000) in pause.takeDecisions(),
        )

        val redirect = EnforcementEngine()
        val redirectSettings = settings(redirects = listOf("alt.app"))
        redirect.update(redirectSettings, observation(elapsedMs = 0))
        redirect.choose(Choice.Redirect("alt.app"), redirectSettings, observation(elapsedMs = 8_000))
        assertTrue(
            EngineDecision.BreakerEnded(TARGET, BreakerEnd.CHOICE_REDIRECT, 8_000) in redirect.takeDecisions(),
        )
    }

    @Test
    fun `safety during a breath ends it for safety and a later clear starts a fresh one`() {
        val settings = settings()
        val engine = EnforcementEngine()
        engine.update(settings, observation(elapsedMs = 0))
        engine.takeDecisions()

        engine.update(settings, observation(safety = true, elapsedMs = 1_000))
        engine.update(settings, observation(safety = true, elapsedMs = 1_250))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerEnded(TARGET, BreakerEnd.SAFETY, 1_000)),
            engine.takeDecisions(),
        )

        engine.update(settings, observation(elapsedMs = 2_000))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerStarted(TARGET, Trigger.OPENING, 12)),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `a target that opens under safety is skipped once for safety`() {
        val settings = settings()
        val engine = EnforcementEngine()
        engine.update(settings, observation(safety = true, elapsedMs = 0))
        engine.update(settings, observation(safety = true, elapsedMs = 250))
        engine.update(settings, observation(safety = true, elapsedMs = 500))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.OpeningSkipped(TARGET, OpeningSkip.SAFETY)),
            engine.takeDecisions(),
        )

        engine.update(settings, observation(elapsedMs = 750))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerStarted(TARGET, Trigger.OPENING, 12)),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `a screen turning off ends the breath as non-interactive and not as safety`() {
        val settings = settings()
        val engine = EnforcementEngine()
        engine.update(settings, observation(elapsedMs = 0))
        engine.takeDecisions()

        // The service folds a dark screen into safetyUiVisible; the engine still names it.
        engine.update(settings, observation(interactive = false, safety = true, elapsedMs = 2_000))

        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerEnded(TARGET, BreakerEnd.NONINTERACTIVE, 2_000)),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `an active pause skips the opening as paused`() {
        val now = epoch(DEFAULT_TIME)
        val engine = EnforcementEngine(
            mapOf(TARGET to PauseState("2026-09-14", used = 1, untilEpochMs = now + 60_000)),
        )
        engine.update(settings(), observation(elapsedMs = 0))
        engine.update(settings(), observation(elapsedMs = 250))

        assertEquals(
            listOf<EngineDecision>(EngineDecision.OpeningSkipped(TARGET, OpeningSkip.PAUSED)),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `an inactive schedule skips the opening and ends a running breath as schedule`() {
        val engine = EnforcementEngine()
        val inactive = settings(schedule = WeeklySchedule())
        engine.update(inactive, observation(elapsedMs = 0))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.OpeningSkipped(TARGET, OpeningSkip.SCHEDULE_INACTIVE)),
            engine.takeDecisions(),
        )

        val window = settings(schedule = WeeklySchedule(mapOf(1 to TimeWindow(22 * 60, 23 * 60))))
        val running = EnforcementEngine()
        running.update(window, observation(at = "2026-09-14T22:30:00Z", elapsedMs = 0))
        running.takeDecisions()
        running.update(window, observation(at = "2026-09-14T23:00:00Z", elapsedMs = 1_500))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerEnded(TARGET, BreakerEnd.SCHEDULE, 1_500)),
            running.takeDecisions(),
        )
    }

    @Test
    fun `withdrawn consent skips the opening and a target removed mid-breath is deselected`() {
        val engine = EnforcementEngine()
        engine.update(settings().copy(consentAccepted = false), observation(elapsedMs = 0))
        engine.update(settings().copy(consentAccepted = false), observation(elapsedMs = 250))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.OpeningSkipped(TARGET, OpeningSkip.CONSENT_OFF)),
            engine.takeDecisions(),
        )

        val running = EnforcementEngine()
        running.update(settings(), observation(elapsedMs = 0))
        running.takeDecisions()
        running.update(settings(apps = emptyMap()), observation(elapsedMs = 1_000))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerEnded(TARGET, BreakerEnd.DESELECTED, 1_000)),
            running.takeDecisions(),
        )
    }

    @Test
    fun `a second visible target waits behind the first and says so`() {
        val settings = settings(apps = mapOf("a.app" to AppPolicy(), "b.app" to AppPolicy()))
        val both = setOf("a.app", "b.app")
        val engine = EnforcementEngine()

        engine.update(settings, observation(visible = both, elapsedMs = 0))
        engine.update(settings, observation(visible = both, elapsedMs = 250))
        assertEquals(
            listOf(
                EngineDecision.BreakerStarted("a.app", Trigger.OPENING, 12),
                EngineDecision.OpeningSkipped("b.app", OpeningSkip.OTHER_BREAKER_ACTIVE),
            ),
            engine.takeDecisions(),
        )

        engine.choose(Choice.Continue, settings, observation(visible = both, elapsedMs = 12_000))
        engine.update(settings, observation(visible = both, elapsedMs = 12_250))
        assertEquals(
            listOf(
                EngineDecision.BreakerEnded("a.app", BreakerEnd.CHOICE_CONTINUE, 12_000),
                EngineDecision.BreakerStarted("b.app", Trigger.OPENING, 12),
            ),
            engine.takeDecisions(),
        )
    }

    @Test
    fun `navigation release and Back are told apart`() {
        val settings = settings()
        val navigated = EnforcementEngine()
        navigated.update(settings, observation(elapsedMs = 0))
        navigated.takeDecisions()
        navigated.update(
            settings,
            observation(visible = setOf("launcher.app"), navigationAway = true, elapsedMs = 8_000),
        )
        assertEquals(
            listOf(
                EngineDecision.BreakerEnded(TARGET, BreakerEnd.NAV_RELEASE, 8_000),
                EngineDecision.TargetHidden(TARGET),
            ),
            navigated.takeDecisions(),
        )

        val back = EnforcementEngine()
        back.update(settings, observation(elapsedMs = 0))
        back.takeDecisions()
        back.update(settings, observation(navigationAway = true, userBack = true, elapsedMs = 8_000))
        assertEquals(
            listOf<EngineDecision>(EngineDecision.BreakerEnded(TARGET, BreakerEnd.USER_BACK, 8_000)),
            back.takeDecisions(),
        )
    }

    @Test
    fun `only selected targets are ever named`() {
        val settings = settings()
        val engine = EnforcementEngine()
        val decisions = buildList {
            engine.update(settings, observation(visible = setOf(TARGET, "stranger.app", "com.android.systemui")))
            addAll(engine.takeDecisions())
            engine.update(
                settings,
                observation(
                    visible = setOf("stranger.app", "launcher.app"),
                    navigationAway = true,
                    elapsedMs = 8_000,
                ),
            )
            addAll(engine.takeDecisions())
        }
        assertTrue(decisions.isNotEmpty())
        assertFalse(decisions.any { "stranger" in it.toString() || "launcher" in it.toString() })
        assertFalse(decisions.any { "systemui" in it.toString() })
    }

    @Test
    fun `unread decisions are capped at the most recent`() {
        val settings = settings()
        val engine = continuedEngine(settings, continuedAtMs = 12_000)
        var now = 13_000L
        repeat(50) {
            engine.update(settings, observation(visible = emptySet(), elapsedMs = now))
            now += 1_000
            engine.update(settings, observation(elapsedMs = now))
            now += 1_000
        }

        val unread = engine.takeDecisions()

        assertEquals(64, unread.size)
        assertTrue(unread.none { it is EngineDecision.BreakerEnded })
        assertTrue(unread.last() is EngineDecision.OpeningSkipped)
        assertTrue(engine.takeDecisions().isEmpty())
    }

    @Test
    fun `reading decisions does not change what the engine does`() {
        val settings = settings(redirects = listOf("alt.app"), continuousSeconds = 10)
        val script = listOf(
            Step("open") { it.update(settings, observation(elapsedMs = 0)) },
            Step("tick") { it.update(settings, observation(elapsedMs = 250)) },
            Step("early leave") { it.choose(Choice.Leave, settings, observation(elapsedMs = 4_000)) },
            Step("safety") { it.update(settings, observation(safety = true, elapsedMs = 5_000)) },
            Step("reopen") { it.update(settings, observation(elapsedMs = 6_000)) },
            Step("continue") { it.choose(Choice.Continue, settings, observation(elapsedMs = 18_000)) },
            Step("away") { it.update(settings, observation(visible = emptySet(), elapsedMs = 20_000)) },
            Step("back") { it.update(settings, observation(elapsedMs = 40_000)) },
            Step("check-in") { it.update(settings, observation(elapsedMs = 58_000)) },
            Step("redirect") {
                it.choose(Choice.Redirect("alt.app"), settings, observation(elapsedMs = 66_000))
            },
            Step("later") { it.update(settings, observation(visible = emptySet(), elapsedMs = 70_000)) },
            Step("again") { it.update(settings, observation(elapsedMs = 80_000)) },
        )
        val draining = EnforcementEngine()
        val ignoring = EnforcementEngine()

        for (step in script) {
            val withDrain = step.act(draining).also { draining.takeDecisions() }
            assertEquals(step.name, step.act(ignoring), withDrain)
        }
    }

    private class Step(val name: String, val act: (EnforcementEngine) -> EngineUpdate)

    private fun continuedEngine(settings: Settings, continuedAtMs: Long): EnforcementEngine =
        EnforcementEngine().also { engine ->
            engine.update(settings, observation(elapsedMs = 0))
            engine.choose(Choice.Continue, settings, observation(elapsedMs = continuedAtMs))
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
        userBack: Boolean = false,
        elapsedMs: Long = 0,
        at: String = DEFAULT_TIME,
    ) = Observation(
        visiblePackages = visible,
        interactive = interactive,
        safetyUiVisible = safety,
        navigationAway = navigationAway,
        userBack = userBack,
        elapsedMs = elapsedMs,
        epochMs = epoch(at),
        zoneId = UTC,
    )

    private fun epoch(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    private companion object {
        const val TARGET = "target.app"
        const val DEFAULT_TIME = "2026-09-14T12:00:00Z"
        val UTC: ZoneId = ZoneId.of("UTC")
    }
}
