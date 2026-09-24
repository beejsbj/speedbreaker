package dev.burooj.speedbreaker.presentation

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Headless screenshot harness using Robolectric + Roborazzi.
 * Renders Compose UI to PNG files without an emulator or device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotHarnessTest {

    @Test
    fun captureHarnessScreenshot() {
        captureScreen("_harness") {
            MaterialTheme {
                Surface(
                    modifier = Modifier.background(Color.White),
                ) {
                    Text("harness")
                }
            }
        }
    }
}

/**
 * Captures a Compose composable to a PNG file using Roborazzi.
 *
 * @param name The filename (without .png extension)
 * @param content The Composable to render
 */
internal fun captureScreen(
    name: String,
    content: @Composable () -> Unit,
) {
    val screenshotsDir = File("../docs/design/screenshots")
    screenshotsDir.mkdirs()

    val filePath = screenshotsDir.resolve("$name.png").absolutePath

    captureRoboImage(filePath = filePath) {
        content()
    }
}
