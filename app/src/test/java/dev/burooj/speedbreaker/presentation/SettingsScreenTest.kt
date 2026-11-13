package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
    fun breathLengthAndScheduleAreSetInPlaceAndDetoursOpensThePicker() {
        val opened = mutableListOf<String>()
        var scheduleChanges = 0
        show(
            settings = Settings(consentAccepted = true, breathSeconds = 20),
            onScheduleTransform = { scheduleChanges++ },
            onDetours = { opened += "detours" },
        )
        // The slider is on the page itself: nothing has to be opened to reach it.
        compose.onNodeWithText("Breath length").assertExists()
        compose.onNodeWithContentDescription("20 seconds").assertExists()
        compose.onNodeWithText("Breaths happen at all hours.").assertExists()
        compose.onNodeWithText("Scheduled").performScrollTo().performClick()
        assertEquals(1, scheduleChanges)
        compose.onNodeWithText("None. Up to four places", substring = true).assertExists()
        compose.onNodeWithText("Detours").performScrollTo().performClick()
        assertEquals(listOf("detours"), opened)
    }

    @Test
    fun movingTheSliderCommitsTheNewBreathLength() {
        val committed = mutableListOf<Int>()
        show(
            settings = Settings(consentAccepted = true, breathSeconds = 20),
            onBreathChange = { committed += it },
        )
        compose.onNodeWithContentDescription("20 seconds")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.runOnIdle { assertEquals(listOf(30), committed) }
    }

    @Test
    fun choosingScheduledCreatesAWeekInPlaceAndAlwaysOnTakesItAway() {
        var settings by mutableStateOf(Settings(consentAccepted = true))
        compose.setContent {
            SpeedbreakerTheme {
                SettingsScreen(
                    settings = settings,
                    notificationsAllowed = true,
                    onBack = {},
                    onBreathChange = {},
                    onScheduleTransform = { transform ->
                        settings = settings.copy(schedule = transform(settings.schedule))
                    },
                    onDetours = {},
                    onNotifications = {},
                    onOpenAppInfo = {},
                    onHelp = {},
                    onPrivacy = {},
                )
            }
        }
        compose.onNodeWithText("Other hours").assertDoesNotExist()

        compose.onNodeWithText("Scheduled").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(startingSchedule(), settings.schedule) }
        compose.onNodeWithText("Other hours").assertExists()
        compose.onNodeWithText("Outside these hours, chosen apps open freely.").assertExists()

        compose.onNodeWithText("Always on").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, settings.schedule) }
        compose.onNodeWithText("Other hours").assertDoesNotExist()
        compose.onNodeWithText("Breaths happen at all hours.").assertExists()
    }

    @Test
    fun aScheduledDefaultShowsItsWeekRightThere() {
        show(settings = Settings(consentAccepted = true, schedule = startingSchedule()))
        compose.onNodeWithText("Outside these hours, chosen apps open freely.").assertExists()
        compose.onNodeWithText("Other hours").assertExists()
    }

    @Test
    fun anAlwaysOnDefaultKeepsTheWeekAway() {
        show()
        compose.onNodeWithText("Other hours").assertDoesNotExist()
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
        onBreathChange: (Int) -> Unit = {},
        onScheduleTransform: (ScheduleTransform) -> Unit = {},
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
                onBreathChange = onBreathChange,
                onScheduleTransform = onScheduleTransform,
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
