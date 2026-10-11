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
}
