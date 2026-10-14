package dev.burooj.speedbreaker.observation

import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.*
import org.junit.Test

class WindowTypeHistoryTest {
    @Test fun removedRecentsWindowStillAttributesItsQueuedNavigationEvent() {
        val history = WindowTypeHistory()
        history.record(mapOf(7 to AccessibilityWindowInfo.TYPE_APPLICATION), 100)
        history.record(mapOf(8 to AccessibilityWindowInfo.TYPE_SYSTEM), 200)
        assertTrue(history.isApplicationTransition(7, 200))
        assertFalse(history.isApplicationTransition(8, 200))
    }

    @Test fun expiredWindowIdentityCannotClassifyANewEvent() {
        val history = WindowTypeHistory()
        history.record(mapOf(7 to AccessibilityWindowInfo.TYPE_APPLICATION), 100)
        assertTrue(history.isApplicationTransition(7, 850))
        assertFalse(history.isApplicationTransition(7, 851))
        history.record(emptyMap(), 851)
        assertFalse(history.isApplicationTransition(7, 851))
    }

    @Test fun currentSystemWindowOverridesAnOlderApplicationType() {
        val history = WindowTypeHistory()
        history.record(mapOf(7 to AccessibilityWindowInfo.TYPE_APPLICATION), 100)
        history.record(mapOf(7 to AccessibilityWindowInfo.TYPE_SYSTEM), 200)
        assertFalse(history.isApplicationTransition(7, 200))
    }

    @Test fun unknownAndUndefinedWindowIdsNeverInventApplicationNavigation() {
        val history = WindowTypeHistory()
        history.record(mapOf(-1 to AccessibilityWindowInfo.TYPE_APPLICATION), 100)
        assertFalse(history.isApplicationTransition(-1, 100))
        assertFalse(history.isApplicationTransition(7, 100))
    }

    @Test fun systemUiPopupEventDoesNotLatchNavigationButRecentsDoes() {
        val history = WindowTypeHistory()
        // Window 7 is Recents (application type); window 8 is a volume panel or heads-up.
        history.record(
            mapOf(
                7 to AccessibilityWindowInfo.TYPE_APPLICATION,
                8 to AccessibilityWindowInfo.TYPE_SYSTEM,
            ),
            100,
        )
        fun latches(windowId: Int) = navigationEvent(
            hasBreaker = true,
            windowTransition = true,
            isNavigationPackage = { true },
            applicationTransition = { history.isApplicationTransition(windowId, 200) },
        )
        assertFalse(latches(8))
        assertTrue(latches(7))
    }

    @Test fun navigationEventsNeedABreathAndAWindowTransition() {
        var typeLookups = 0
        fun latches(hasBreaker: Boolean, transition: Boolean) = navigationEvent(
            hasBreaker, transition, { true }, { typeLookups++; true },
        )
        assertFalse(latches(hasBreaker = false, transition = true))
        assertFalse(latches(hasBreaker = true, transition = false))
        assertEquals(0, typeLookups)
        assertTrue(latches(hasBreaker = true, transition = true))
    }
}
