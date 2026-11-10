package dev.burooj.speedbreaker.observation

import dev.burooj.speedbreaker.observation.SetupReturn.Waiting
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupReturnTest {
    @After
    fun clear() = SetupReturn.cancel()

    @Test
    fun `a return is taken once, for the thing that was asked for`() {
        SetupReturn.expect(Waiting.ServiceOn, nowElapsedMs = 1_000)
        assertFalse(SetupReturn.take(Waiting.PhoneAllowed, 2_000))
        assertTrue(SetupReturn.take(Waiting.ServiceOn, 2_000))
        // A later reconnect of the service must not open the app again.
        assertFalse(SetupReturn.take(Waiting.ServiceOn, 3_000))
    }

    @Test
    fun `nothing is taken when the app never sent anyone to settings`() {
        assertFalse(SetupReturn.take(Waiting.ServiceOn, 1_000))
        assertFalse(SetupReturn.isWaitingFor(Waiting.PhoneAllowed, 1_000))
    }

    @Test
    fun `coming back on their own cancels the return`() {
        SetupReturn.expect(Waiting.PhoneAllowed, 1_000)
        assertTrue(SetupReturn.isWaitingFor(Waiting.PhoneAllowed, 1_500))
        SetupReturn.cancel()
        assertFalse(SetupReturn.take(Waiting.PhoneAllowed, 2_000))
    }

    @Test
    fun `a return lapses after five minutes`() {
        SetupReturn.expect(Waiting.ServiceOn, 0)
        assertTrue(SetupReturn.isWaitingFor(Waiting.ServiceOn, 5 * 60_000L))
        assertFalse(SetupReturn.take(Waiting.ServiceOn, 5 * 60_000L + 1))
    }

    @Test
    fun `a newer request replaces an older one`() {
        SetupReturn.expect(Waiting.ServiceOn, 0)
        SetupReturn.expect(Waiting.PhoneAllowed, 10)
        assertFalse(SetupReturn.take(Waiting.ServiceOn, 20))
        assertTrue(SetupReturn.take(Waiting.PhoneAllowed, 20))
    }
}
