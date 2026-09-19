package dev.burooj.speedbreaker.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings as AndroidSettings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.burooj.speedbreaker.model.*
import dev.burooj.speedbreaker.observation.ServiceStatus
import dev.burooj.speedbreaker.persistence.SpeedbreakerRepository
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import kotlinx.coroutines.launch
import java.util.Locale

@Composable fun SpeedbreakerApp() {
    SpeedbreakerTheme {
        val context = LocalContext.current
        val repository = remember { SpeedbreakerRepository.get(context) }
        val settings by repository.settings.collectAsStateWithLifecycle()
        val ready by repository.ready.collectAsStateWithLifecycle()
        val error by repository.error.collectAsStateWithLifecycle()
        val connected by ServiceStatus.connected.collectAsStateWithLifecycle()
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var resumeTick by remember { mutableIntStateOf(0) }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val enabled = remember(resumeTick) { ServiceStatus.isEnabled(context) }
        val scope = rememberCoroutineScope()
        val save: (Settings) -> Unit = { value -> scope.launch { repository.setSettings(value) } }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                error != null -> Failure(error)
                !ready -> CenterText("Opening your local settings…")
                !settings.consentAccepted -> Disclosure { save(settings.copy(consentAccepted = true)) }
                else -> when (val route = screen) {
                    Screen.Home -> Home(settings, enabled && connected, { context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }, { screen = Screen.Apps }, { screen = Screen.Global }, { screen = Screen.App(it) }, { if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) })
                    Screen.Apps -> Picker(settings.apps.keys, { screen = Screen.Home }) { app ->
                        val apps = settings.apps.toMutableMap(); if (app.packageName in apps) apps.remove(app.packageName) else apps[app.packageName] = AppPolicy(); save(settings.copy(apps = apps))
                    }
                    Screen.Global -> Global(settings, { screen = Screen.Home }, save)
                    is Screen.App -> AppSettings(route.name, settings, { screen = Screen.Home }, save)
                }
            }
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) { val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick++ }; lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) } }
    }
}
private sealed interface Screen { data object Home: Screen; data object Apps: Screen; data object Global: Screen; data class App(val name: String): Screen }
@Composable private fun CenterText(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
@Composable private fun Failure(error: String?) = Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("Speedbreaker is inactive", style = MaterialTheme.typography.headlineSmall); Text(error ?: "Local settings aren’t available, so protection cannot start."); Text("Restart the app after resolving the storage problem.") }
@Composable private fun Disclosure(accept: () -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Spacer(Modifier.height(28.dp)); Text("Make the next tap intentional.", style = MaterialTheme.typography.headlineMedium)
    Text("Speedbreaker adds a brief breathing pause before apps you choose. It observes only the visible selected app, locally on this phone.")
    Text("During the first eight seconds of a pause, navigation cannot dismiss it. Calls, the dialer, lock screen, Accessibility settings, and a non-interactive phone always break through.")
    Text("It stores no usage history, reflection answers, analytics, or cloud data. It does not ask for Usage Access or a separate overlay permission.")
    Button(accept, Modifier.fillMaxWidth()) { Text("I understand — continue") }
}
@Composable private fun Home(settings: Settings, active: Boolean, enable: () -> Unit, apps: () -> Unit, global: () -> Unit, edit: (String) -> Unit, notifications: () -> Unit) = Page("Speedbreaker") {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (active) "Protection is active" else "Protection is inactive", style = MaterialTheme.typography.titleLarge); Text(if (active) "Accessibility is connected." else "Enable Accessibility to let Speedbreaker protect selected apps. Settings remain editable while it is off."); if (!active) Button(enable) { Text("Enable Accessibility") } } }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Samsung battery guidance", style = MaterialTheme.typography.titleMedium); Text("For reliable pauses, allow Speedbreaker to run in the background and avoid putting it to sleep in Samsung battery settings."); OutlinedButton(enable) { Text("Open Android settings") } } }
    if (android.os.Build.VERSION.SDK_INT >= 33) OutlinedButton(notifications, Modifier.fillMaxWidth()) { Text("Allow pause notifications (optional)") }
    ListItem({ Text("Selected apps") }, supportingContent = { Text(if (settings.apps.isEmpty()) "None yet — choose apps to mediate." else "${settings.apps.size} selected") }, modifier = Modifier.clickable(onClick = apps)); Divider()
    ListItem({ Text("Global defaults") }, supportingContent = { Text("${settings.breathSeconds}-second breath · ${summary(settings.schedule)}") }, modifier = Modifier.clickable(onClick = global))
    if (settings.apps.isNotEmpty()) { Text("Per-app settings", style = MaterialTheme.typography.titleMedium); settings.apps.keys.sorted().forEach { pkg -> ListItem({ Text(label(LocalContext.current, pkg)) }, supportingContent = { Text(pkg) }, modifier = Modifier.clickable { edit(pkg) }) } }
}
@Composable private fun Picker(selected: Set<String>, back: () -> Unit, toggle: (LaunchableApp) -> Unit) { val apps = remember { discover(LocalContext.current) }; var query by remember { mutableStateOf("") }; Column(Modifier.fillMaxSize()) { Header("Choose apps", back); OutlinedTextField(query, { query = it }, { Text("Search apps") }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true); Text("Nothing is selected by default. System and safety-critical apps are unavailable. If a newly selected target is a redirect, that redirect set is cleared rather than left partially active.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall); LazyColumn { items(apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }, { it.packageName }) { app -> ListItem({ Text(app.label) }, supportingContent = { Text(app.packageName) }, leadingContent = { AppIcon(app) }, trailingContent = { Checkbox(app.packageName in selected, { toggle(app) }) }, modifier = Modifier.clickable { toggle(app) }) } } } }
@Composable private fun Global(settings: Settings, back: () -> Unit, save: (Settings) -> Unit) = Column(Modifier.fillMaxSize()) { Header("Global defaults", back); Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { Text("Breath: ${settings.breathSeconds} seconds", style = MaterialTheme.typography.titleMedium); Slider(settings.breathSeconds.toFloat(), { save(settings.copy(breathSeconds = it.toInt())) }, valueRange = 8f..60f, steps = 51); Redirects("Redirect alternatives", settings.redirects, settings.apps.keys) { save(settings.copy(redirects = it)) }; Schedule("Active schedule", settings.schedule) { save(settings.copy(schedule = it)) } } }
@Composable private fun AppSettings(pkg: String, settings: Settings, back: () -> Unit, save: (Settings) -> Unit) { val policy = settings.apps[pkg] ?: return; Column(Modifier.fillMaxSize()) { Header(label(LocalContext.current, pkg), back); Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { Text("Continuous-use interval", style = MaterialTheme.typography.titleMedium); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(policy.continuousSeconds != null, { checked -> change(settings, pkg, policy.copy(continuousSeconds = if (checked) 600 else null), save) }); Text(if (policy.continuousSeconds == null) "Off" else "On") }; policy.continuousSeconds?.let { seconds -> val min = (seconds / 60).coerceIn(1, 120); Text("Every $min minutes"); Slider(min.toFloat(), { change(settings, pkg, policy.copy(continuousSeconds = it.toInt() * 60), save) }, valueRange = 1f..120f, steps = 118) }; OverrideSchedule(policy, settings, pkg, save); OverrideRedirects(policy, settings, pkg, save) } } }
private fun change(settings: Settings, pkg: String, policy: AppPolicy, save: (Settings) -> Unit) = save(settings.copy(apps = settings.apps + (pkg to policy)))
@Composable private fun OverrideSchedule(policy: AppPolicy, settings: Settings, pkg: String, save: (Settings) -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(policy.schedule != null, { enabled -> change(settings, pkg, policy.copy(schedule = if (enabled) WeeklySchedule() else null), save) }); Text(if (policy.schedule == null) "Schedule: inherit global" else "Schedule: override global") }; policy.schedule?.let { Schedule("Per-app active schedule", it) { change(settings, pkg, policy.copy(schedule = it), save) } } }
@Composable private fun OverrideRedirects(policy: AppPolicy, settings: Settings, pkg: String, save: (Settings) -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(policy.redirects != null, { enabled -> change(settings, pkg, policy.copy(redirects = if (enabled) emptyList() else null), save) }); Text(if (policy.redirects == null) "Redirects: inherit global" else "Redirects: replace global") }; policy.redirects?.let { Redirects("Per-app redirect alternatives", it, settings.apps.keys) { change(settings, pkg, policy.copy(redirects = it), save) } } }
@Composable private fun Redirects(title: String, values: List<String>, targets: Set<String>, change: (List<String>) -> Unit) { val apps = remember { discover(LocalContext.current) }.filter { it.packageName !in targets }; Text(title, style = MaterialTheme.typography.titleMedium); Text(if (values.size == 4) "Four alternatives configured." else "Choose exactly four alternatives. Partial selections are inactive.", style = MaterialTheme.typography.bodySmall); apps.forEach { app -> val chosen = app.packageName in values; FilterChip(chosen, { change(if (chosen) values - app.packageName else if (values.size < 4) values + app.packageName else values) }, { Text(if (chosen) "✓ ${app.label}" else app.label) }) } }
@Composable private fun Schedule(title: String, value: WeeklySchedule?, change: (WeeklySchedule?) -> Unit) { Text(title, style = MaterialTheme.typography.titleMedium); if (value == null) { Text("Always active.", style = MaterialTheme.typography.bodySmall); OutlinedButton({ change(WeeklySchedule()) }) { Text("Set active days") }; return }; Text("One window per day. An end before its start continues overnight.", style = MaterialTheme.typography.bodySmall); val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"); names.forEachIndexed { index, name -> val day = index + 1; val window = value.days[day]; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(window != null, { checked -> change(WeeklySchedule(value.days.toMutableMap().apply { if (checked) put(day, TimeWindow(540, 1020)) else remove(day) })) }); Text(name, Modifier.width(40.dp)); if (window != null) { Minute("Start", window.startMinute) { start -> if (start != window.endMinute) change(WeeklySchedule(value.days + (day to window.copy(startMinute = start)))) }; Minute("End", window.endMinute) { end -> if (end != window.startMinute) change(WeeklySchedule(value.days + (day to window.copy(endMinute = end)))) } } } }; if (value.days.isNotEmpty()) { val source = value.days.values.first(); Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { names.forEachIndexed { index, name -> AssistChip({ change(WeeklySchedule(value.days + ((index + 1) to source))) }, { Text("Copy to $name") }) } } }; OutlinedButton({ change(null) }) { Text("Use always active") } }
@Composable private fun Minute(title: String, minute: Int, change: (Int) -> Unit) { var text by remember(minute) { mutableStateOf(time(minute)) }; OutlinedTextField(text, { entered -> text = entered; parse(entered)?.let(change) }, { Text(title) }, Modifier.width(112.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
private fun time(minute: Int) = "%02d:%02d".format(Locale.US, minute / 60, minute % 60)
private fun parse(text: String): Int? { val parts = text.split(":"); if (parts.size != 2) return null; return ((parts[0].toIntOrNull() ?: return null) * 60 + (parts[1].toIntOrNull() ?: return null)).takeIf { it in 0..1439 } }
private fun summary(schedule: WeeklySchedule?) = if (schedule == null) "always active" else if (schedule.days.isEmpty()) "no active days" else "scheduled"
@OptIn(ExperimentalMaterial3Api::class) @Composable private fun Header(title: String, back: () -> Unit) = CenterAlignedTopAppBar({ Text(title) }, navigationIcon = { if (title != "Speedbreaker") TextButton(back) { Text("Back") } })
@Composable private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize()) { Header(title, {}); Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
private data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable)
private fun discover(context: Context): List<LaunchableApp> { val pm = context.packageManager; val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName; val input = AndroidSettings.Secure.getString(context.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/'); val inputs = context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }.orEmpty(); val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage; val denied = setOfNotNull(context.packageName, home, input, dialer) + inputs + setOf("com.android.settings", "com.android.systemui", "com.google.android.permissioncontroller", "com.android.permissioncontroller", "com.android.packageinstaller"); return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL).mapNotNull { r -> val info = r.activityInfo ?: return@mapNotNull null; val pkg = info.packageName; if (pkg in denied || safety(pkg)) null else LaunchableApp(pkg, r.loadLabel(pm).toString(), r.loadIcon(pm)) }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) } }
private fun safety(pkg: String) = listOf("systemui", "permissioncontroller", "packageinstaller", "emergency", "dialer", "incall", "telecom", "inputmethod").any { it in pkg.lowercase(Locale.US) }
private fun label(context: Context, pkg: String) = try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
@Composable private fun AppIcon(app: LaunchableApp) { val image = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }; Image(image, app.label, Modifier.size(40.dp)) }
