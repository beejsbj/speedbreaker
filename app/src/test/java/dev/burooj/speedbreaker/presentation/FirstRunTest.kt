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

/** What a new person meets before the first breath: two welcome pages, then two steps on Home. */
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
        compose.onNodeWithText("Two steps to start").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 1 of 2, next. Turn on Speedbreaker in Accessibility").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 2 of 2, later. Choose your apps").assertExists()
        compose.onNodeWithText("find “Speedbreaker”", substring = true).assertExists()
        compose.onNodeWithText("Turn on in Accessibility").assertExists()
        // The later step stays closed until its turn, and nothing reads as a fault.
        compose.onNodeWithText("Pick the apps that pull at you", substring = true).assertDoesNotExist()
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
        show(Protection.Off)
        compose.onNodeWithText("APPS").assertExists()
        compose.onNodeWithText("Choose apps").assertExists()
    }

    @Test
    fun phonePermissionIsNotASetupStepAndBreathsDoNotWaitForIt() {
        show(Protection.On, phoneAllowed = false)
        compose.onNodeWithText("One step left").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 1 of 2, done. Turn on Speedbreaker in Accessibility").assertIsDisplayed()
        compose.onNodeWithText("Breaths are off", substring = true).assertDoesNotExist()
    }

    @Test
    fun thePhoneRowSaysWhatIsMissedWithoutItAndAsks() {
        var presses = 0
        show(Protection.On, phoneAllowed = false, onCallAccess = { presses++ })
        compose.onNodeWithText("rings silently or on vibrate can stay hidden", substring = true).assertExists()
        compose.onNodeWithText("make and manage phone calls", substring = true).assertExists()
        compose.onNodeWithText("Phone permission").performScrollTo().performClick()
        assertEquals(1, presses)
    }

    @Test
    fun aWithheldPromptIsExplainedInsteadOfLeavingADeadRow() {
        var presses = 0
        show(Protection.On, phoneAllowed = false, phoneNeedsSettings = true, onCallAccess = { presses++ })
        compose.onNodeWithText("Permissions → Phone → Allow", substring = true).assertExists()
        compose.onNodeWithText("Phone permission").performScrollTo().performClick()
        assertEquals(1, presses)
    }

    @Test
    fun anAllowedPhoneRowSaysSoAndLeadsToAppInfo() {
        var asked = 0
        var appInfo = 0
        show(Protection.On, onCallAccess = { asked++ }, onOpenAppInfo = { appInfo++ })
        compose.onNodeWithText("even a silent one", substring = true).assertExists()
        compose.onNodeWithText("Phone permission").performScrollTo().performClick()
        assertEquals(0, asked)
        assertEquals(1, appInfo)
    }

    @Test
    fun afterAFirstBreathTheSameStatesReadAsRepairsNotSetup() {
        show(
            Protection.Off,
            settings = Settings(consentAccepted = true, apps = mapOf("test.app" to AppPolicy())),
            stats = BreathStats(totalMs = 60_000, totalBreaths = 5),
        )
        compose.onNodeWithText("Off for now").assertIsDisplayed()
        compose.onNodeWithText("Two steps to start").assertDoesNotExist()
        compose.onNodeWithText("Turn on in Accessibility").assertExists()
    }

    private fun show(
        protection: Protection,
        settings: Settings = Settings(consentAccepted = true),
        stats: BreathStats = BreathStats(),
        phoneNeedsSettings: Boolean = false,
        phoneAllowed: Boolean = true,
        onCallAccess: () -> Unit = {},
        onOpenAppInfo: () -> Unit = {},
    ) = compose.setContent {
        SpeedbreakerTheme {
            HomeScreen(
                settings = settings,
                protection = protection,
                runtimeError = null,
                notificationsAllowed = true,
                onEnableAccessibility = {},
                onOpenAppInfo = onOpenAppInfo,
                onNotifications = {},
                onChooseApps = {},
                onDefaults = {},
                onApp = {},
                onCallAccess = onCallAccess,
                phoneNeedsSettings = phoneNeedsSettings,
                phoneAllowed = phoneAllowed,
                stats = stats,
            )
        }
    }
}
