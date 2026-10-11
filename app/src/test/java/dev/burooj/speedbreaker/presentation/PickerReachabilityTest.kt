package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.burooj.speedbreaker.presentation.components.AppCatalog
import dev.burooj.speedbreaker.presentation.components.LocalAppCatalog
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w915dp-h300dp-land-xxhdpi")
class PickerReachabilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val apps = (1..30).map { LaunchableApp("dev.test.app$it", "App $it") }

    @Test
    fun appSearchResultCanBeReachedAndCheckedWithLargeTextInShortLandscape() {
        var wentBack = false
        setPickerContent {
            var selected by remember { mutableStateOf(emptySet<String>()) }
            AppPickerScreen(
                apps = apps,
                selected = selected,
                onBack = { wentBack = true },
                onSetSelected = { app, checked ->
                    selected = if (checked) selected + app.packageName else selected - app.packageName
                },
            )
        }

        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText(apps.last().label) and isToggleable())
        compose.onNode(hasText(apps.last().label) and isToggleable()).assertIsDisplayed()
        list.performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput(apps.last().label)
        list.performScrollToNode(hasText(apps.last().label) and isToggleable())
        compose.onNode(hasText(apps.last().label) and isToggleable())
            .assertIsDisplayed().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun redirectResultAndFixedFooterStayUsableWithLargeTextInShortLandscape() {
        val initial = apps.take(3).map { it.packageName }
        var saved: List<String>? = null
        var wentBack = false
        setPickerContent {
            RedirectPickerScreen(
                title = "Redirects for this app",
                apps = apps,
                initial = initial,
                onBack = { wentBack = true },
                onSave = { saved = it },
            )
        }

        compose.onNodeWithText("Clear").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Save").assertIsDisplayed().assertIsEnabled()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText(apps.last().label) and isToggleable())
        compose.onNode(hasText(apps.last().label) and isToggleable()).assertIsDisplayed()
        list.performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput(apps.last().packageName)
        list.performScrollToNode(hasText(apps.last().label) and isToggleable())
        compose.onNode(hasText(apps.last().label) and isToggleable())
            .assertIsDisplayed().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Save").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(initial + apps.last().packageName, saved) }
        compose.onNodeWithText("Clear").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), saved) }
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    private fun setPickerContent(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.6f),
                LocalAppCatalog provides object : AppCatalog {
                    override fun label(packageName: String): String = packageName
                    override fun icon(packageName: String): ImageBitmap? = null
                },
            ) {
                SpeedbreakerTheme(content = content)
            }
        }
    }
}
