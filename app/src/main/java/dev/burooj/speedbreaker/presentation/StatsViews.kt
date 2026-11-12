package dev.burooj.speedbreaker.presentation

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.model.BreathChoices
import dev.burooj.speedbreaker.model.BreathEnding
import dev.burooj.speedbreaker.model.BreathStats
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.presentation.components.AppGlyph
import dev.burooj.speedbreaker.presentation.components.Eyebrow
import dev.burooj.speedbreaker.presentation.components.Glyphs
import dev.burooj.speedbreaker.presentation.components.Page
import dev.burooj.speedbreaker.presentation.components.Section
import dev.burooj.speedbreaker.presentation.components.appCatalog

/* ---------- Home's header ---------- */

/**
 * Home while breaths work: the status as a pill that carries the breathing mark, then the time
 * breathed as the page's one large figure, with the breath count and today's time beneath a rule.
 * Numbers are set alone and their units are small labels, so nothing reads as a sentence. Before
 * the first breath it says so and names what starts one.
 */
@Composable
internal fun BreathedHeader(
    stats: BreathStats,
    today: String,
    appCount: Int,
    restingUntil: String?,
    notificationsAllowed: Boolean,
    firstAppLabel: String?,
    onShowApps: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp)) {
            StatusPill(
                text = statusPillText(appCount, restingUntil),
                description = statusPillDescription(appCount, restingUntil),
                resting = restingUntil != null,
                onClick = onShowApps,
            )
            Spacer(Modifier.height(14.dp))
            if (stats.isEmpty) {
                Text("No breaths yet", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Open ${firstAppLabel ?: "a chosen app"} and your first one begins.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(Modifier.clearAndSetSemantics { contentDescription = breathedDescription(stats, today) }) {
                    Eyebrow("Breathed")
                    Figure(
                        parts = breathFigure(stats.totalMs),
                        valueStyle = MaterialTheme.typography.displayMedium,
                        unitStyle = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    FigureRow(
                        listOf(
                            "Breaths" to listOf(FigurePart(formatCount(stats.totalBreaths))),
                            "Today" to breathFigure(stats.todayMsOn(today)),
                        ),
                    )
                }
            }
            pauseNote(restingUntil, notificationsAllowed)?.let { note ->
                Spacer(Modifier.height(14.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Moss while breaths can happen, plain while every app is outside its hours. Tapping it shows the apps. */
@Composable
private fun StatusPill(text: String, description: String, resting: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (resting) colors.surfaceContainerHighest else colors.tertiaryContainer,
        contentColor = if (resting) colors.onSurfaceVariant else colors.onTertiaryContainer,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreathingMark(Modifier.size(22.dp), still = resting)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The app's mark, breathing slowly: the sign that Speedbreaker is working. It holds still when
 * [still] is set, when the system's animations are off, and in previews and screenshots.
 */
@Composable
internal fun BreathingMark(modifier: Modifier = Modifier, still: Boolean = false, color: Color = LocalContentColor.current) {
    val animationsOff = remember { !ValueAnimator.areAnimatorsEnabled() }
    val held = still || animationsOff || LocalStillBreath.current
    val swell = if (held) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "status mark")
        val value by transition.animateFloat(
            initialValue = 0.76f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(MARK_HALF_BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "swell",
        )
        value
    }
    Canvas(modifier) {
        val outer = size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.16f), radius = outer * swell)
        drawCircle(color.copy(alpha = 0.85f), radius = outer * 0.6f * swell, style = Stroke(width = 1.2.dp.toPx()))
        drawCircle(color, radius = outer * 0.18f)
    }
}

private const val MARK_HALF_BREATH_MS = 2_600

/** A number with its unit as a small label on the same baseline; long figures wrap between parts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Figure(parts: List<FigurePart>, valueStyle: TextStyle, unitStyle: TextStyle, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            Row {
                Text(part.value, style = valueStyle, modifier = Modifier.alignByBaseline())
                if (part.unit.isNotEmpty()) {
                    Text(
                        text = part.unit,
                        style = unitStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 5.dp).alignByBaseline(),
                    )
                }
            }
        }
    }
}

/** Labelled lesser figures side by side, a hairline between them. */
@Composable
private fun FigureRow(figures: List<Pair<String, List<FigurePart>>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        figures.forEachIndexed { index, (label, parts) ->
            if (index > 0) {
                VerticalDivider(
                    modifier = Modifier.padding(end = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(label)
                Figure(parts, MaterialTheme.typography.headlineSmall, MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/* ---------- What followed a breath ---------- */

/**
 * Home's summary of what followed the breaths: one thin bar, the counts beside their names, and
 * the detours taken most. The whole card opens the full page. It is descriptive only: plain
 * counts, no percentages, and no ending is drawn as better than another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoicesCard(choices: BreathChoices, settings: Settings, onOpen: () -> Unit) {
    val catalog = appCatalog()
    val detours = detourTallies(settings, choices, catalog::label)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .clickable(onClickLabel = "Open your breaths", role = Role.Button, onClick = onOpen)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("After a breath", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    Glyphs.Chevron,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            SplitBar(choices, height = 10.dp)
            Spacer(Modifier.height(14.dp))
            if (choices.total == 0L) {
                Text(
                    text = "What you choose is counted from your next breath.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Legend(endingCounts(choices, everyEnding = false), columns = if (largeText()) 1 else 2)
            }
            val taken = detours.filter { it.count > 0 }.take(HOME_DETOUR_CHIPS)
            if (taken.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    taken.forEach { DetourChip(it) }
                    val more = detours.size - taken.size
                    if (more > 0) {
                        Text(
                            text = "+$more more",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun largeText(): Boolean = LocalDensity.current.fontScale > 1.3f

@Composable
private fun DetourChip(tally: DetourTally) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "${tally.label} ${formatCount(tally.count)}" },
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyph(tally.packageName, tally.label, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(formatCount(tally.count), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun endingColor(ending: BreathEnding): Color {
    val colors = MaterialTheme.colorScheme
    return when (ending) {
        BreathEnding.Left -> colors.tertiary
        BreathEnding.Continued -> colors.primary
        BreathEnding.Detoured -> lerp(colors.tertiary, colors.surfaceContainer, 0.45f)
        BreathEnding.TimeOff -> colors.outline
        BreathEnding.Other -> colors.outlineVariant
    }
}

/** One bar split by how breaths ended. Decoration: the counts beside it carry the meaning. */
@Composable
private fun SplitBar(choices: BreathChoices, height: Dp) {
    val total = choices.total
    val shape = RoundedCornerShape(height / 2)
    if (total == 0L) {
        Box(Modifier.fillMaxWidth().height(height).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        return
    }
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ENDING_ORDER.forEach { ending ->
            val count = choices.count(ending)
            if (count > 0) {
                // A sliver stays wide enough to see.
                val share = maxOf(count.toFloat() / total, 0.025f)
                Box(Modifier.weight(share).fillMaxHeight().clip(shape).background(endingColor(ending)))
            }
        }
    }
}

@Composable
private fun Legend(entries: List<Pair<BreathEnding, Long>>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { (ending, count) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(endingColor(ending)))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = ending.label(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatCount(count), style = MaterialTheme.typography.labelLarge)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ---------- The full page ---------- */

/**
 * Everything counted, on one page behind Home's card: the three figures, how breaths ended,
 * every chosen detour with how often it was taken, and the time breathed on each of the last
 * seven days. It ends by saying since when, and that it stays on the phone.
 */
@Composable
internal fun StatsScreen(
    stats: BreathStats,
    choices: BreathChoices,
    settings: Settings,
    today: String,
    onBack: () -> Unit,
) {
    val catalog = appCatalog()
    val detours = detourTallies(settings, choices, catalog::label)
    Page(title = "Your breaths", onBack = onBack, eyebrow = "Speedbreaker") {
        Section(title = null) {
            FigureRow(
                figures = listOf(
                    "Breathed" to breathFigure(stats.totalMs),
                    "Breaths" to listOf(FigurePart(formatCount(stats.totalBreaths))),
                    "Today" to breathFigure(stats.todayMsOn(today)),
                ),
                modifier = Modifier
                    .padding(20.dp)
                    .clearAndSetSemantics { contentDescription = breathedDescription(stats, today) },
            )
        }

        Section(
            title = "After a breath",
            footnote = if (choices.total == 0L) "What you choose is counted from your next breath." else null,
        ) {
            Column(Modifier.padding(20.dp)) {
                SplitBar(choices, height = 16.dp)
                Spacer(Modifier.height(16.dp))
                Legend(endingCounts(choices, everyEnding = true), columns = 1)
            }
        }

        Section(
            title = "Detours taken",
            footnote = if (detours.isEmpty()) null else "A detour you remove everywhere takes its count with it.",
        ) {
            if (detours.isEmpty()) {
                Text(
                    text = "No detours are chosen. Add some under Settings → Detours.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                val most = detours.maxOf { it.count }
                Column(Modifier.padding(vertical = 8.dp)) {
                    detours.forEach { DetourBar(it, most) }
                }
            }
        }

        Section(title = "This week", footnote = "Time breathed each day.") {
            WeekBars(weekBars(choices, today), Modifier.padding(20.dp))
        }

        val since = countedSince(choices)
        Text(
            text = if (since == null) {
                "What follows a breath, your detours and the week are counted from your next breath. They stay on this phone."
            } else {
                "What followed a breath, your detours and the week are counted since $since. They stay on " +
                    "this phone, and Reset breathing total in Settings clears them."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun DetourBar(tally: DetourTally, most: Long) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = if (tally.count == 0L) {
                    "${tally.label}, not taken yet"
                } else {
                    "${tally.label}, taken ${formatCount(tally.count)} ${if (tally.count == 1L) "time" else "times"}"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(tally.packageName, tally.label, size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = tally.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tally.count == 0L) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.42f),
        )
        Box(
            Modifier
                .weight(0.58f)
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest),
        ) {
            if (tally.count > 0 && most > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(tally.count.toFloat() / most)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(endingColor(BreathEnding.Detoured)),
                )
            }
        }
        Text(
            text = formatCount(tally.count),
            style = MaterialTheme.typography.labelLarge,
            color = if (tally.count == 0L) colors.onSurfaceVariant else colors.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 36.dp).padding(start = 12.dp),
        )
    }
}

/** Seven bars, oldest first, today in moss. Heights compare the days with each other, nothing more. */
@Composable
private fun WeekBars(bars: List<DayBar>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val most = bars.maxOfOrNull { it.ms } ?: 0L
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = weekDescription(bars) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bars.forEach { bar ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.BottomCenter) {
                    // A quiet day keeps a stub, so the week always reads as seven days.
                    val share = if (most > 0 && bar.ms > 0) (bar.ms.toFloat() / most).coerceAtLeast(0.08f) else 0.04f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(share)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(
                                when {
                                    bar.ms <= 0 -> colors.surfaceContainerHighest
                                    bar.isToday -> colors.tertiary
                                    else -> colors.outline
                                },
                            ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = bar.letter,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (bar.isToday) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }
}
