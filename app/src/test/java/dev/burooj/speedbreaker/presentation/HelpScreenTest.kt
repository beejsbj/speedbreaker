package dev.burooj.speedbreaker.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Somewhere to go when stuck: readable offline, and it names the version. Settings leads to it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class HelpScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun helpAnswersTheStuckQuestionsAndLeadsOnward() {
        var guide = 0
        var privacy = 0
        var back = 0
        compose.setContent {
            SpeedbreakerTheme {
                HelpScreen(onBack = { back++ }, onGuide = { guide++ }, onPrivacy = { privacy++ }, versionName = "9.9.9")
            }
        }
        compose.onNodeWithText("IF A BREATH DOESN’T APPEAR").assertExists()
        compose.onNodeWithText("SWITCH IT OFF").assertExists()
        compose.onNodeWithText("Turn off “Speedbreaker”", substring = true).assertExists()
        compose.onNodeWithText("Speedbreaker 9.9.9").assertExists()
        // Back first: it sits at the top and scrolls out of reach once the rows below are shown.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Setup guide and reporting a problem").performScrollTo().performClick()
        compose.onNodeWithText("Privacy policy").performScrollTo().performClick()
        assertEquals(listOf(1, 1, 1), listOf(guide, privacy, back))
    }
}
