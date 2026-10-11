package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Trigger
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class BreakerUnlockTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsUnlockWithoutWaitingForMotion() = checkUnlock(breathSeconds = 12)

    @Test fun minimumBreathMakesContinueAvailableAtUnlock() = checkUnlock(breathSeconds = 8)

    private fun checkUnlock(breathSeconds: Int) {
        val now = mutableStateOf(7_999L)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BreakerOverlay(
                breaker = Breaker("test.target", Trigger.OPENING, 0, breathSeconds),
                targetLabel = "Test target",
                nowElapsedMs = now.value,
                pauseTokensLeft = 2,
                pauseResetLabel = "resets tomorrow",
                pauseAvailable = true,
                redirects = listOf("Clock", "Calendar", "Camera", "Notes").map {
                    RedirectDestination("test.$it", it)
                },
                motionEnabled = true,
                onChoice = {},
            )
        }
        compose.onNodeWithText("Leave").assertDoesNotExist()
        compose.onNodeWithText("Pause 15 min · 2 left").assertDoesNotExist()
        compose.runOnUiThread { now.value = 8_000L }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Leave").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Pause 15 min · 2 left").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Clock").assertIsDisplayed().assertIsEnabled()
        if (breathSeconds == 8) {
            compose.onNodeWithText("Continue").assertIsDisplayed().assertIsEnabled()
        } else {
            compose.onNodeWithText("Continue in 4s").assertIsNotEnabled()
            compose.runOnUiThread { now.value = 12_000L }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Continue").assertIsDisplayed().assertIsEnabled()
        }
    }
}
