package dev.burooj.speedbreaker.observation

import dev.burooj.speedbreaker.enforcement.BreakerEnd
import dev.burooj.speedbreaker.enforcement.EngineDecision
import dev.burooj.speedbreaker.enforcement.OpeningSkip
import dev.burooj.speedbreaker.enforcement.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagTest {
    @Test fun decisionsPrintReasonCodesAndNumbersOnly() {
        assertEquals(
            "breaker started pkg=target.app trigger=OPENING breathSec=12",
            EngineDecision.BreakerStarted("target.app", Trigger.OPENING, 12).logLine(),
        )
        assertEquals(
            "breaker ended pkg=target.app reason=NAV_RELEASE ageMs=8000",
            EngineDecision.BreakerEnded("target.app", BreakerEnd.NAV_RELEASE, 8_000).logLine(),
        )
        assertEquals(
            "opening skipped pkg=target.app reason=IN_SESSION absenceMs=12000 sessionAgeMs=18000",
            EngineDecision.OpeningSkipped("target.app", OpeningSkip.IN_SESSION, 12_000, 18_000).logLine(),
        )
        assertEquals(
            "opening skipped pkg=target.app reason=SAFETY",
            EngineDecision.OpeningSkipped("target.app", OpeningSkip.SAFETY).logLine(),
        )
        assertEquals("target hidden pkg=target.app", EngineDecision.TargetHidden("target.app").logLine())
    }

    @Test fun onlySafetyDecisionsAskForTheSafetyDetail() {
        assertTrue(EngineDecision.BreakerEnded("t", BreakerEnd.SAFETY, 0).involvesSafety())
        assertTrue(EngineDecision.BreakerEnded("t", BreakerEnd.NONINTERACTIVE, 0).involvesSafety())
        assertTrue(EngineDecision.OpeningSkipped("t", OpeningSkip.SAFETY).involvesSafety())
        assertTrue(EngineDecision.OpeningSkipped("t", OpeningSkip.NONINTERACTIVE).involvesSafety())
        assertFalse(EngineDecision.BreakerEnded("t", BreakerEnd.CHOICE_LEAVE, 0).involvesSafety())
        assertFalse(EngineDecision.OpeningSkipped("t", OpeningSkip.IN_SESSION).involvesSafety())
        assertFalse(EngineDecision.BreakerStarted("t", Trigger.OPENING, 12).involvesSafety())
        assertFalse(EngineDecision.TargetHidden("t").involvesSafety())
    }

    @Test fun safetyDetailCarriesStatusBooleansAndTheAudioModeNumber() {
        val detail = SafetyDetail(
            callSafety = CallSafety.Status.PermissionRequired,
            telecomInCall = null,
            audioMode = 0,
            keyguardLocked = false,
            yieldPackageVisible = true,
        )
        assertEquals(
            "safety detail interactive=true callSafety=PermissionRequired telecomInCall=unknown " +
                "audioMode=0 keyguardLocked=false yieldPackageVisible=true",
            detail.logLine(interactive = true),
        )
    }

    @Test fun focusResultsAreNamedAndUnknownOnesKeepTheirNumber() {
        assertEquals("FAILED", focusResultName(0))
        assertEquals("GRANTED", focusResultName(1))
        assertEquals("DELAYED", focusResultName(2))
        assertEquals("7", focusResultName(7))
        assertEquals("NO_AUDIO_MANAGER", focusResultName(null))
    }

    @Test fun loggingWithoutAnAndroidLogDoesNotThrow() {
        // A plain JVM test has no android.util.Log; diagnostics must never break the caller.
        Diag.i("probe")
        Diag.log(EngineDecision.TargetHidden("target.app"))
    }
}
