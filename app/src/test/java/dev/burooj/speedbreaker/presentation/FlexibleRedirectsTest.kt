package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Choice
import dev.burooj.speedbreaker.enforcement.Trigger
import dev.burooj.speedbreaker.presentation.components.AppCatalog
import dev.burooj.speedbreaker.presentation.components.LocalAppCatalog
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class FlexibleRedirectsTest {
    @get:Rule val compose = createComposeRule()
    private val apps = (1..5).map { LaunchableApp("alt.$it", "Alternative $it") }

    @Test fun pickerSavesZero() = checkPicker(0)
    @Test fun pickerSavesOne() = checkPicker(1)
    @Test fun pickerSavesTwo() = checkPicker(2)
    @Test fun pickerSavesThree() = checkPicker(3)
    @Test fun pickerSavesFour() = checkPicker(4)

    private fun checkPicker(count: Int) {
        var saved: List<String>? = null
        compose.setContent {
            SpeedbreakerTheme {
                RedirectPickerScreen("Redirects", apps, emptyList(), onBack = {}, onSave = { saved = it })
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        for (app in apps.take(count)) {
            list.performScrollToNode(hasText(app.label) and isToggleable())
            compose.onNode(hasText(app.label) and isToggleable()).performClick()
        }
        list.performScrollToNode(hasText(apps.last().label) and isToggleable())
        if (count == 4) compose.onNode(hasText(apps.last().label) and isToggleable()).assertIsNotEnabled()
        else compose.onNode(hasText(apps.last().label) and isToggleable()).assertIsEnabled()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(apps.take(count).map { it.packageName }, saved) }
    }

    @Test
    fun removingOneAtTheCapAllowsAReplacement() {
        var saved: List<String>? = null
        compose.setContent {
            SpeedbreakerTheme {
                RedirectPickerScreen("Redirects", apps, apps.take(4).map { it.packageName }, onBack = {}, onSave = { saved = it })
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText(apps.last().label) and isToggleable())
        compose.onNode(hasText(apps.last().label) and isToggleable()).assertIsNotEnabled()
        list.performScrollToNode(hasText(apps.first().label) and isToggleable())
        compose.onNode(hasText(apps.first().label) and isToggleable()).performClick().assertIsOff()
        list.performScrollToNode(hasText(apps.last().label) and isToggleable())
        compose.onNode(hasText(apps.last().label) and isToggleable()).assertIsEnabled().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(apps.drop(1).map { it.packageName }, saved) }
    }

    @Test fun overlayShowsZero() = checkOverlay(0)
    @Test fun overlayShowsOne() = checkOverlay(1)
    @Test fun overlayShowsTwo() = checkOverlay(2)
    @Test fun overlayShowsThree() = checkOverlay(3)
    @Test fun overlayShowsFour() = checkOverlay(4)

    private fun checkOverlay(count: Int) {
        val destinations = apps.take(count)
        val iconsLoaded = mutableSetOf<String>()
        val choices = mutableListOf<Choice>()
        compose.setContent {
            CompositionLocalProvider(LocalAppCatalog provides object : AppCatalog {
                override fun label(packageName: String): String = apps.first { it.packageName == packageName }.label
                override fun icon(packageName: String): ImageBitmap {
                    iconsLoaded += packageName
                    return ImageBitmap(16, 16)
                }
            }) {
                BreakerOverlay(
                    breaker = Breaker("target", Trigger.OPENING, 0, 12),
                    targetLabel = "Target",
                    nowElapsedMs = 8_000,
                    pauseTokensLeft = 2,
                    pauseResetLabel = "resets tomorrow",
                    pauseAvailable = true,
                    redirects = destinations.map { RedirectDestination(it.packageName, it.label) },
                    motionEnabled = false,
                    onChoice = { choices += it },
                )
            }
        }
        compose.onNodeWithText("Leave").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Continue in 4s").assertIsNotEnabled()
        if (count == 0) compose.onNodeWithText("Or go to").assertDoesNotExist()
        else compose.onNodeWithText("Or go to").assertIsDisplayed()
        for (app in destinations) {
            compose.onNodeWithText(app.label).assertIsDisplayed().assertIsEnabled().performClick()
        }
        for (app in apps.drop(count)) compose.onNodeWithText(app.label).assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(iconsLoaded.containsAll(destinations.map { it.packageName }))
            assertEquals(destinations.map { Choice.Redirect(it.packageName) }, choices)
        }
    }
}
