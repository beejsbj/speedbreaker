package dev.burooj.speedbreaker.presentation

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import dev.burooj.speedbreaker.R
import dev.burooj.speedbreaker.model.BreathStats
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.model.localDayKey
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.presentation.components.AppGlyph
import dev.burooj.speedbreaker.presentation.components.ContentMaxWidth
import dev.burooj.speedbreaker.presentation.components.Eyebrow
import dev.burooj.speedbreaker.presentation.components.Glyphs
import dev.burooj.speedbreaker.presentation.components.MorphingLoader
import dev.burooj.speedbreaker.presentation.components.NavRow
import dev.burooj.speedbreaker.presentation.components.Page
import dev.burooj.speedbreaker.presentation.components.PrimaryAction
import dev.burooj.speedbreaker.presentation.components.QuietAction
import dev.burooj.speedbreaker.presentation.components.RowDivider
import dev.burooj.speedbreaker.presentation.components.Section
import dev.burooj.speedbreaker.presentation.components.StatusDot
import dev.burooj.speedbreaker.presentation.components.appCatalog

/* ---------- First run ---------- */

/**
 * Two short pages before anything is switched on. The first says what Speedbreaker does; the
 * second is the Accessibility disclosure, which has to be read and accepted and so cannot be
 * skipped. Neither shows again once it is accepted.
 */
@Composable
internal fun WelcomeScreen(
    onDecline: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    startOnDisclosure: Boolean = false,
    onAccept: () -> Unit,
) {
    var onDisclosure by rememberSaveable { mutableStateOf(startOnDisclosure) }
    // A fresh scroll position per page, so the disclosure always opens at its top.
    key(onDisclosure) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                if (onDisclosure) {
                    BackHandler { onDisclosure = false }
                    DisclosurePage(onAccept, onDecline, onPrivacy)
                } else {
                    IntroPage(onContinue = { onDisclosure = true })
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.IntroPage(onContinue: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    BreathMark(Modifier.size(72.dp))
    Spacer(Modifier.height(32.dp))
    Text(
        text = "Make the next tap intentional.",
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Speedbreaker puts a short breath between you and the apps you choose.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(36.dp))
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Promise(heading = "You choose the apps", body = "Pick the ones that pull at you. Nothing is chosen for you.")
        Promise(
            heading = "Opening one starts a breath",
            body = "Twelve seconds, unless you change it. For the first eight, there is only the breath.",
        )
        Promise(heading = "Then you decide", body = "Continue, leave, or go somewhere else.")
    }
    Spacer(Modifier.height(40.dp))
    PrimaryAction("Continue", onContinue, Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.DisclosurePage(onAccept: () -> Unit, onDecline: () -> Unit, onPrivacy: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Before you turn it on",
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Speedbreaker works through Android’s Accessibility service. This is what it does with it.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(36.dp))
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Promise(
            heading = "What it sees",
            body = "Which app is on screen, including system screens, so it can show the breath before " +
                "the apps you chose. It doesn’t read what is on the screen or what you type. " +
                "This stays on your phone and is not shared.",
        )
        Promise(
            heading = "The first eight seconds",
            body = "Back, Home, Recents, and the notification shade don’t close a breath. Calls that " +
                "Android reports, the dialer, the lock screen, and Android Settings still get through.",
        )
        Promise(
            heading = "What it keeps",
            body = "Your settings, today’s pauses, and a running total of breaths that you can reset. " +
                "No record of which apps you open or when, no answers, no analytics, no cloud.",
        )
    }
    Spacer(Modifier.height(40.dp))
    PrimaryAction("I understand — continue", onAccept, Modifier.fillMaxWidth())
    QuietAction("Not now — close", onDecline, Modifier.fillMaxWidth())
    QuietAction("Privacy policy", onPrivacy, Modifier.fillMaxWidth())
}

@Composable
private fun Promise(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(heading)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A still version of the overlay's breath, used as the app's mark. */
@Composable
internal fun BreathMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val outer = size.minDimension / 2f
        drawCircle(ink.copy(alpha = 0.05f), radius = outer)
        drawCircle(ink.copy(alpha = 0.10f), radius = outer * 0.68f)
        drawCircle(ink.copy(alpha = 0.6f), radius = outer * 0.68f, style = Stroke(width = 1.25.dp.toPx()))
        drawCircle(ink, radius = 2.5.dp.toPx())
    }
}

@Composable
internal fun LoadingScreen() {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MorphingLoader(size = 56.dp, still = LocalStillBreath.current)
            Spacer(Modifier.height(24.dp))
            Text(
                "Opening your settings…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StorageFailureScreen(error: String?) {
    Page(title = "Speedbreaker is off", onBack = null, eyebrow = "Speedbreaker") {
        Notice(
            tone = NoticeTone.Repair,
            text = error ?: "Local settings aren’t available, so protection can’t start.",
        )
        Text(
            "Restart the app after resolving the storage problem.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ---------- Home ---------- */

internal enum class Protection {
    /** Accessibility is off: nothing is paused. */
    Off,

    /** Accessibility is on but the service is not connected or reported an error. */
    Broken,

    /** Connected and able to pause chosen apps. */
    On,

    CallPermission,
    CallUnavailable,
    CallActive,
}

/**
 * Home reads top-down as: is it working, which apps, how it behaves, what
 * keeps it reliable. Setup is the same page — undone steps simply lead.
 * While breaths work and apps are chosen, the status and the breathing total
 * are the header; every other state keeps its serif title and repair notice.
 */
@Composable
internal fun HomeScreen(
    settings: Settings,
    protection: Protection,
    runtimeError: String?,
    notificationsAllowed: Boolean,
    onEnableAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onNotifications: () -> Unit,
    onChooseApps: () -> Unit,
    onDefaults: () -> Unit,
    onApp: (String) -> Unit,
    onCallAccess: () -> Unit = {},
    phoneNeedsSettings: Boolean = false,
    pauses: Map<String, PauseState> = emptyMap(),
    nowEpochMs: Long = System.currentTimeMillis(),
    onEndPause: (String) -> Unit = {},
    onSupport: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    supportLabel: String = "Buy me a coffee",
    supportSummary: String = "Opens ko-fi.com in your browser",
    supportAvailable: Boolean = true,
    stats: BreathStats = BreathStats(),
    onResetStats: () -> Unit = {},
    updateCard: (@Composable () -> Unit)? = null,
    onCheckUpdates: (() -> Unit)? = null,
) {
    val appCount = settings.apps.size
    val catalog = appCatalog()
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val zone = ZoneId.systemDefault()
    val today = localDayKey(nowEpochMs, zone)
    var confirmingReset by remember { mutableStateOf(false) }

    val healthy = protection == Protection.On && appCount > 0
    val setup = setupProgress(protection, hasApps = appCount > 0, hasBreathed = !stats.isEmpty)
    val title = if (healthy) {
        null
    } else if (setup != null) {
        setupTitle(setup)
    } else {
        when (protection) {
            Protection.Off -> "Off for now"
            Protection.Broken, Protection.CallUnavailable -> "Needs attention"
            Protection.CallPermission -> "One more step"
            Protection.CallActive -> "Giving your call room"
            Protection.On -> "Ready when you are"
        }
    }
    val header: (@Composable ColumnScope.() -> Unit)? = if (healthy) {
        {
            val note = homeStatusNote(
                restingUntil = restingUntil(settings, nowEpochMs, zone, use24Hour),
                notificationsAllowed = notificationsAllowed,
            )
            StatusLine(text = homeStatusText(note), description = homeStatusDescription(note))
            Spacer(Modifier.height(16.dp))
            BreathTotal(
                stats = stats,
                today = today,
                firstAppLabel = settings.apps.keys.singleOrNull()?.let(catalog::label),
            )
        }
    } else {
        null
    }

    Page(title = title, onBack = null, eyebrow = "Speedbreaker", header = header) {
        ActivePausesSection(pauses, nowEpochMs, onEndPause)

        if (setup != null) {
            SetupSteps(
                progress = setup,
                phoneNeedsSettings = phoneNeedsSettings,
                onEnableAccessibility = onEnableAccessibility,
                onOpenAppInfo = onOpenAppInfo,
                onCallAccess = onCallAccess,
                onChooseApps = onChooseApps,
            )
        } else {
            StatusPanel(
                protection = protection,
                runtimeError = runtimeError,
                hasApps = appCount > 0,
                phoneNeedsSettings = phoneNeedsSettings,
                onEnableAccessibility = onEnableAccessibility,
                onOpenAppInfo = onOpenAppInfo,
                onChooseApps = onChooseApps,
                onCallAccess = onCallAccess,
            )
        }

        // Repairs lead the page, so the total shrinks to one line after them.
        if (protection != Protection.On && !stats.isEmpty) {
            Text(
                text = compactBreathLine(stats),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        updateCard?.invoke()

        // While choosing apps is the open setup step, its button is the way in; a second row would repeat it.
        if (setup?.current != 3) Section(
            title = "Apps",
            footnote = if (appCount == 0) "Nothing is chosen until you pick it." else null,
        ) {
            settings.apps.entries
                .sortedBy { catalog.label(it.key).lowercase() }
                .forEach { (packageName, policy) ->
                    val label = catalog.label(packageName)
                    NavRow(
                        title = label,
                        summary = appSummary(policy, settings, use24Hour),
                        onClick = { onApp(packageName) },
                        leading = { AppGlyph(packageName, label) },
                    )
                    RowDivider()
                }
            NavRow(
                title = if (appCount == 0) "Choose apps" else "Add or remove apps",
                onClick = onChooseApps,
                leading = { LeadingGlyphCircle() },
            )
        }

        Section(title = "Defaults") {
            NavRow(
                title = defaultsTitle(settings),
                summary = defaultsSummary(settings, use24Hour),
                onClick = onDefaults,
            )
        }

        Section(title = "Reliability") {
            NavRow(
                title = "Pause notifications",
                summary = if (notificationsAllowed) {
                    "Allowed"
                } else {
                    "Needed to start a pause; End now is also here"
                },
                onClick = onNotifications,
                leading = {
                    StatusDot(
                        if (notificationsAllowed) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                },
            )
            RowDivider()
            NavRow(
                title = "Battery and background",
                summary = "On Samsung, set Battery to Unrestricted",
                onClick = onOpenAppInfo,
                leading = { StatusDot(Color.Transparent) },
            )
        }

        Column {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            if (supportAvailable) {
                FooterLink(supportLabel, onSupport, description = "$supportLabel. $supportSummary")
            }
            FooterLink("Privacy policy", onPrivacy, description = "Privacy policy. Opens the public policy in your browser")
            if (onCheckUpdates != null) {
                FooterLink("Check for updates", onCheckUpdates, description = "Check for updates. Asks GitHub for the latest release")
            }
            if (!stats.isEmpty) {
                FooterLink("Reset breathing total", { confirmingReset = true })
            }
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Reset your breathing total?") },
            text = {
                Text(
                    "The total, the breath count and today’s time go back to zero. This stays on " +
                        "this phone and can’t be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingReset = false; onResetStats() }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text("Keep it") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }
}

/** A moss dot and a plain word: Speedbreaker is on. The dot is decoration; the words are what TalkBack reads. */
@Composable
private fun StatusLine(text: String, description: String) {
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * How long you have breathed, as the page's one large number, with the breath count and today's
 * time beneath. Before the first breath it says so and names what starts one. Text wraps freely
 * at any size; it is never clipped or shrunk.
 */
@Composable
private fun BreathTotal(stats: BreathStats, today: String, firstAppLabel: String?) {
    val headline: String
    val subline: String
    val description: String
    if (stats.isEmpty) {
        headline = "No breaths yet"
        subline = "Open ${firstAppLabel ?: "a chosen app"} and your first one begins."
        description = "$headline. $subline"
    } else {
        headline = formatBreathTotal(stats.totalMs)
        subline = breathSubline(stats, today)
        description = "$headline breathed in total. ${subline.replace(" · ", ", ")}."
    }
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(headline, style = MaterialTheme.typography.displaySmall)
        Text(subline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A quiet text-only action at the foot of Home. */
@Composable
private fun FooterLink(text: String, onClick: () -> Unit, description: String = text) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun LeadingGlyphCircle() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StatusPanel(
    protection: Protection,
    runtimeError: String?,
    hasApps: Boolean,
    phoneNeedsSettings: Boolean,
    onEnableAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onChooseApps: () -> Unit,
    onCallAccess: () -> Unit,
) {
    when (protection) {
        Protection.Off -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Notice(
                tone = NoticeTone.Repair,
                text = "Speedbreaker’s Accessibility switch is off, so nothing is paused. Your settings are kept.",
            )
            AccessibilityActions(onEnableAccessibility, onOpenAppInfo)
        }
        Protection.Broken -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Notice(
                tone = NoticeTone.Repair,
                text = runtimeError ?: "Accessibility is on, but Speedbreaker hasn’t connected. " +
                    "Turning it off and on again usually helps.",
            )
            PrimaryAction("Open Accessibility", onEnableAccessibility, Modifier.fillMaxWidth())
        }
        Protection.CallPermission -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Notice(tone = NoticeTone.Repair, text = "Breaths are off until Phone permission is allowed. $PHONE_REASON")
            PhoneActions(phoneNeedsSettings, onCallAccess)
        }
        Protection.CallUnavailable -> Notice(
            tone = NoticeTone.Repair,
            text = "Android’s call-state check is unavailable, so breaths are off. " +
                "Your settings are kept. Try restarting your phone.",
        )
        Protection.CallActive -> Notice(
            tone = NoticeTone.Calm,
            text = "Breaths are suspended while Android reports a call or communication audio. " +
                "They resume when it ends.",
        )
        // With apps chosen, the header already says Speedbreaker is on.
        Protection.On -> if (!hasApps) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Accessibility is connected. Choose the apps that pull at you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryAction("Choose apps", onChooseApps, Modifier.fillMaxWidth())
            }
        }
    }
}

/* ---------- First-time setup ---------- */

/**
 * The three things a new person does before the first breath, in order. The step to do next is
 * open, with its reason and its one button; finished steps keep a moss tick. It gives way to the
 * ordinary status as soon as a breath has been taken, so a later repair never reads as setup.
 */
@Composable
private fun SetupSteps(
    progress: SetupProgress,
    phoneNeedsSettings: Boolean,
    onEnableAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onCallAccess: () -> Unit,
    onChooseApps: () -> Unit,
) {
    val serviceName = stringResource(R.string.accessibility_service_label)
    Section(title = null) {
        SetupStep(1, "Turn on Speedbreaker in Accessibility", progress.accessibility, progress.current == 1) {
            StepText(
                "This is how Speedbreaker sees which app is on screen and shows the breath. On the next " +
                    "screen, find “$serviceName” (often under Installed apps or Downloaded apps), " +
                    "switch it on, and come back. Android shows its full-control warning for every app " +
                    "of this kind.",
            )
            AccessibilityActions(onEnableAccessibility, onOpenAppInfo)
        }
        RowDivider()
        SetupStep(2, "Allow Phone permission", progress.phone, progress.current == 2) {
            StepText("$PHONE_REASON Breaths stay off until it’s allowed.")
            PhoneActions(phoneNeedsSettings, onCallAccess)
        }
        RowDivider()
        SetupStep(3, "Choose your apps", progress.apps, progress.current == 3) {
            StepText("Pick the apps that pull at you. Opening one starts a breath.")
            PrimaryAction("Choose apps", onChooseApps, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    done: Boolean,
    current: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val state = when {
        done -> "done"
        current -> "next"
        else -> "later"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = "Step $number of 3, $state. $title" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    done -> colors.tertiaryContainer
                    current -> colors.primary
                    else -> colors.surfaceContainerHighest
                },
                contentColor = when {
                    done -> colors.onTertiaryContainer
                    current -> colors.onPrimary
                    else -> colors.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (done) {
                        Icon(Glyphs.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    } else {
                        Text("$number", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (done || current) colors.onSurface else colors.onSurfaceVariant,
            )
        }
        if (current) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun StepText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The Accessibility button with the way round Android's restricted-setting block. */
@Composable
private fun ColumnScope.AccessibilityActions(onEnableAccessibility: () -> Unit, onOpenAppInfo: () -> Unit) {
    PrimaryAction("Turn on in Accessibility", onEnableAccessibility, Modifier.fillMaxWidth())
    Text(
        text = "If Android says the setting is restricted: open app info, tap ⋮ → Allow restricted settings, " +
            "confirm it’s you, then come back and turn it on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    QuietAction("Open app info", onOpenAppInfo)
}

/**
 * Asks for Phone permission while Android will still show its prompt. Once Android has stopped
 * asking, the same place says so and leads to the permission in app info instead.
 */
@Composable
private fun ColumnScope.PhoneActions(needsSettings: Boolean, onCallAccess: () -> Unit) {
    if (needsSettings) {
        Text(
            text = "Android won’t ask again here. Open app info, then Permissions → Phone → Allow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryAction("Open app info", onCallAccess, Modifier.fillMaxWidth())
    } else {
        PrimaryAction("Allow Phone permission", onCallAccess, Modifier.fillMaxWidth())
    }
}

private const val PHONE_REASON = "So a breath never covers an incoming call, Speedbreaker checks whether your " +
    "phone is ringing or in a call. Android words this as “make and manage phone calls”; Speedbreaker " +
    "can’t make calls and doesn’t read numbers, call history, or audio."

internal enum class NoticeTone { Calm, Repair }

/** A status sentence led by a dot: moss when all is well, brick when something needs fixing. */
@Composable
internal fun Notice(tone: NoticeTone, text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (tone == NoticeTone.Repair) colors.errorContainer else colors.tertiaryContainer,
        contentColor = if (tone == NoticeTone.Repair) colors.onErrorContainer else colors.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            StatusDot(
                color = if (tone == NoticeTone.Repair) colors.error else colors.tertiary,
                modifier = Modifier.padding(top = 7.dp),
            )
            Spacer(Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
