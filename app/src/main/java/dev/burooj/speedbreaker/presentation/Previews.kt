package dev.burooj.speedbreaker.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Trigger
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import dev.burooj.speedbreaker.presentation.components.AppCatalog
import dev.burooj.speedbreaker.presentation.components.LocalAppCatalog
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme

/*
 * Every screen in every state that matters, with fixed data. The screenshot
 * tests render this same gallery, so a preview here is also a reviewable PNG.
 */

internal data class GallerySpec(
    val name: String,
    val landscape: Boolean = false,
    val fontScale: Float = 1f,
    val dark: Boolean = false,
    val content: @Composable () -> Unit,
)

internal val Gallery: List<GallerySpec> = listOf(
    GallerySpec("overlay-01-breath") { OverlaySample(elapsedMs = 3_000) },
    GallerySpec("overlay-02-choices-waiting") { OverlaySample(elapsedMs = 11_000, breathSeconds = 20) },
    GallerySpec("overlay-03-choices-ready") { OverlaySample(elapsedMs = 21_000, breathSeconds = 20) },
    GallerySpec("overlay-04-checkin-no-redirects-no-pauses") {
        OverlaySample(
            elapsedMs = 14_000,
            trigger = Trigger.CONTINUOUS,
            redirects = emptyList(),
            pauseTokensLeft = 0,
        )
    },
    GallerySpec("overlay-05-dark", dark = true) { OverlaySample(elapsedMs = 11_000, breathSeconds = 20) },
    GallerySpec("overlay-06-landscape", landscape = true) { OverlaySample(elapsedMs = 11_000, breathSeconds = 20) },
    GallerySpec("overlay-07-large-text", fontScale = 1.6f) { OverlaySample(elapsedMs = 14_000) },
    GallerySpec("overlay-08-reduced-motion-breath") { OverlaySample(elapsedMs = 3_000, motionEnabled = false) },
    GallerySpec("setup-01-welcome") { Screen { WelcomeScreen(onAccept = {}) } },
    GallerySpec("setup-02-home-off") { Screen { HomeSample(Protection.Off, SampleSettings.copy(apps = emptyMap())) } },
    GallerySpec("setup-03-home-ready-no-apps") {
        Screen { HomeSample(Protection.On, SampleSettings.copy(apps = emptyMap()), notifications = false) }
    },
    GallerySpec("setup-04-app-picker") {
        Screen {
            AppPickerScreen(
                apps = SampleApps,
                selected = setOf(INSTAGRAM, YOUTUBE),
                onBack = {},
                onSetSelected = { _, _ -> },
            )
        }
    },
    GallerySpec("settings-01-home-on") { Screen { HomeSample(Protection.On, SampleSettings) } },
    GallerySpec("settings-02-home-needs-attention") { Screen { HomeSample(Protection.Broken, SampleSettings) } },
    GallerySpec("settings-03-defaults") {
        Screen {
            DefaultsScreen(SampleSettings, onBack = {}, onBreathChange = {}, onScheduleTransform = {}, onRedirects = {})
        }
    },
    GallerySpec("settings-04-app") {
        Screen {
            AppSettingsScreen(
                packageName = YOUTUBE,
                settings = SampleSettings,
                onBack = {},
                onPolicyTransform = {},
                onRedirects = {},
                onRemove = {},
            )
        }
    },
    GallerySpec("settings-05-redirect-picker") {
        Screen {
            RedirectPickerScreen(
                title = "Redirects",
                apps = SampleApps.filter { it.packageName !in SampleSettings.apps },
                initial = listOf(KINDLE, NOTES, DUOLINGO),
                onBack = {},
                onSave = {},
            )
        }
    },
    GallerySpec("settings-06-home-dark", dark = true) { Screen { HomeSample(Protection.On, SampleSettings) } },
    GallerySpec("settings-07-home-large-text", fontScale = 1.6f) { Screen { HomeSample(Protection.On, SampleSettings) } },
)

/* ---------- Samples ---------- */

@Composable
private fun OverlaySample(
    elapsedMs: Long,
    breathSeconds: Int = 12,
    trigger: Trigger = Trigger.OPENING,
    redirects: List<RedirectDestination> = SampleRedirects,
    pauseTokensLeft: Int = 2,
    motionEnabled: Boolean = true,
) = WithCatalog {
    BreakerOverlay(
        breaker = Breaker(INSTAGRAM, trigger, startedElapsedMs = 0, breathSeconds = breathSeconds),
        targetLabel = "Instagram",
        nowElapsedMs = elapsedMs,
        pauseTokensLeft = pauseTokensLeft,
        pauseResetLabel = "resets tomorrow",
        pauseAvailable = true,
        redirects = redirects,
        motionEnabled = motionEnabled,
        onChoice = {},
    )
}

@Composable
private fun HomeSample(protection: Protection, settings: Settings, notifications: Boolean = true) = HomeScreen(
    settings = settings,
    protection = protection,
    runtimeError = null,
    notificationsAllowed = notifications,
    onEnableAccessibility = {},
    onOpenAppInfo = {},
    onNotifications = {},
    onChooseApps = {},
    onDefaults = {},
    onApp = {},
)

@Composable
private fun Screen(content: @Composable () -> Unit) = WithCatalog {
    SpeedbreakerTheme {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

@Composable
private fun WithCatalog(content: @Composable () -> Unit) = CompositionLocalProvider(
    LocalAppCatalog provides PreviewCatalog,
    LocalStillBreath provides true,
    content = content,
)

private object PreviewCatalog : AppCatalog {
    override fun label(packageName: String): String =
        SampleApps.firstOrNull { it.packageName == packageName }?.label ?: packageName

    override fun icon(packageName: String): ImageBitmap? = null
}

private const val INSTAGRAM = "com.instagram.android"
private const val YOUTUBE = "com.google.android.youtube"
private const val REDDIT = "com.reddit.frontpage"
private const val KINDLE = "com.amazon.kindle"
private const val NOTES = "com.samsung.android.app.notes"
private const val DUOLINGO = "com.duolingo"
private const val MAPS = "com.google.android.apps.maps"

private val SampleApps = listOf(
    LaunchableApp("com.android.chrome", "Chrome"),
    LaunchableApp(DUOLINGO, "Duolingo"),
    LaunchableApp("com.google.android.gm", "Gmail"),
    LaunchableApp(INSTAGRAM, "Instagram"),
    LaunchableApp(KINDLE, "Kindle"),
    LaunchableApp(MAPS, "Maps"),
    LaunchableApp(NOTES, "Notes"),
    LaunchableApp(REDDIT, "Reddit"),
    LaunchableApp("com.spotify.music", "Spotify"),
    LaunchableApp("org.thoughtcrime.securesms", "Signal"),
    LaunchableApp(YOUTUBE, "YouTube"),
)

private val SampleRedirects = listOf(
    RedirectDestination(KINDLE, "Kindle"),
    RedirectDestination(NOTES, "Notes"),
    RedirectDestination(DUOLINGO, "Duolingo"),
    RedirectDestination(MAPS, "Maps"),
)

private val Weekdays9to5 = WeeklySchedule((1..5).associateWith { TimeWindow(9 * 60, 17 * 60) })

private val SampleSettings = Settings(
    consentAccepted = true,
    breathSeconds = 12,
    apps = mapOf(
        INSTAGRAM to AppPolicy(),
        YOUTUBE to AppPolicy(
            continuousSeconds = 20 * 60,
            schedule = WeeklySchedule(
                (1..5).associateWith { TimeWindow(18 * 60, 1 * 60) } + (6 to TimeWindow(10 * 60, 1440)),
            ),
        ),
        REDDIT to AppPolicy(continuousSeconds = null),
    ),
    redirects = listOf(KINDLE, NOTES, DUOLINGO, MAPS),
    schedule = Weekdays9to5,
)

/* ---------- Android Studio previews ---------- */

@Preview(name = "Overlay · breath", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewOverlayBreath() = Gallery[0].content()

@Preview(name = "Overlay · choices", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewOverlayChoices() = Gallery[1].content()

@Preview(name = "Overlay · landscape", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun PreviewOverlayLandscape() = Gallery[5].content()

@Preview(name = "Overlay · large text", widthDp = 412, heightDp = 915, fontScale = 1.6f, showBackground = true)
@Composable
private fun PreviewOverlayLargeText() = Gallery[6].content()

@Preview(name = "Welcome", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewWelcome() = Gallery[8].content()

@Preview(name = "Home · on", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewHomeOn() = Gallery[12].content()

@Preview(name = "Home · off", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewHomeOff() = Gallery[9].content()

@Preview(name = "Defaults", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewDefaults() = Gallery[14].content()

@Preview(name = "App", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewApp() = Gallery[15].content()

@Preview(name = "Redirect picker", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewRedirectPicker() = Gallery[16].content()
