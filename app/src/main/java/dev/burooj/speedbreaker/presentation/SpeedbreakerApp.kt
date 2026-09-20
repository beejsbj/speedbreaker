package dev.burooj.speedbreaker.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import dev.burooj.speedbreaker.observation.ServiceStatus
import dev.burooj.speedbreaker.persistence.SpeedbreakerRepository
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

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
        ) { }
        val accessibilityEnabled = remember(resumeTick) { ServiceStatus.isEnabled(context) }

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
        ) {
            when {
                repositoryError != null -> RepositoryFailure(repositoryError)
                !ready -> CenterText("Opening your local settings…")
                !settings.consentAccepted -> DisclosureScreen {
                    update { current -> current.copy(consentAccepted = true) }
                }
                route == Route.Home -> HomeScreen(
                    settings = settings,
                    protected = accessibilityEnabled && connected && runtimeError == null,
                    runtimeError = runtimeError,
                    onEnableAccessibility = {
                        context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenAppSettings = { openAppDetails(context) },
                    onOpenNotificationSettings = { openNotificationSettings(context) },
                    onApps = { route = Route.Apps },
                    onGlobal = { route = Route.Global },
                    onApp = { route = Route.App(it) },
                    onRequestNotifications = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationRequestAttempted = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    showNotificationRequest = android.os.Build.VERSION.SDK_INT >= 33 &&
                        !notificationRequestAttempted &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED,
                )
                route == Route.Apps -> AppPicker(
                    selected = settings.apps.keys,
                    onBack = { route = Route.Home },
                    onSetSelected = { app, selected ->
                        update { current ->
                            val updatedApps = current.apps.toMutableMap()
                            if (selected) {
                                updatedApps.putIfAbsent(app.packageName, AppPolicy())
                            } else {
                                updatedApps.remove(app.packageName)
                            }
                            current.copy(apps = updatedApps)
                        }
                    },
                )
                route == Route.Global -> GlobalEditor(
                    settings = settings,
                    onBack = { route = Route.Home },
                    onBreathChange = { breath -> update { current -> current.copy(breathSeconds = breath) } },
                    onScheduleTransform = { transform ->
                        update { current -> current.copy(schedule = transform(current.schedule)) }
                    },
                    onRedirects = {
                        redirectRequest = RedirectRequest(
                            title = "Redirect alternatives",
                            selected = settings.redirects,
                            targetPackages = settings.apps.keys,
                            transformFor = { value -> { current -> current.copy(redirects = value) } },
                        )
                    },
                )
                route is Route.App -> {
                    val packageName = (route as Route.App).packageName
                    AppEditor(
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
                                title = "Per-app redirect alternatives",
                                selected = current,
                                targetPackages = settings.apps.keys,
                                transformFor = { value ->
                                    { current ->
                                        val policy = current.apps[packageName]
                                        if (policy == null) {
                                            current
                                        } else {
                                            current.copy(
                                                apps = current.apps + (packageName to policy.copy(redirects = value)),
                                            )
                                        }
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
        redirectRequest?.let { request ->
            RedirectPickerDialog(
                request = request,
                onApply = ::update,
                onDismiss = { redirectRequest = null },
            )
        }
    }
}

private sealed interface Route {
    data object Home : Route
    data object Apps : Route
    data object Global : Route
    data class App(val packageName: String) : Route
}

private data class RedirectRequest(
    val title: String,
    val selected: List<String>,
    val targetPackages: Set<String>,
    val transformFor: (List<String>) -> SettingsTransform,
)

private typealias SettingsTransform = (Settings) -> Settings
private typealias PolicyTransform = (AppPolicy) -> AppPolicy
private typealias ScheduleTransform = (WeeklySchedule?) -> WeeklySchedule?

@Composable
private fun RepositoryFailure(error: String?) {
    Column(
        modifier = Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Speedbreaker is inactive", style = MaterialTheme.typography.headlineSmall)
        Text(error ?: "Local settings aren’t available, so protection cannot start.")
        Text("Restart the app after resolving the storage problem.")
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

@Composable
private fun DisclosureScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Make the next tap intentional.", style = MaterialTheme.typography.headlineMedium)
        Text("Speedbreaker adds a brief breathing pause before apps you choose. It observes only the visible selected app, locally on this phone.")
        Text("During the first eight seconds of a pause, navigation cannot dismiss it. Calls, the dialer, lock screen, Accessibility settings, and a non-interactive phone always break through.")
        Text("It stores no usage history, reflection answers, analytics, or cloud data. It does not ask for Usage Access or a separate overlay permission.")
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("I understand — continue")
        }
    }
}

@Composable
private fun HomeScreen(
    settings: Settings,
    protected: Boolean,
    runtimeError: String?,
    onEnableAccessibility: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onApps: () -> Unit,
    onGlobal: () -> Unit,
    onApp: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    showNotificationRequest: Boolean,
) = SettingsPage("Speedbreaker") {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (protected) "Protection is active" else "Protection is inactive",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                runtimeError ?: if (protected) {
                    "Accessibility is connected."
                } else {
                    "Enable Accessibility to let Speedbreaker protect selected apps. Settings remain editable while it is off."
                },
            )
            if (!protected) {
                Button(onClick = onEnableAccessibility) { Text("Enable Accessibility") }
                Text("If Android says access is restricted, open Speedbreaker’s app info, choose More options → Allow restricted settings, and verify your identity. Then return to Accessibility to enable Speedbreaker attention pauses.")
                OutlinedButton(onClick = onOpenAppSettings) { Text("Open Speedbreaker app info") }
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Samsung battery guidance", style = MaterialTheme.typography.titleMedium)
            Text("For reliable pauses, allow Speedbreaker to run in the background and avoid putting it to sleep in Samsung battery settings.")
            OutlinedButton(onClick = onOpenAppSettings) { Text("Open app settings") }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pause notifications", style = MaterialTheme.typography.titleMedium)
            Text("Notifications make Pause and its End now action usable. Breathing protection still works without them.")
            if (showNotificationRequest) {
                OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow notifications")
                }
            }
            OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Open notification settings")
            }
        }
    }
    SettingsRow(
        title = "Selected apps",
        subtitle = if (settings.apps.isEmpty()) "Next: choose apps after enabling Accessibility." else "${settings.apps.size} selected",
        onClick = onApps,
    )
    SettingsRow(
        title = "Global defaults",
        subtitle = "${settings.breathSeconds}-second breath · ${scheduleSummary(settings.schedule)}",
        onClick = onGlobal,
    )
    if (settings.apps.isNotEmpty()) {
        Text("Per-app settings", style = MaterialTheme.typography.titleMedium)
        settings.apps.keys.sorted().forEach { packageName ->
            SettingsRow(
                title = appLabel(LocalContext.current, packageName),
                subtitle = packageName,
                onClick = { onApp(packageName) },
            )
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
    Divider()
}

@Composable
private fun AppPicker(
    selected: Set<String>,
    onBack: () -> Unit,
    onSetSelected: (LaunchableApp, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember(context) { discoverApps(context) }
    var query by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        PageHeader("Choose apps", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text("Search apps") },
            singleLine = true,
        )
        Text(
            "Nothing is selected by default. System and safety-critical apps are unavailable. Selecting a redirect destination clears that redirect configuration rather than leaving it unsafe.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        val filtered = apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSetSelected(app, app.packageName !in selected) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(
                        checked = app.packageName in selected,
                        onCheckedChange = { enabled -> onSetSelected(app, enabled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalEditor(
    settings: Settings,
    onBack: () -> Unit,
    onBreathChange: (Int) -> Unit,
    onScheduleTransform: (ScheduleTransform) -> Unit,
    onRedirects: () -> Unit,
) = SettingsPage("Global defaults", onBack) {
    IntSlider(
        title = "Breath",
        current = settings.breathSeconds,
        range = 8..60,
        suffix = "seconds",
        onCommit = onBreathChange,
    )
    RedirectSummary(settings.redirects, onRedirects)
    ScheduleEditor(
        title = "Active schedule",
        schedule = settings.schedule,
        isAppOverride = false,
        onTransform = onScheduleTransform,
    )
}

@Composable
private fun AppEditor(
    packageName: String,
    settings: Settings,
    onBack: () -> Unit,
    onPolicyTransform: (PolicyTransform) -> Unit,
    onRedirects: (List<String>) -> Unit,
) {
    val policy = settings.apps[packageName] ?: return
    SettingsPage(appLabel(LocalContext.current, packageName), onBack) {
        Text("Continuous-use interval", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = policy.continuousSeconds != null,
                onCheckedChange = { enabled ->
                    onPolicyTransform { current ->
                        current.copy(continuousSeconds = if (enabled) DEFAULT_INTERVAL_SECONDS else null)
                    }
                },
            )
            Text(if (policy.continuousSeconds == null) "Off" else "On")
        }
        policy.continuousSeconds?.let { seconds ->
            IntSlider(
                title = "Repeat after",
                current = (seconds / 60).coerceIn(1, 120),
                range = 1..120,
                suffix = "minutes",
                onCommit = { minutes ->
                    onPolicyTransform { current -> current.copy(continuousSeconds = minutes * 60) }
                },
            )
        }
        ScheduleOverride(policy, onPolicyTransform)
        RedirectOverride(policy, onRedirects, onPolicyTransform)
    }
}

@Composable
private fun IntSlider(
    title: String,
    current: Int,
    range: IntRange,
    suffix: String,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(current) { mutableFloatStateOf(current.toFloat()) }
    Text("$title: ${draft.roundToInt()} $suffix", style = MaterialTheme.typography.titleMedium)
    Slider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft.roundToInt().coerceIn(range)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
    )
}

@Composable
private fun RedirectSummary(redirects: List<String>, onEdit: () -> Unit) {
    Text("Redirect alternatives", style = MaterialTheme.typography.titleMedium)
    Text(
        if (redirects.size == 4) "Four alternatives configured." else "No redirects are active. Configure exactly four alternatives.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onEdit) { Text("Configure redirects") }
}

@Composable
private fun ScheduleOverride(policy: AppPolicy, onPolicyTransform: (PolicyTransform) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = policy.schedule != null,
            onCheckedChange = { enabled ->
                onPolicyTransform { current ->
                    current.copy(schedule = if (enabled) WeeklySchedule() else null)
                }
            },
        )
        Text(if (policy.schedule == null) "Schedule: inherit global" else "Schedule: override global")
    }
    policy.schedule?.let { schedule ->
        ScheduleEditor(
            title = "Per-app active schedule",
            schedule = schedule,
            isAppOverride = true,
            onTransform = { transform ->
                onPolicyTransform { current ->
                    current.copy(schedule = transform(current.schedule) ?: allDaySchedule())
                }
            },
        )
    }
}

@Composable
private fun RedirectOverride(
    policy: AppPolicy,
    onRedirects: (List<String>) -> Unit,
    onPolicyTransform: (PolicyTransform) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = policy.redirects != null,
            onCheckedChange = { enabled ->
                onPolicyTransform { current ->
                    current.copy(redirects = if (enabled) emptyList() else null)
                }
            },
        )
        Text(if (policy.redirects == null) "Redirects: inherit global" else "Redirects: replace global")
    }
    policy.redirects?.let { RedirectSummary(it) { onRedirects(it) } }
}

@Composable
private fun ScheduleEditor(
    title: String,
    schedule: WeeklySchedule?,
    isAppOverride: Boolean,
    onTransform: (ScheduleTransform) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (schedule == null) {
        Text("Always active.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onTransform { WeeklySchedule() } }) { Text("Set active days") }
        return
    }
    Text(
        "One window per day. An end before its start continues overnight.",
        style = MaterialTheme.typography.bodySmall,
    )
    var copySource by remember(schedule.days) { mutableStateOf<Int?>(null) }
    weekdays.forEachIndexed { index, name ->
        val day = index + 1
        val window = schedule.days[day]
        DayWindowEditor(
            name = name,
            window = window,
            onEnabledChange = { enabled ->
                onTransform { current ->
                    val days = (current ?: WeeklySchedule()).days.toMutableMap()
                    if (enabled) days.putIfAbsent(day, DEFAULT_WINDOW) else days.remove(day)
                    WeeklySchedule(days)
                }
            },
            onStartChange = { start ->
                onTransform { current ->
                    val existing = current?.days?.get(day)
                    when {
                        existing == null -> current
                        start == existing.endMinute -> current
                        else -> WeeklySchedule(current.days + (day to existing.copy(startMinute = start)))
                    }
                }
            },
            onEndChange = { end ->
                onTransform { current ->
                    val existing = current?.days?.get(day)
                    when {
                        existing == null -> current
                        end == existing.startMinute -> current
                        else -> WeeklySchedule(current.days + (day to existing.copy(endMinute = end)))
                    }
                }
            },
            onCopy = { copySource = day },
        )
    }
    copySource?.let { sourceDay ->
        if (schedule.days[sourceDay] == null) return@let
        Text("Copy ${weekdays[sourceDay - 1]} window to:", style = MaterialTheme.typography.bodySmall)
        weekdays.forEachIndexed { index, name ->
            val targetDay = index + 1
            if (targetDay != sourceDay) {
                AssistChip(
                    onClick = {
                        onTransform { current ->
                            val currentSource = current?.days?.get(sourceDay)
                            if (currentSource == null) current else WeeklySchedule(current.days + (targetDay to currentSource))
                        }
                    },
                    label = { Text(name) },
                )
            }
        }
        TextButton(onClick = { copySource = null }) { Text("Done copying") }
    }
    if (isAppOverride) {
        OutlinedButton(onClick = { onTransform { allDaySchedule() } }) {
            Text("Always active for this app")
        }
    } else {
        OutlinedButton(onClick = { onTransform { null } }) { Text("Use always active") }
    }
}

@Composable
private fun DayWindowEditor(
    name: String,
    window: TimeWindow?,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onCopy: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = window != null, onCheckedChange = onEnabledChange)
            Text(name, modifier = Modifier.weight(1f))
            if (window != null) TextButton(onClick = onCopy) { Text("Copy") }
        }
        if (window != null) {
            TimeField(
                label = "$name start",
                minute = window.startMinute,
                allowEndOfDay = false,
                otherMinute = window.endMinute,
                onValidMinute = onStartChange,
            )
            TimeField(
                label = "$name end",
                minute = window.endMinute,
                allowEndOfDay = true,
                otherMinute = window.startMinute,
                onValidMinute = onEndChange,
            )
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    minute: Int,
    allowEndOfDay: Boolean,
    otherMinute: Int,
    onValidMinute: (Int) -> Unit,
) {
    var draft by remember(label, minute) { mutableStateOf(formatTime(minute)) }
    val parsed = parseTime(draft, allowEndOfDay)
    val invalid = draft.isNotBlank() && (parsed == null || parsed == otherMinute)
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = {
            if (draft.isNotBlank() && parsed == null) {
                Text("Use HH:MM${if (allowEndOfDay) "; 24:00 allowed" else ""}.")
            } else if (parsed == otherMinute) {
                Text("Start and end must differ.")
            }
        },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )
    TextButton(
        enabled = parsed != null && parsed != minute && parsed != otherMinute,
        onClick = { parsed?.let(onValidMinute) },
    ) { Text("Apply $label") }
}

@Composable
private fun RedirectPickerDialog(
    request: RedirectRequest,
    onApply: (SettingsTransform) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember(context, request.targetPackages) {
        discoverApps(context).filter { it.packageName !in request.targetPackages }
    }
    var draft by remember(request.title, request.selected) { mutableStateOf(request.selected) }
    var query by remember(request.title) { mutableStateOf("") }
    val filtered = apps.filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose exactly four alternatives. Changes apply only when all four are selected.")
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search apps") },
                    singleLine = true,
                )
                Column(Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
                    filtered.forEach { app ->
                        val chosen = app.packageName in draft
                        FilterChip(
                            selected = chosen,
                            onClick = {
                                draft = when {
                                    chosen -> draft - app.packageName
                                    draft.size < 4 -> draft + app.packageName
                                    else -> draft
                                }
                            },
                            label = { Text(app.label) },
                        )
                    }
                }
                Text("${draft.size} of 4 selected", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = draft.size == 4,
                onClick = { onApply(request.transformFor(draft)); onDismiss() },
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(request.transformFor(emptyList())); onDismiss() }) {
                    Text("Clear")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (title != "Speedbreaker") TextButton(onClick = onBack) { Text("Back") }
        },
    )
}

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = title != "Speedbreaker", onBack = onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        PageHeader(title, onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

private data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

private fun discoverApps(context: Context): List<LaunchableApp> {
    val packageManager = context.packageManager
    val home = packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName
    val defaultInput = AndroidSettings.Secure.getString(
        context.contentResolver,
        AndroidSettings.Secure.DEFAULT_INPUT_METHOD,
    )?.substringBefore('/')
    val inputPackages = context.getSystemService(InputMethodManager::class.java)
        ?.enabledInputMethodList
        ?.map { it.packageName }
        .orEmpty()
    val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    val excluded = setOfNotNull(context.packageName, home, defaultInput, dialer) + inputPackages + safetyPackages
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        .mapNotNull { resolveInfo ->
            val activity = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activity.packageName
            if (packageName in excluded || looksSafetyCritical(packageName)) return@mapNotNull null
            LaunchableApp(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
                icon = resolveInfo.loadIcon(packageManager),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

@Composable
private fun AppIcon(app: LaunchableApp) {
    val image = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Image(image, contentDescription = app.label, modifier = Modifier.size(40.dp))
}

private fun appLabel(context: Context, packageName: String): String = try {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
} catch (_: PackageManager.NameNotFoundException) {
    packageName
}

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

private fun formatTime(minute: Int): String = "%02d:%02d".format(Locale.US, minute / 60, minute % 60)

private fun parseTime(value: String, allowEndOfDay: Boolean): Int? {
    val match = TIME_PATTERN.matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    if (minute !in 0..59) return null
    if (hour in 0..23) return hour * 60 + minute
    return if (allowEndOfDay && hour == 24 && minute == 0) 1440 else null
}

private fun allDaySchedule(): WeeklySchedule = WeeklySchedule(
    (1..7).associateWith { TimeWindow(startMinute = 0, endMinute = 1440) },
)

private fun scheduleSummary(schedule: WeeklySchedule?): String = when {
    schedule == null -> "always active"
    schedule.days.isEmpty() -> "no active days"
    else -> "scheduled"
}

private fun looksSafetyCritical(packageName: String): Boolean {
    val normalized = packageName.lowercase(Locale.US)
    return safetyMarkers.any { it in normalized }
}

private const val DEFAULT_INTERVAL_SECONDS = 10 * 60
private val DEFAULT_WINDOW = TimeWindow(startMinute = 9 * 60, endMinute = 17 * 60)
private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val TIME_PATTERN = Regex("^(\\d{1,2}):(\\d{2})$")
private val safetyPackages = setOf(
    "com.android.settings",
    "com.android.systemui",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "com.android.packageinstaller",
)
private val safetyMarkers = listOf(
    "systemui",
    "permissioncontroller",
    "packageinstaller",
    "emergency",
    "dialer",
    "incall",
    "telecom",
    "inputmethod",
)
