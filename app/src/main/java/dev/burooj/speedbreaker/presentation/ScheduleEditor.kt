package dev.burooj.speedbreaker.presentation

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import dev.burooj.speedbreaker.presentation.components.Glyphs
import dev.burooj.speedbreaker.presentation.components.RowDivider

internal typealias ScheduleTransform = (WeeklySchedule?) -> WeeklySchedule?

/*
 * A week is shown as groups of days that share the same hours — "Mon–Fri,
 * 9 AM to 5 PM" — instead of seven rows to fill and copy between. Changing a
 * group's hours changes every day in it; tapping a day moves it into the
 * group. Days in no group are off.
 */

/** Days that share one window. Derived from the schedule; never stored. */
internal data class HoursGroup(val window: TimeWindow, val days: Set<Int>)

internal fun hoursGroups(schedule: WeeklySchedule): List<HoursGroup> =
    schedule.days.entries
        .groupBy({ it.value }, { it.key })
        .map { (window, days) -> HoursGroup(window, days.toSet()) }
        .sortedBy { it.days.min() }

/** Every day in [from] gets [to]; if [to] matches another group, the two merge. */
internal fun WeeklySchedule.retime(from: TimeWindow, to: TimeWindow): WeeklySchedule =
    WeeklySchedule(days.mapValues { (_, window) -> if (window == from) to else window })

/** Puts [day] into the group with [window] (moving it from any other), or turns it off. */
internal fun WeeklySchedule.assign(day: Int, window: TimeWindow, include: Boolean): WeeklySchedule =
    WeeklySchedule(if (include) days + (day to window) else days - day)

internal fun WeeklySchedule.withoutGroup(window: TimeWindow): WeeklySchedule =
    WeeklySchedule(days.filterValues { it != window })

/** Hours offered for a new group: all day, unless a group already has those. */
internal fun newGroupWindow(schedule: WeeklySchedule): TimeWindow =
    listOf(ALL_DAY_WINDOW, DEFAULT_WINDOW, EVENING_WINDOW).firstOrNull { it !in schedule.days.values }
        ?: TimeWindow(startMinute = 10 * 60, endMinute = 22 * 60)

@Composable
internal fun WeekEditor(
    schedule: WeeklySchedule,
    onTransform: (ScheduleTransform) -> Unit,
) {
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val groups = hoursGroups(schedule)
    // A new group has no days yet, so it lives only here until one is tapped.
    var draft by remember { mutableStateOf<TimeWindow?>(null) }
    var editing by remember { mutableStateOf<TimeEdit?>(null) }
    val offDays = (1..7).toSet() - schedule.days.keys

    fun edit(transform: (WeeklySchedule) -> WeeklySchedule) =
        onTransform { current -> transform(current ?: WeeklySchedule()) }

    Column {
        groups.forEachIndexed { index, group ->
            if (index > 0) RowDivider()
            HoursGroupEditor(
                window = group.window,
                days = group.days,
                elsewhere = schedule.days.keys - group.days,
                use24Hour = use24Hour,
                removable = groups.size > 1,
                onEditStart = { editing = TimeEdit(group.window, isEnd = false) },
                onEditEnd = { editing = TimeEdit(group.window, isEnd = true) },
                onToggleDay = { day, include -> edit { it.assign(day, group.window, include) } },
                onRemove = { edit { it.withoutGroup(group.window) } },
            )
        }
        draft?.let { window ->
            if (groups.isNotEmpty()) RowDivider()
            HoursGroupEditor(
                window = window,
                days = emptySet(),
                elsewhere = schedule.days.keys,
                use24Hour = use24Hour,
                removable = true,
                onEditStart = { editing = TimeEdit(window, isEnd = false, isDraft = true) },
                onEditEnd = { editing = TimeEdit(window, isEnd = true, isDraft = true) },
                onToggleDay = { day, include ->
                    if (include) {
                        draft = null
                        edit { it.assign(day, window, include = true) }
                    }
                },
                onRemove = { draft = null },
                hint = "Tap the days that get these hours.",
            )
        }
        RowDivider()
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    offDays.isEmpty() -> "Every day has hours."
                    offDays.size == 7 -> "No days yet — nothing is paused."
                    else -> "Off ${daysSummary(offDays).inSentence()}: apps open freely."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (draft == null && offDays.isNotEmpty()) {
                TextButton(onClick = { draft = newGroupWindow(schedule) }) {
                    Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Other hours")
                }
            }
        }
    }

    editing?.let { current ->
        val window = current.window
        TimePickerDialog(
            title = if (current.isEnd) "Until" else "From",
            initialMinute = if (current.isEnd) window.endMinute else window.startMinute,
            otherMinute = if (current.isEnd) window.startMinute else window.endMinute,
            isEnd = current.isEnd,
            use24Hour = use24Hour,
            onDismiss = { editing = null },
            onConfirm = { minute ->
                editing = null
                val updated = if (current.isEnd) window.copy(endMinute = minute) else window.copy(startMinute = minute)
                if (current.isDraft) draft = updated else edit { it.retime(window, updated) }
            },
        )
    }
}

private data class TimeEdit(val window: TimeWindow, val isEnd: Boolean, val isDraft: Boolean = false)

@Composable
private fun HoursGroupEditor(
    window: TimeWindow,
    days: Set<Int>,
    elsewhere: Set<Int>,
    use24Hour: Boolean,
    removable: Boolean,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onToggleDay: (day: Int, include: Boolean) -> Unit,
    onRemove: () -> Unit,
    hint: String? = null,
) {
    Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeChip(formatMinute(window.startMinute, use24Hour), "Start", onEditStart)
            Text(
                "to",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            TimeChip(formatMinute(window.endMinute, use24Hour), "End", onEditEnd)
            Spacer(Modifier.weight(1f))
            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Glyphs.Close,
                        contentDescription = "Remove these hours",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        val note = hint ?: when {
            window.startMinute == 0 && window.endMinute == 1440 -> "All day"
            window.endMinute < window.startMinute -> "Runs past midnight"
            else -> null
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        DayToggles(
            active = days,
            elsewhere = elsewhere,
            onToggle = onToggleDay,
            modifier = Modifier.padding(top = 14.dp, end = 12.dp),
        )
    }
}

/**
 * Seven day toggles. Filled days belong here; outlined days have other
 * hours (tapping moves them here); plain days are off.
 */
@Composable
internal fun DayToggles(
    active: Set<Int>,
    onToggle: (day: Int, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    elsewhere: Set<Int> = emptySet(),
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAY_NAMES.forEachIndexed { index, name ->
            val day = index + 1
            val on = day in active
            val other = !on && day in elsewhere
            Surface(
                shape = CircleShape,
                color = when {
                    on -> colors.primary
                    other -> Color.Transparent
                    else -> colors.surfaceContainerHighest
                },
                contentColor = when {
                    on -> colors.onPrimary
                    other -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                border = if (other) BorderStroke(1.dp, colors.outline) else null,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .heightIn(min = 40.dp)
                    .semantics {
                        contentDescription = name
                        selected = on
                        stateDescription = when {
                            on -> "In these hours"
                            other -> "Has other hours"
                            else -> "Off"
                        }
                    }
                    .clickable(role = Role.Checkbox) { onToggle(day, !on) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.take(1), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, role: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = "Change ${role.lowercase()} time", onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    otherMinute: Int,
    isEnd: Boolean,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinute / 60) % 24,
        initialMinute = initialMinute % 60,
        is24Hour = use24Hour,
    )
    var typing by remember { mutableStateOf(false) }
    // A picked end of 00:00 means "until the end of the day", stored as 24:00.
    val picked = (state.hour * 60 + state.minute).let { if (isEnd && it == 0) 1440 else it }
    val clashes = picked == otherMinute
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePickerPanel(state, typing, clashes, isEnd) },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }, enabled = !clashes) { Text("Set") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { typing = !typing }) {
                    Icon(
                        if (typing) Glyphs.Clock else Glyphs.Keyboard,
                        contentDescription = if (typing) "Pick on a clock" else "Type the time",
                    )
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}

/** The Material 3 clock dial, or its typed-in twin, sharing one state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerPanel(state: TimePickerState, typing: Boolean, clashes: Boolean, isEnd: Boolean) {
    val colors = TimePickerDefaults.colors(
        clockDialColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        selectorColor = MaterialTheme.colorScheme.primary,
        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (typing) TimeInput(state = state, colors = colors) else TimePicker(state = state, colors = colors)
        Text(
            text = when {
                clashes -> "Start and end can’t be the same time."
                isEnd -> "An end before the start runs past midnight. Midnight means end of day."
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (clashes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

internal val DEFAULT_WINDOW = TimeWindow(startMinute = 9 * 60, endMinute = 17 * 60)
private val ALL_DAY_WINDOW = TimeWindow(startMinute = 0, endMinute = 1440)
private val EVENING_WINDOW = TimeWindow(startMinute = 18 * 60, endMinute = 23 * 60)
