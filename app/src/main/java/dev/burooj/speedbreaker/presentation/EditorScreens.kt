package dev.burooj.speedbreaker.presentation

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.presentation.components.AppGlyph
import dev.burooj.speedbreaker.presentation.components.NavRow
import dev.burooj.speedbreaker.presentation.components.Page
import dev.burooj.speedbreaker.presentation.components.QuietAction
import dev.burooj.speedbreaker.presentation.components.RowDivider
import dev.burooj.speedbreaker.presentation.components.Section
import dev.burooj.speedbreaker.presentation.components.ToggleRow
import dev.burooj.speedbreaker.presentation.components.appCatalog
import kotlin.math.roundToInt

internal typealias PolicyTransform = (AppPolicy) -> AppPolicy

/* ---------- Defaults ---------- */

@Composable
internal fun DefaultsScreen(
    settings: Settings,
    onBack: () -> Unit,
    onBreathChange: (Int) -> Unit,
    onScheduleTransform: (ScheduleTransform) -> Unit,
    onRedirects: () -> Unit,
) = Page(title = "Defaults", onBack = onBack, eyebrow = "Every app") {
    Section(
        title = "Breath",
        footnote = "The first 8 seconds are always locked. Continue unlocks when the breath ends.",
    ) {
        ValueSlider(
            current = settings.breathSeconds,
            range = 8..60,
            unit = "seconds",
            onCommit = onBreathChange,
        )
    }

    Section(
        title = "Redirects",
        footnote = "Four places to go instead, shown under Continue and Leave.",
    ) {
        RedirectSlots(settings.redirects, onRedirects)
    }

    Section(
        title = "Schedule",
        footnote = if (settings.schedule == null) {
            "Pauses apply at all hours."
        } else {
            "Outside these hours, chosen apps open freely."
        },
    ) {
        ModeSwitch(
            options = listOf("Always on", "Scheduled"),
            selected = if (settings.schedule == null) 0 else 1,
            onSelect = { index ->
                onScheduleTransform { current ->
                    when {
                        index == 0 -> null
                        current == null -> startingSchedule()
                        else -> current
                    }
                }
            },
        )
        settings.schedule?.let { WeekEditor(it, onScheduleTransform) }
    }
}

/* ---------- One app ---------- */

@Composable
internal fun AppSettingsScreen(
    packageName: String,
    settings: Settings,
    onBack: () -> Unit,
    onPolicyTransform: (PolicyTransform) -> Unit,
    onRedirects: (List<String>) -> Unit,
    onRemove: () -> Unit,
) {
    val policy = settings.apps[packageName] ?: return
    val label = appCatalog().label(packageName)
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    Page(title = label, onBack = onBack, eyebrow = "App") {
        Section(title = "While you use it") {
            ToggleRow(
                title = "Check in again",
                summary = "Another pause after continuous use",
                checked = policy.continuousSeconds != null,
                onCheckedChange = { enabled ->
                    onPolicyTransform { current ->
                        current.copy(continuousSeconds = if (enabled) DEFAULT_INTERVAL_SECONDS else null)
                    }
                },
            )
            policy.continuousSeconds?.let { seconds ->
                RowDivider()
                ValueSlider(
                    current = (seconds / 60).coerceIn(1, 120),
                    range = 1..120,
                    unit = "minutes",
                    prefix = "Every",
                    onCommit = { minutes ->
                        onPolicyTransform { current -> current.copy(continuousSeconds = minutes * 60) }
                    },
                )
            }
        }

        val schedule = policy.schedule
        val scheduleMode = when {
            schedule == null -> 0
            isAllDay(schedule) -> 1
            else -> 2
        }
        Section(
            title = "Schedule",
            footnote = when (scheduleMode) {
                0 -> "Following the default: ${scheduleSummary(settings.schedule, use24Hour).inSentence()}."
                1 -> "Pauses apply at all hours, whatever the default says."
                else -> "Replaces the default schedule for this app."
            },
        ) {
            ModeSwitch(
                options = listOf("Default", "Always on", "Custom"),
                selected = scheduleMode,
                onSelect = { index ->
                    onPolicyTransform { current ->
                        current.copy(
                            schedule = when (index) {
                                0 -> null
                                1 -> allDaySchedule()
                                else -> current.schedule?.takeUnless(::isAllDay)
                                    ?: settings.schedule
                                    ?: startingSchedule()
                            },
                        )
                    }
                },
            )
            if (scheduleMode == 2 && schedule != null) {
                WeekEditor(schedule) { transform ->
                    onPolicyTransform { current ->
                        current.copy(schedule = transform(current.schedule) ?: allDaySchedule())
                    }
                }
            }
        }

        val redirects = policy.redirects
        Section(
            title = "Redirects",
            footnote = when {
                redirects == null -> "Using the default four."
                redirects.size == 4 -> "Replaces all four default redirects for this app."
                else -> "This app shows no redirects until four are chosen."
            },
        ) {
            ModeSwitch(
                options = listOf("Default", "Custom"),
                selected = if (redirects == null) 0 else 1,
                onSelect = { index ->
                    onPolicyTransform { current ->
                        current.copy(
                            redirects = when {
                                index == 0 -> null
                                current.redirects != null -> current.redirects
                                else -> emptyList()
                            },
                        )
                    }
                },
            )
            RedirectSlots(redirects ?: settings.redirects, onEdit = redirects?.let { { onRedirects(it) } })
        }

        Text(
            text = "Breath length, the 60-second return window, and two daily pauses are shared by every app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        QuietAction("Stop pausing $label", onRemove)
    }
}

/* ---------- Shared pieces ---------- */

/** A large readout above a slider. Commits only when the thumb is released. */
@Composable
private fun ValueSlider(
    current: Int,
    range: IntRange,
    unit: String,
    onCommit: (Int) -> Unit,
    prefix: String? = null,
) {
    var draft by remember(current) { mutableFloatStateOf(current.toFloat()) }
    val shown = draft.roundToInt()
    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (prefix != null) {
                Text(
                    "$prefix ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text("$shown", style = MaterialTheme.typography.headlineLarge)
            Text(
                " $unit",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics { contentDescription = "$shown $unit" },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = index == selected,
                onClick = { if (index != selected) onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The four redirect places as they will appear, then the edit row.
 * With [onEdit] null the slots are read-only (an inherited set).
 */
@Composable
internal fun RedirectSlots(redirects: List<String>, onEdit: (() -> Unit)?) {
    val catalog = appCatalog()
    val filled = redirects.takeIf { it.size == 4 }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(4) { index ->
            val packageName = filled?.get(index)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onEdit != null) Modifier.clickable(role = Role.Button, onClick = onEdit) else Modifier),
            ) {
                if (packageName != null) {
                    val label = catalog.label(packageName)
                    AppGlyph(packageName, label, size = 44.dp)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
                    )
                } else {
                    EmptySlot()
                    Text(
                        text = "Empty",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
    if (onEdit != null) {
        RowDivider()
        NavRow(
            title = if (filled == null) "Choose four apps" else "Change redirects",
            summary = if (filled == null) "None shown until all four are set" else null,
            onClick = onEdit,
        )
    }
}

@Composable
private fun EmptySlot() {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(44.dp)) {
        drawCircle(
            color = outline,
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            ),
        )
    }
}

private const val DEFAULT_INTERVAL_SECONDS = 10 * 60
