package dev.burooj.speedbreaker.presentation

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.text.format.DateFormat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.burooj.speedbreaker.MainActivity
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.observation.PauseNotifications
import dev.burooj.speedbreaker.persistence.SpeedbreakerRepository
import dev.burooj.speedbreaker.presentation.components.AppCatalog
import dev.burooj.speedbreaker.presentation.components.LocalAppCatalog
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.LocalDate
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class PauseControlsTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val firstApp = "test.first"
    private val secondApp = "test.second"
    private val catalog = object : AppCatalog {
        override fun label(packageName: String): String = when (packageName) {
            firstApp -> "First app"
            secondApp -> "Second app"
            else -> "Expired app"
        }
        override fun icon(packageName: String): ImageBitmap? = null
    }

    @Test
    fun dismissedNotificationStaysDismissedAndPauseCanBeEndedFromHome() {
        val now = System.currentTimeMillis()
        val pause = PauseState(LocalDate.now().toString(), used = 1, untilEpochMs = now + 900_000)
        val repository = seed(mapOf(firstApp to pause))
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = PauseNotifications(context)
        assertTrue(notifications.isAvailable())
        notifications.reconcile(repository.pauses.value, now)
        assertEquals(1, manager.activeNotifications.size)

        // Model an individual Android 14 swipe, without changing the helper's successful-post cache.
        manager.cancel(manager.activeNotifications.single().id)
        notifications.reconcile(repository.pauses.value, now + 1_000)
        assertTrue(manager.activeNotifications.isEmpty())
        assertEquals(pause, repository.pauses.value[firstApp])

        showApp()
        compose.onNodeWithText("ACTIVE PAUSES").assertIsDisplayed()
        compose.onNodeWithText("Paused until ${DateFormat.getTimeFormat(context).format(Date(pause.untilEpochMs))}")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("End pause for First app")
            .assertIsDisplayed().assertIsEnabled().performClick()
        awaitPauseEnded(repository, firstApp)
        compose.onNodeWithContentDescription("End pause for First app").assertDoesNotExist()
        compose.onNodeWithText("ACTIVE PAUSES").assertDoesNotExist()
        assertEquals(pause.copy(untilEpochMs = 0), repository.pauses.value[firstApp])
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun endingOnePausePreservesTheOtherAndNewDurableStateAppearsLive() {
        val now = System.currentTimeMillis()
        val first = PauseState(LocalDate.now().toString(), used = 1, untilEpochMs = now + 600_000)
        val second = PauseState(LocalDate.now().toString(), used = 2, untilEpochMs = now + 900_000)
        val repository = seed(mapOf(firstApp to first))
        manager.cancelAll()
        showApp()
        compose.onNodeWithContentDescription("End pause for Second app").assertDoesNotExist()

        // Grant while the app is already open: the list must collect the repository, not a snapshot.
        runBlocking { repository.savePauses(mapOf(firstApp to first, secondApp to second)) }
        compose.onNodeWithContentDescription("End pause for Second app")
            .performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("End pause for First app")
            .performScrollTo().performClick()
        awaitPauseEnded(repository, firstApp)
        assertEquals(first.copy(untilEpochMs = 0), repository.pauses.value[firstApp])
        assertEquals(second, repository.pauses.value[secondApp])
        compose.onNodeWithContentDescription("End pause for First app").assertDoesNotExist()
        compose.onNodeWithContentDescription("End pause for Second app")
            .performScrollTo().assertIsDisplayed().performClick()
        awaitPauseEnded(repository, secondApp)
        assertEquals(second.copy(untilEpochMs = 0), repository.pauses.value[secondApp])
        compose.onNodeWithText("ACTIVE PAUSES").assertDoesNotExist()
    }

    @Test
    fun expiryHidesControlsAtTheBoundaryWithoutChangingTokensOrDependingOnTokenDay() {
        val now = mutableStateOf(10_000L)
        val pauses = mapOf(
            firstApp to PauseState("2026-10-10", used = 2, untilEpochMs = 11_000L),
            secondApp to PauseState("2026-10-11", used = 1, untilEpochMs = 10_000L),
            "test.expired" to PauseState("2026-10-11", used = 2, untilEpochMs = 9_999L),
        )
        var ended: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalAppCatalog provides catalog) {
                SpeedbreakerTheme {
                    ActivePausesSection(pauses, now.value) { ended = it }
                }
            }
        }
        // Yesterday's token day does not end a pause whose exact expiry is still in the future.
        compose.onNodeWithContentDescription("End pause for First app").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(firstApp, ended) }
        compose.onNodeWithContentDescription("End pause for Second app").assertDoesNotExist()
        compose.onNodeWithContentDescription("End pause for Expired app").assertDoesNotExist()
        compose.runOnIdle { now.value = 11_000L }
        compose.onNodeWithContentDescription("End pause for First app").assertDoesNotExist()
        compose.onNodeWithText("ACTIVE PAUSES").assertDoesNotExist()
        assertEquals(2, pauses.getValue(firstApp).used)
    }

    @Test
    fun notificationTapOpensHomeAndExpiryDoesNotResurrectDismissedNotifications() {
        val now = System.currentTimeMillis()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = PauseNotifications(context)
        val pause = PauseState(LocalDate.now().toString(), used = 1, untilEpochMs = now + 900_000)
        notifications.reconcile(mapOf(firstApp to pause), now)
        val posted = manager.activeNotifications.single()
        val openApp = shadowOf(posted.notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, openApp.component?.className)
        assertTrue(openApp.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        posted.notification.contentIntent.send()
        assertEquals(MainActivity::class.java.name, shadowOf(context).nextStartedActivity.component?.className)
        manager.cancel(posted.id)
        notifications.reconcile(mapOf(firstApp to pause), pause.untilEpochMs - 1)
        assertTrue(manager.activeNotifications.isEmpty())
        notifications.reconcile(mapOf(firstApp to pause), pause.untilEpochMs)
        assertTrue(manager.activeNotifications.isEmpty())
        // A genuinely new pause can post its own notification.
        notifications.reconcile(mapOf(firstApp to pause.copy(untilEpochMs = pause.untilEpochMs + 900_000)), pause.untilEpochMs)
        assertEquals(1, manager.activeNotifications.size)
    }

    private fun awaitPauseEnded(repository: SpeedbreakerRepository, packageName: String) {
        compose.waitUntil(10_000) {
            // sendBroadcast queues the real manifest receiver on Android's main looper.
            // Compose's waitUntil advances its own clock, not this paused looper. Drain
            // ready work without advancing wall time, then let the receiver's IO write finish.
            shadowOf(Looper.getMainLooper()).idle()
            assertNull("Ending $packageName failed to persist", repository.error.value)
            repository.pauses.value[packageName]?.untilEpochMs == 0L
        }
    }

    private fun seed(pauses: Map<String, PauseState>): SpeedbreakerRepository {
        val repository = SpeedbreakerRepository.get(context)
        runBlocking {
            withTimeout(10_000) { repository.ready.first { it } }
            repository.setSettings(Settings(consentAccepted = true, apps = pauses.keys.associateWith { AppPolicy() }))
            repository.savePauses(pauses)
        }
        return repository
    }

    private fun showApp() {
        compose.setContent {
            CompositionLocalProvider(LocalAppCatalog provides catalog, LocalStillBreath provides true) {
                SpeedbreakerApp()
            }
        }
    }
}
