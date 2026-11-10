package dev.burooj.speedbreaker.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.BreathStats
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What a new person meets before the first breath: two welcome pages, then three steps on Home. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class FirstRunTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun consentIsOnlyOfferedAfterTheDisclosureIsShown() {
        var accepted = 0
        var declined = 0
        compose.setContent {
            SpeedbreakerTheme { WelcomeScreen(onDecline = { declined++ }, onAccept = { accepted++ }) }
        }
        compose.onNodeWithText("Make the next tap intentional.").assertIsDisplayed()
        compose.onNodeWithText("I understand — continue").assertDoesNotExist()
        compose.onNodeWithText("Not now — close").assertDoesNotExist()

        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Before you turn it on").assertIsDisplayed()
        compose.onNodeWithText("WHAT IT SEES").assertIsDisplayed()
        assertEquals(0, accepted)

        compose.onNodeWithText("Not now — close").performScrollTo().performClick()
        assertEquals(1, declined)
        assertEquals(0, accepted)
        compose.onNodeWithText("I understand — continue").performScrollTo().performClick()
        assertEquals(1, accepted)
    }

    @Test
    fun aFreshInstallOpensOnStepOneAndNamesTheServiceToFind() {
        show(Protection.Off)
        compose.onNodeWithText("Three steps to start").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 1 of 3, next. Turn on Speedbreaker in Accessibility").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 2 of 3, later. Allow Phone permission").assertExists()
        compose.onNodeWithText("Speedbreaker attention pauses", substring = true).assertExists()
        compose.onNodeWithText("Turn on in Accessibility").assertExists()
        // Later steps stay closed until their turn, and nothing reads as a fault.
        compose.onNodeWithText("make and manage phone calls", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Off for now").assertDoesNotExist()
    }

    @Test
    fun choosingAppsIsOfferedOnceWhileItIsTheOpenStep() {
        show(Protection.On)
        compose.onNodeWithText("One step left").assertIsDisplayed()
        compose.onNodeWithText("Choose apps").assertIsDisplayed() // exactly one: the step's button
        compose.onNodeWithText("APPS").assertDoesNotExist()
    }

    @Test
    fun appsCanBeChosenEarlyFromTheirOwnRow() {
        show(Protection.CallPermission)
        compose.onNodeWithText("APPS").assertExists()
        compose.onNodeWithText("Choose apps").assertExists()
    }

    @Test
    fun thePhoneStepGivesItsReasonAndAsks() {
        var presses = 0
        show(Protection.CallPermission, onCallAccess = { presses++ })
        compose.onNodeWithText("Two steps left").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 1 of 3, done. Turn on Speedbreaker in Accessibility").assertIsDisplayed()
        compose.onNodeWithText("make and manage phone calls", substring = true).assertExists()
        compose.onNodeWithText("Open app info").assertDoesNotExist()
        compose.onNodeWithText("Allow Phone permission").performScrollTo().performClick()
        assertEquals(1, presses)
    }

    @Test
    fun aWithheldPromptIsExplainedInsteadOfLeavingADeadButton() {
        var presses = 0
        show(Protection.CallPermission, phoneNeedsSettings = true, onCallAccess = { presses++ })
        compose.onNodeWithText("Permissions → Phone → Allow", substring = true).assertExists()
        compose.onNodeWithText("Allow Phone permission").assertDoesNotExist()
        compose.onNodeWithText("Open app info").performScrollTo().performClick()
        assertEquals(1, presses)
    }

    @Test
    fun afterAFirstBreathTheSameStatesReadAsRepairsNotSetup() {
        show(
            Protection.Off,
            settings = Settings(consentAccepted = true, apps = mapOf("test.app" to AppPolicy())),
            stats = BreathStats(totalMs = 60_000, totalBreaths = 5),
        )
        compose.onNodeWithText("Off for now").assertIsDisplayed()
        compose.onNodeWithText("Three steps to start").assertDoesNotExist()
        compose.onNodeWithText("Turn on in Accessibility").assertExists()
    }

    private fun show(
        protection: Protection,
        settings: Settings = Settings(consentAccepted = true),
        stats: BreathStats = BreathStats(),
        phoneNeedsSettings: Boolean = false,
        onCallAccess: () -> Unit = {},
    ) = compose.setContent {
        SpeedbreakerTheme {
            HomeScreen(
                settings = settings,
                protection = protection,
                runtimeError = null,
                notificationsAllowed = true,
                onEnableAccessibility = {},
                onOpenAppInfo = {},
                onNotifications = {},
                onChooseApps = {},
                onDefaults = {},
                onApp = {},
                onCallAccess = onCallAccess,
                phoneNeedsSettings = phoneNeedsSettings,
                stats = stats,
            )
        }
    }
}
