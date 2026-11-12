package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Everything a person sets or checks sits on one page behind Home's gear. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theDefaultsShowTheirValuesAndEachOpensItsOwnEditor() {
        val opened = mutableListOf<String>()
        show(
            settings = Settings(consentAccepted = true, breathSeconds = 20, schedule = startingSchedule()),
            onBreathLength = { opened += "breath" },
            onSchedule = { opened += "schedule" },
            onDetours = { opened += "detours" },
        )
        compose.onNodeWithText("20 seconds").assertExists()
        compose.onNodeWithText("None. Up to four places", substring = true).assertExists()
        compose.onNodeWithText("Breath length").performScrollTo().performClick()
        compose.onNodeWithText("Schedule").performScrollTo().performClick()
        compose.onNodeWithText("Detours").performScrollTo().performClick()
        assertEquals(listOf("breath", "schedule", "detours"), opened)
    }

    @Test
    fun thePhoneRowSaysWhatIsMissedWithoutItAndAsks() {
        var presses = 0
        show(phoneAllowed = false, onCallAccess = { presses++ })
        compose.onNodeWithText("rings without a ringtone", substring = true).assertExists()
        compose.onNodeWithText("make and manage phone calls", substring = true).assertExists()
        compose.onNodeWithText("Phone permission").performScrollTo().performClick()
        assertEquals(1, presses)
    }

    @Test
    fun aWithheldPromptIsExplainedInsteadOfLeavingADeadRow() {
        var presses = 0
        show(phoneAllowed = false, phoneNeedsSettings = true, onCallAccess = { presses++ })
        compose.onNodeWithText("Permissions → Phone → Allow", substring = true).assertExists()
        compose.onNodeWithText("Phone permission").performScrollTo().performClick()
        assertEquals(1, presses)
    }

    @Test
    fun anAllowedPhoneRowSaysSoAndLeadsToAppInfo() {
        var asked = 0
        var appInfo = 0
        show(onCallAccess = { asked++ }, onOpenAppInfo = { appInfo++ })
        compose.onNodeWithText("even a silent one", substring = true).assertExists()
        compose.onNodeWithText("Phone permission").performScrollTo().performClick()
        assertEquals(0, asked)
        assertEquals(1, appInfo)
    }

    @Test
    fun helpPrivacyAndSupportAreOrdinaryRows() {
        val opened = mutableListOf<String>()
        show(
            onHelp = { opened += "help" },
            onPrivacy = { opened += "privacy" },
            onSupport = { opened += "support" },
        )
        compose.onNodeWithText("Help").performScrollTo().performClick()
        compose.onNodeWithText("Privacy policy").performScrollTo().performClick()
        compose.onNodeWithText("Buy me a coffee").performScrollTo().performClick()
        assertEquals(listOf("help", "privacy", "support"), opened)
        compose.onNodeWithText("Speedbreaker 9.9.9").assertExists()
    }

    @Test
    fun aHiddenSupportLinkLeavesNoRow() {
        show(supportAvailable = false)
        compose.onNodeWithText("Buy me a coffee").assertDoesNotExist()
    }

    @Test
    fun resettingTheTotalAsksFirstAndOnlyShowsWhenThereIsOne() {
        var resets = 0
        show(canResetStats = true, onResetStats = { resets++ })
        compose.onNodeWithText("Reset breathing total").performScrollTo().performClick()
        compose.onNodeWithText("Reset your breathing total?").assertExists()
        compose.onNodeWithText("Keep it").performClick()
        assertEquals(0, resets)
        compose.onNodeWithText("Reset breathing total").performScrollTo().performClick()
        compose.onNodeWithText("Reset").performClick()
        assertEquals(1, resets)
    }

    @Test
    fun nothingToResetMeansNoResetRow() {
        show(canResetStats = false)
        compose.onNodeWithText("Reset breathing total").assertDoesNotExist()
    }

    @Test
    fun aBuildThatUpdatesItselfPutsItsGroupBetweenReliabilityAndAbout() {
        show(updatesSection = { Text("UPDATES GO HERE") })
        compose.onNodeWithText("UPDATES GO HERE").assertExists()
    }

    @Test
    fun aBuildThatDoesNotUpdateItselfSaysNothingAboutUpdates() {
        show()
        compose.onNodeWithText("Check for updates").assertDoesNotExist()
        compose.onNodeWithText("UPDATES").assertDoesNotExist()
    }

    @Test
    fun backLeavesSettings() {
        var back = 0
        show(onBack = { back++ })
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    private fun show(
        settings: Settings = Settings(consentAccepted = true),
        phoneNeedsSettings: Boolean = false,
        phoneAllowed: Boolean = true,
        supportAvailable: Boolean = true,
        canResetStats: Boolean = false,
        onBack: () -> Unit = {},
        onBreathLength: () -> Unit = {},
        onSchedule: () -> Unit = {},
        onDetours: () -> Unit = {},
        onCallAccess: () -> Unit = {},
        onOpenAppInfo: () -> Unit = {},
        onHelp: () -> Unit = {},
        onPrivacy: () -> Unit = {},
        onSupport: () -> Unit = {},
        onResetStats: () -> Unit = {},
        updatesSection: (@Composable () -> Unit)? = null,
    ) = compose.setContent {
        SpeedbreakerTheme {
            SettingsScreen(
                settings = settings,
                notificationsAllowed = true,
                onBack = onBack,
                onBreathLength = onBreathLength,
                onSchedule = onSchedule,
                onDetours = onDetours,
                onNotifications = {},
                onOpenAppInfo = onOpenAppInfo,
                onHelp = onHelp,
                onPrivacy = onPrivacy,
                onCallAccess = onCallAccess,
                phoneNeedsSettings = phoneNeedsSettings,
                phoneAllowed = phoneAllowed,
                onSupport = onSupport,
                supportAvailable = supportAvailable,
                canResetStats = canResetStats,
                onResetStats = onResetStats,
                versionName = "9.9.9",
                updatesSection = updatesSection,
            )
        }
    }
}
