package dev.burooj.speedbreaker.presentation

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.model.TimeWindow
import dev.burooj.speedbreaker.model.WeeklySchedule
import dev.burooj.speedbreaker.presentation.components.RowDivider

internal typealias ScheduleTransform = (WeeklySchedule?) -> WeeklySchedule?

/**
 * Days as a row of toggles, then one line per active day with its window.
 * Times open a picker; an end earlier than the start runs past midnight.
 */
@Composable
internal fun WeekEditor(
    schedule: WeeklySchedule,
    onTransform: (ScheduleTransform) -> Unit,
) {
    var editing by remember { mutableStateOf<TimeEdit?>(null) }
    var copyFrom by remember { mutableStateOf<Int?>(null) }
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)

    Column {
        DayToggles(
            active = schedule.days.keys,
            onToggle = { day, enabled ->
                onTransform { current ->
                    val days = (current ?: WeeklySchedule()).days.toMutableMap()
                    if (enabled) days.putIfAbsent(day, DEFAULT_WINDOW) else days.remove(day)
                    WeeklySchedule(days)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        schedule.days.toSortedMap().forEach { (day, window) ->
            RowDivider()
            DayWindowRow(
                day = day,
                window = window,
                use24Hour = use24Hour,
                onEditStart = { editing = TimeEdit(day, isEnd = false) },
                onEditEnd = { editing = TimeEdit(day, isEnd = true) },
                onCopy = { copyFrom = day },
            )
        }
    }

    editing?.let { edit ->
        val window = schedule.days[edit.day]
        if (window == null) {
            editing = null
            return@let
        }
        TimePickerDialog(
            title = "${WEEKDAY_NAMES[edit.day - 1]} ${if (edit.isEnd) "end" else "start"}",
            initialMinute = if (edit.isEnd) window.endMinute else window.startMinute,
            otherMinute = if (edit.isEnd) window.startMinute else window.endMinute,
            isEnd = edit.isEnd,
            use24Hour = use24Hour,
            onDismiss = { editing = null },
            onConfirm = { minute ->
                editing = null
                onTransform { current ->
                    val existing = current?.days?.get(edit.day)
                    when {
                        existing == null -> current
                        edit.isEnd && minute != existing.startMinute ->
                            WeeklySchedule(current.days + (edit.day to existing.copy(endMinute = minute)))
                        !edit.isEnd && minute != existing.endMinute ->
                            WeeklySchedule(current.days + (edit.day to existing.copy(startMinute = minute)))
                        else -> current
                    }
                }
            },
        )
    }

    copyFrom?.let { source ->
        val window = schedule.days[source]
        if (window == null) {
            copyFrom = null
            return@let
        }
        CopyDaysDialog(
            source = source,
            window = window,
            use24Hour = use24Hour,
            initiallyActive = schedule.days.keys,
            onDismiss = { copyFrom = null },
            onConfirm = { targets ->
                copyFrom = null
                onTransform { current ->
                    val sourceWindow = current?.days?.get(source)
                    if (sourceWindow == null) current else WeeklySchedule(current.days + targets.associateWith { sourceWindow })
                }
            },
        )
    }
}

private data class TimeEdit(val day: Int, val isEnd: Boolean)

@Composable
internal fun DayToggles(
    active: Set<Int>,
    onToggle: (day: Int, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAY_NAMES.forEachIndexed { index, name ->
            val day = index + 1
            val on = day in active
            Surface(
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .heightIn(min = 40.dp)
                    .semantics {
                        contentDescription = name
                        selected = on
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
private fun DayWindowRow(
    day: Int,
    window: TimeWindow,
    use24Hour: Boolean,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = WEEKDAY_NAMES[day - 1],
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(48.dp),
        )
        TimeChip(formatMinute(window.startMinute, use24Hour), "Start", onEditStart)
        Text(
            "–",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        TimeChip(formatMinute(window.endMinute, use24Hour), "End", onEditEnd)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onCopy) { Text("Copy") }
        }
    }
    if (window.endMinute < window.startMinute) {
        Text(
            text = "Runs past midnight",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 68.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun TimeChip(text: String, role: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClickLabel = "Change ${role.lowercase()} time", onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
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
    // A picked end of 00:00 means "until the end of the day", stored as 24:00.
    val picked = (state.hour * 60 + state.minute).let { if (isEnd && it == 0) END_OF_DAY else it }
    val clashes = picked == otherMinute
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                if (clashes) {
                    Text(
                        "Start and end can’t be the same time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }, enabled = !clashes) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}

@Composable
private fun CopyDaysDialog(
    source: Int,
    window: TimeWindow,
    use24Hour: Boolean,
    initiallyActive: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
) {
    var targets by remember { mutableStateOf(initiallyActive - source) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy ${WEEKDAY_NAMES[source - 1]}’s hours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${formatMinute(window.startMinute, use24Hour)} – ${formatMinute(window.endMinute, use24Hour)} to:")
                DayToggles(
                    active = targets + source,
                    onToggle = { day, enabled ->
                        if (day != source) targets = if (enabled) targets + day else targets - day
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(targets) }, enabled = targets.isNotEmpty()) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}

internal val DEFAULT_WINDOW = TimeWindow(startMinute = 9 * 60, endMinute = 17 * 60)
private const val END_OF_DAY = 1440
