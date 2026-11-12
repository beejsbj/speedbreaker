package dev.burooj.speedbreaker.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.BreathChoices
import dev.burooj.speedbreaker.model.BreathStats
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.localDayKey
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home's figures, the card of what followed the breaths, and the full page behind it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class BreathStatsScreensTest {
    @get:Rule val compose = createComposeRule()

    private val originalLocale = Locale.getDefault()
    private val now = 1_794_500_000_000L
    private val today = localDayKey(now, ZoneId.systemDefault())

    // Without a preview catalog an app that isn't installed is labelled by its package name.
    private val settings = Settings(
        consentAccepted = true,
        apps = mapOf("target.one" to AppPolicy(redirects = listOf("signal"))),
        redirects = listOf("kindle", "notes", "maps", "duolingo"),
    )
    private val stats = BreathStats(totalMs = 18 * 60_000L, totalBreaths = 94, todayKey = today, todayMs = 120_000)
    private val choices = BreathChoices(
        since = "2026-11-12",
        left = 41,
        continued = 36,
        detoured = 12,
        timeOff = 3,
        other = 2,
        detours = mapOf("kindle" to 6L, "notes" to 1L, "maps" to 4L, "duolingo" to 2L),
        days = mapOf(today to 120_000L),
    )

    @Before
    fun useEnglish() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test
    fun homeLeadsWithAStatusPillAndFiguresReadAsWords() {
        showHome()
        compose.onNodeWithText("Active · 1 app").assertExists()
        compose.onNodeWithContentDescription("Speedbreaker is active on 1 app").assertExists()
        compose.onNodeWithContentDescription("18 minutes breathed in total. 94 breaths, 2 minutes today.").assertExists()
        // The old sentence-style line is gone.
        compose.onNodeWithText("94 breaths · 2 minutes today").assertDoesNotExist()
        compose.onNodeWithText("On").assertDoesNotExist()
    }

    @Test
    fun thePillCanBePressedToReachTheApps() {
        showHome()
        compose.onNodeWithText("Active · 1 app").performClick()
        compose.onNodeWithText("APPS").assertExists()
    }

    @Test
    fun theCardCountsWhatFollowedAndOpensTheFullPage() {
        var opened = 0
        showHome(onStats = { opened++ })
        compose.onNodeWithText("Left").assertExists()
        compose.onNodeWithText("41").assertExists()
        compose.onNodeWithText("Took 15 min off").assertExists()
        compose.onNodeWithText("Ended another way").assertExists()
        compose.onNodeWithText("After a breath").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun homeNamesTheThreeMostTakenDetoursAndCountsTheRest() {
        showHome()
        compose.onNodeWithContentDescription("Detour to kindle, 6").assertExists()
        compose.onNodeWithContentDescription("Detour to maps, 4").assertExists()
        compose.onNodeWithContentDescription("Detour to duolingo, 2").assertExists()
        compose.onNodeWithContentDescription("Detour to notes, 1").assertDoesNotExist()
        // Four detours were taken: three named, one more. The untaken fifth is not counted here.
        compose.onNodeWithContentDescription("and 1 more detour taken").assertExists()
    }

    @Test
    fun withThreeOrFewerDetoursTakenThereIsNoMore() {
        showHome(choices = choices.copy(detours = mapOf("kindle" to 6L)))
        compose.onNodeWithContentDescription("Detour to kindle, 6").assertExists()
        compose.onNodeWithContentDescription("more detour", substring = true).assertDoesNotExist()
    }

    @Test
    fun countsKeptWithoutATotalStillShowTheirCard() {
        // The two records are written separately, so one can outlive the other.
        showHome(stats = BreathStats())
        compose.onNodeWithText("After a breath").assertExists()
        compose.onNodeWithText("Left").assertExists()
    }

    @Test
    fun whileBreathsAreOffTheTotalIsOneRowThatStillOpensTheFullPage() {
        var opened = 0
        showHome(protection = Protection.Off, onStats = { opened++ })
        compose.onNodeWithText("Off for now").assertExists()
        compose.onNodeWithText("After a breath").assertDoesNotExist()
        compose.onNodeWithText("18 minutes breathed so far.").assertExists()
        compose.onNodeWithText("Your breaths").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun anotherWayStaysOffHomeUntilItHappens() {
        showHome(choices = choices.copy(other = 0))
        compose.onNodeWithText("Left").assertExists()
        compose.onNodeWithText("Ended another way").assertDoesNotExist()
    }

    @Test
    fun rightAfterTheUpdateTheCardSaysCountingStartsWithTheNextBreath() {
        showHome(choices = BreathChoices())
        compose.onNodeWithText("After a breath").assertExists()
        compose.onNodeWithText("What you choose is counted from your next breath.").assertExists()
        compose.onNodeWithText("Left").assertDoesNotExist()
    }

    @Test
    fun beforeTheFirstBreathThereIsNoCardAndTheHeaderSaysWhatStartsOne() {
        showHome(stats = BreathStats(), choices = BreathChoices())
        compose.onNodeWithText("No breaths yet").assertExists()
        compose.onNodeWithText("Open target.one and your first one begins.").assertExists()
        compose.onNodeWithText("After a breath").assertDoesNotExist()
    }

    @Test
    fun blockedNotificationsAreAQuietLineUnderTheFigures() {
        showHome(notificationsAllowed = false)
        compose.onNodeWithText("Pause 15 min needs notifications. Allow them in Settings.").assertExists()
        compose.onNodeWithText("Active · 1 app").assertExists()
    }

    @Test
    fun theFullPageListsEveryEndingEveryDetourAndTheWeek() {
        showStats()
        compose.onNodeWithContentDescription("18 minutes breathed in total. 94 breaths, 2 minutes today.").assertExists()
        listOf("Left" to "41", "Continued" to "36", "Took a detour" to "12", "Took 15 min off" to "3", "Ended another way" to "2")
            .forEach { (label, count) ->
                compose.onNodeWithText(label).assertExists()
                compose.onNodeWithText(count).assertExists()
            }
        compose.onNodeWithContentDescription("kindle, taken 6 times").assertExists()
        compose.onNodeWithContentDescription("maps, taken 4 times").assertExists()
        compose.onNodeWithContentDescription("notes, taken 1 time").assertExists()
        // A detour that only one app uses, and that was never taken, is still listed.
        compose.onNodeWithContentDescription("signal, not taken yet").assertExists()
        compose.onNodeWithContentDescription("Time breathed each day.", substring = true).assertExists()
        compose.onNodeWithText("counted since 12 November 2026", substring = true).assertExists()
    }

    @Test
    fun theFullPageSaysSoWhenNothingIsCountedYetOrNoDetoursAreChosen() {
        showStats(settings = Settings(consentAccepted = true), choices = BreathChoices())
        compose.onNodeWithText("What you choose is counted from your next breath.").assertExists()
        compose.onNodeWithText("No detours are chosen. Add some under Settings → Detours.").assertExists()
        compose.onNodeWithText("They stay on this phone.", substring = true).assertExists()
        // Every ending is listed at zero, so the page keeps its shape.
        compose.onNodeWithText("Ended another way").assertExists()
    }

    @Test
    fun backLeavesTheFullPage() {
        var back = 0
        showStats(onBack = { back++ })
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    private fun showHome(
        stats: BreathStats = this.stats,
        choices: BreathChoices = this.choices,
        notificationsAllowed: Boolean = true,
        protection: Protection = Protection.On,
        onStats: () -> Unit = {},
    ) = compose.setContent {
        SpeedbreakerTheme {
            HomeScreen(
                settings = settings,
                protection = protection,
                runtimeError = null,
                notificationsAllowed = notificationsAllowed,
                onEnableAccessibility = {},
                onOpenAppInfo = {},
                onChooseApps = {},
                onSettings = {},
                onApp = {},
                nowEpochMs = now,
                stats = stats,
                choices = choices,
                onStats = onStats,
            )
        }
    }

    private fun showStats(
        settings: Settings = this.settings,
        choices: BreathChoices = this.choices,
        onBack: () -> Unit = {},
    ) = compose.setContent {
        SpeedbreakerTheme {
            StatsScreen(stats = stats, choices = choices, settings = settings, today = today, onBack = onBack)
        }
    }
}
