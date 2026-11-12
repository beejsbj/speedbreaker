package dev.burooj.speedbreaker.observation

import dev.burooj.speedbreaker.enforcement.BreakerEnd
import dev.burooj.speedbreaker.enforcement.EngineDecision
import dev.burooj.speedbreaker.enforcement.Trigger
import dev.burooj.speedbreaker.model.BreathEnding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BreathCounterTest {
    private fun started(seconds: Int) = EngineDecision.BreakerStarted("t", Trigger.OPENING, seconds)
    private fun ended(ageMs: Long, reason: BreakerEnd = BreakerEnd.CHOICE_CONTINUE) =
        EngineDecision.BreakerEnded("t", reason, ageMs)

    @Test
    fun anyEndPath_countsOnceWithTheBreathsOwnLength() {
        for (reason in BreakerEnd.entries) {
            val counter = BreathCounter()
            assertNull(counter.consume(started(10), 12))
            assertEquals(reason.name, 9_000L, counter.consume(ended(9_000, reason), 12))
        }
    }

    @Test
    fun shownTimeIsCappedAtTheBreathLengthStartedWith() {
        val counter = BreathCounter()
        counter.consume(started(10), fallbackBreathSeconds = 30)
        assertEquals(10_000L, counter.consume(ended(25_000), fallbackBreathSeconds = 30))
    }

    @Test
    fun briefRestartsDoNotCount() {
        val counter = BreathCounter()
        counter.consume(started(12), 12)
        assertNull(counter.consume(ended(250, BreakerEnd.SAFETY), 12))
    }

    @Test
    fun anEndWithoutAKnownStart_usesTheFallbackLength() {
        assertEquals(12_000L, BreathCounter().consume(ended(40_000), 12))
    }

    @Test
    fun aSecondEndDoesNotReuseTheFinishedBreath() {
        val counter = BreathCounter()
        counter.consume(started(8), 12)
        counter.consume(ended(8_000), 12)
        assertEquals(12_000L, counter.consume(ended(40_000), 12))
    }

    @Test
    fun otherDecisionsAreIgnored() {
        assertNull(BreathCounter().consume(EngineDecision.TargetHidden("t"), 12))
    }

    @Test
    fun leavingByButtonOrByNavigatingAwayIsLeft_andWhatThePersonDidNotDoIsOther() {
        assertEquals(BreathEnding.Left, BreakerEnd.CHOICE_LEAVE.asEnding())
        // Home, Recents or another app, and Back, once the first eight seconds allow them.
        assertEquals(BreathEnding.Left, BreakerEnd.NAV_RELEASE.asEnding())
        assertEquals(BreathEnding.Left, BreakerEnd.USER_BACK.asEnding())
        assertEquals(BreathEnding.Continued, BreakerEnd.CHOICE_CONTINUE.asEnding())
        assertEquals(BreathEnding.Detoured, BreakerEnd.CHOICE_REDIRECT.asEnding())
        assertEquals(BreathEnding.TimeOff, BreakerEnd.CHOICE_PAUSE.asEnding())
        val unchosen = BreakerEnd.entries - setOf(
            BreakerEnd.CHOICE_LEAVE,
            BreakerEnd.NAV_RELEASE,
            BreakerEnd.USER_BACK,
            BreakerEnd.CHOICE_CONTINUE,
            BreakerEnd.CHOICE_REDIRECT,
            BreakerEnd.CHOICE_PAUSE,
        )
        unchosen.forEach { assertEquals(BreathEnding.Other, it.asEnding()) }
    }
}
