package dev.burooj.speedbreaker.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.burooj.speedbreaker.observation.PauseNotifications
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.observation.ServiceStatus
import dev.burooj.speedbreaker.persistence.SpeedbreakerRepository
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import kotlinx.coroutines.launch

/** Owns state and navigation; every screen it shows is stateless and previewable. */
@Composable
internal fun SpeedbreakerApp() {
    SpeedbreakerTheme {
        val context = LocalContext.current
        val repository = remember(context) { SpeedbreakerRepository.get(context) }
        val settings by repository.settings.collectAsStateWithLifecycle()
        val ready by repository.ready.collectAsStateWithLifecycle()
        val repositoryError by repository.error.collectAsStateWithLifecycle()
        val connected by ServiceStatus.connected.collectAsStateWithLifecycle()
        val runtimeError by ServiceStatus.error.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var route by remember { mutableStateOf<Route>(Route.Home) }
        var resumeTick by remember { mutableIntStateOf(0) }
        var redirectRequest by remember { mutableStateOf<RedirectRequest?>(null) }
        var notificationRequestAttempted by rememberSaveable { mutableStateOf(false) }
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
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) resumeTick++
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
                !settings.consentAccepted -> WelcomeScreen {
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
                        else -> Protection.On
                    },
                    runtimeError = runtimeError,
                    notificationsAllowed = notificationsAllowed,
                    onEnableAccessibility = {
                        context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenAppInfo = { openAppDetails(context) },
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
                    onChooseApps = { route = Route.Apps },
                    onDefaults = { route = Route.Defaults },
                    onApp = { route = Route.App(it) },
                )
                currentRoute == Route.Apps -> AppPickerScreen(
                    apps = launchableApps,
                    selected = settings.apps.keys,
                    onBack = { route = Route.Home },
                    onSetSelected = { app, selected ->
                        update { current -> current.withTarget(app.packageName, selected) }
                    },
                )
                currentRoute == Route.Defaults -> DefaultsScreen(
                    settings = settings,
                    onBack = { route = Route.Home },
                    onBreathChange = { breath -> update { current -> current.copy(breathSeconds = breath) } },
                    onScheduleTransform = { transform ->
                        update { current -> current.copy(schedule = transform(current.schedule)) }
                    },
                    onRedirects = {
                        redirectRequest = RedirectRequest(
                            title = "Redirects",
                            selected = settings.redirects,
                            transformFor = { value -> { current -> current.copy(redirects = value) } },
                        )
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
                                title = "Redirects for this app",
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
    data object Defaults : Route
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
