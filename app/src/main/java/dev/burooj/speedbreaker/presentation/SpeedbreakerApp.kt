package dev.burooj.speedbreaker.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.burooj.speedbreaker.observation.PauseNotifications
import dev.burooj.speedbreaker.observation.PauseEndReceiver
import dev.burooj.speedbreaker.observation.CallSafety
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.observation.ServiceStatus
import dev.burooj.speedbreaker.observation.SetupReturn
import dev.burooj.speedbreaker.persistence.SpeedbreakerRepository
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Owns state and navigation; every screen it shows is stateless and previewable. */
@Composable
internal fun SpeedbreakerApp() {
    SpeedbreakerTheme {
        val context = LocalContext.current
        val support = rememberDistributionSupport()
        val updates = rememberUpdateSurface()
        val repository = remember(context) { SpeedbreakerRepository.get(context) }
        val settings by repository.settings.collectAsStateWithLifecycle()
        val pauses by repository.pauses.collectAsStateWithLifecycle()
        val stats by repository.stats.collectAsStateWithLifecycle()
        val ready by repository.ready.collectAsStateWithLifecycle()
        val repositoryError by repository.error.collectAsStateWithLifecycle()
        val connected by ServiceStatus.connected.collectAsStateWithLifecycle()
        val runtimeError by ServiceStatus.error.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var route by remember { mutableStateOf<Route>(Route.Home) }
        var resumeTick by remember { mutableIntStateOf(0) }
        var redirectRequest by remember { mutableStateOf<RedirectRequest?>(null) }
        var notificationRequestAttempted by rememberSaveable { mutableStateOf(false) }
        // True once Android has refused without showing its prompt, which it does after two denials.
        var phoneNeedsSettings by rememberSaveable { mutableStateOf(false) }
        var phoneCouldAsk by rememberSaveable { mutableStateOf(false) }
        var phoneSilentRefusals by rememberSaveable { mutableIntStateOf(0) }
        var phoneAskedAtMs by remember { mutableLongStateOf(0L) }
        fun mayAskForPhone(): Boolean = (context as? Activity)
            ?.shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE) == true
        val phonePermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val mayAskAgain = mayAskForPhone()
            phoneNeedsSettings = promptWasWithheld(
                granted = granted,
                couldAskBefore = phoneCouldAsk,
                mayAskAgain = mayAskAgain,
                answeredAfterMs = SystemClock.elapsedRealtime() - phoneAskedAtMs,
                earlierSilentRefusals = phoneSilentRefusals,
            )
            phoneSilentRefusals = if (!granted && !mayAskAgain) phoneSilentRefusals + 1 else 0
            resumeTick++
        }
        val callSafety = remember(context) { CallSafety(context.applicationContext) }
        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { resumeTick++ }
        val accessibilityEnabled = remember(resumeTick) { ServiceStatus.isEnabled(context) }
        val notificationsAllowed = remember(resumeTick) {
            // Same test the service uses, so a muted Pause channel isn't reported as allowed.
            PauseNotifications(context).isAvailable()
        }
        val launchableApps = remember(context, route, redirectRequest) {
            if (route == Route.Apps || redirectRequest != null) discoverApps(context) else emptyList()
        }

        val lifecycle = LocalLifecycleOwner.current.lifecycle
        // Expiry must advance even when persisted pause state does not change (or the service is off).
        val nowEpochMs by produceState(System.currentTimeMillis(), lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    value = System.currentTimeMillis()
                    delay(1_000L)
                }
            }
        }
        val callStatus = remember(resumeTick, nowEpochMs) { callSafety.status() }
        val phoneAllowed = remember(resumeTick, nowEpochMs) { callSafety.phoneAllowed() }
        // Allowed from app info, or revoked later: either way Android's prompt is the way in again.
        SideEffect {
            if (phoneAllowed && (phoneNeedsSettings || phoneSilentRefusals > 0)) {
                phoneNeedsSettings = false
                phoneSilentRefusals = 0
            }
        }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // Back in the app, by whatever route: nothing is left to be brought back from.
                    SetupReturn.cancel()
                    resumeTick++
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        fun update(transform: SettingsTransform) {
            scope.launch { repository.updateSettings(transform) }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            val request = redirectRequest
            val currentRoute = route
            when {
                repositoryError != null -> StorageFailureScreen(repositoryError)
                !ready -> LoadingScreen()
                !settings.consentAccepted -> WelcomeScreen(onDecline = {
                    (context as? Activity)?.finish()
                }, onPrivacy = { openPrivacyPolicy(context) }) {
                    update { current -> current.copy(consentAccepted = true) }
                }
                request != null -> RedirectPickerScreen(
                    title = request.title,
                    apps = launchableApps.filter { it.packageName !in settings.apps.keys },
                    initial = request.selected,
                    onBack = { redirectRequest = null },
                    onSave = { value ->
                        update(request.transformFor(value))
                        redirectRequest = null
                    },
                )
                currentRoute == Route.Home -> HomeScreen(
                    settings = settings,
                    protection = when {
                        !accessibilityEnabled -> Protection.Off
                        !connected || runtimeError != null -> Protection.Broken
                        callStatus == CallSafety.Status.Unavailable -> Protection.CallUnavailable
                        callStatus == CallSafety.Status.InCall -> Protection.CallActive
                        else -> Protection.On
                    },
                    runtimeError = runtimeError,
                    notificationsAllowed = notificationsAllowed,
                    pauses = pauses,
                    nowEpochMs = nowEpochMs,
                    stats = stats,
                    onEndPause = { packageName ->
                        // Share the notification action's service coordination and durable transaction.
                        context.sendBroadcast(
                            Intent(context, PauseEndReceiver::class.java)
                                .putExtra(PauseEndReceiver.EXTRA_PACKAGE_NAME, packageName),
                        )
                    },
                    onEnableAccessibility = {
                        openAccessibilitySettings(context)
                        SetupReturn.expect(SetupReturn.Waiting.ServiceOn, SystemClock.elapsedRealtime())
                    },
                    onOpenAppInfo = { openAppDetails(context) },
                    onChooseApps = { route = Route.Apps },
                    onSettings = { route = Route.Settings },
                    onApp = { route = Route.App(it) },
                    updateCard = updates?.homeCard,
                )
                currentRoute == Route.Settings -> SettingsScreen(
                    settings = settings,
                    notificationsAllowed = notificationsAllowed,
                    onBack = { route = Route.Home },
                    onBreathLength = { route = Route.BreathLength },
                    onSchedule = { route = Route.Schedule },
                    onDetours = {
                        redirectRequest = RedirectRequest(
                            title = "Detours",
                            selected = settings.redirects,
                            transformFor = { value -> { current -> current.copy(redirects = value) } },
                        )
                    },
                    onNotifications = {
                        val canAsk = android.os.Build.VERSION.SDK_INT >= 33 &&
                            !notificationRequestAttempted &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        if (canAsk) {
                            notificationRequestAttempted = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    },
                    onOpenAppInfo = { openAppDetails(context) },
                    onCallAccess = {
                        if (phoneNeedsSettings) {
                            openAppDetails(context)
                            SetupReturn.expect(SetupReturn.Waiting.PhoneAllowed, SystemClock.elapsedRealtime())
                        } else {
                            phoneCouldAsk = mayAskForPhone()
                            phoneAskedAtMs = SystemClock.elapsedRealtime()
                            phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    },
                    phoneNeedsSettings = phoneNeedsSettings && !phoneAllowed,
                    phoneAllowed = phoneAllowed,
                    onHelp = { route = Route.Help },
                    onPrivacy = { openPrivacyPolicy(context) },
                    onSupport = support.onClick,
                    supportLabel = support.label,
                    supportSummary = support.summary,
                    supportAvailable = support.available,
                    canResetStats = !stats.isEmpty,
                    onResetStats = { scope.launch { repository.resetStats() } },
                    versionName = remember(context) { installedVersionName(context) },
                    updatesSection = updates?.settingsSection,
                )
                currentRoute == Route.Help -> HelpScreen(
                    onBack = { route = Route.Settings },
                    onGuide = { openSupportPage(context) },
                    onPrivacy = { openPrivacyPolicy(context) },
                    versionName = remember(context) { installedVersionName(context) },
                )
                currentRoute == Route.Apps -> AppPickerScreen(
                    apps = launchableApps,
                    selected = settings.apps.keys,
                    onBack = { route = Route.Home },
                    onSetSelected = { app, selected ->
                        update { current -> current.withTarget(app.packageName, selected) }
                    },
                )
                currentRoute == Route.BreathLength -> BreathLengthScreen(
                    settings = settings,
                    onBack = { route = Route.Settings },
                    onBreathChange = { breath -> update { current -> current.copy(breathSeconds = breath) } },
                )
                currentRoute == Route.Schedule -> DefaultScheduleScreen(
                    settings = settings,
                    onBack = { route = Route.Settings },
                    onScheduleTransform = { transform ->
                        update { current -> current.copy(schedule = transform(current.schedule)) }
                    },
                )
                currentRoute is Route.App -> {
                    val packageName = currentRoute.packageName
                    AppSettingsScreen(
                        packageName = packageName,
                        settings = settings,
                        onBack = { route = Route.Home },
                        onPolicyTransform = { transform ->
                            update { current ->
                                val policy = current.apps[packageName]
                                if (policy == null) current else {
                                    current.copy(apps = current.apps + (packageName to transform(policy)))
                                }
                            }
                        },
                        onRedirects = { current ->
                            redirectRequest = RedirectRequest(
                                title = "Detours for this app",
                                selected = current,
                                transformFor = { value ->
                                    { settings ->
                                        val policy = settings.apps[packageName]
                                        if (policy == null) {
                                            settings
                                        } else {
                                            settings.copy(
                                                apps = settings.apps + (packageName to policy.copy(redirects = value)),
                                            )
                                        }
                                    }
                                },
                            )
                        },
                        onRemove = {
                            route = Route.Home
                            update { current -> current.withTarget(packageName, false) }
                        },
                    )
                }
            }
        }
    }
}

private sealed interface Route {
    data object Home : Route
    data object Apps : Route
    data object Settings : Route
    data object BreathLength : Route
    data object Schedule : Route
    data object Help : Route
    data class App(val packageName: String) : Route
}

private data class RedirectRequest(
    val title: String,
    val selected: List<String>,
    val transformFor: (List<String>) -> SettingsTransform,
)

private typealias SettingsTransform = (Settings) -> Settings

private fun Settings.withTarget(packageName: String, selected: Boolean): Settings {
    val updated = apps.toMutableMap()
    if (selected) {
        updated.putIfAbsent(packageName, dev.burooj.speedbreaker.model.AppPolicy())
    } else {
        updated.remove(packageName)
    }
    return copy(apps = updated)
}
