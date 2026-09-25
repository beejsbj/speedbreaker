package dev.burooj.speedbreaker.presentation

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every [Gallery] entry to docs/design/screenshots for review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class GalleryScreenshotTest {

    @Test
    fun renderGallery() {
        Gallery.forEach { spec ->
            val size = if (spec.landscape) "w915dp-h412dp-land" else "w412dp-h915dp-port"
            val night = if (spec.dark) "night" else "notnight"
            RuntimeEnvironment.setQualifiers("$size-$night-xxhdpi")
            RuntimeEnvironment.setFontScale(spec.fontScale)
            captureScreen(spec.name, spec.content)
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
