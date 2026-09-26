package dev.burooj.speedbreaker.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Choice
import dev.burooj.speedbreaker.enforcement.Trigger
import dev.burooj.speedbreaker.presentation.components.AppGlyph
import dev.burooj.speedbreaker.presentation.components.Eyebrow
import dev.burooj.speedbreaker.presentation.components.pressShape
import dev.burooj.speedbreaker.presentation.components.pressShapeDp
import dev.burooj.speedbreaker.presentation.theme.Motion
import dev.burooj.speedbreaker.presentation.theme.SpeedbreakerTheme
import kotlinx.coroutines.delay
import kotlin.math.ceil

internal data class RedirectDestination(
    val packageName: String,
    val label: String,
)

/*
 * The breath is the whole screen for the first eight seconds. When the hard
 * lock lifts, the orb draws back, the reflection arrives, and only then do
 * the choices rise into place — one idea at a time.
 */
@Composable
internal fun BreakerOverlay(
    breaker: Breaker,
    targetLabel: String,
    nowElapsedMs: Long,
    pauseTokensLeft: Int,
    pauseResetLabel: String,
    pauseAvailable: Boolean,
    redirects: List<RedirectDestination>,
    motionEnabled: Boolean,
    onChoice: (Choice) -> Unit,
) = key(breaker.packageName, breaker.startedElapsedMs) {
    val elapsedMs = (nowElapsedMs - breaker.startedElapsedMs).coerceAtLeast(0L)
    val breathTotalMs = breaker.breathSeconds * 1_000L
    val moment = BreathMoment(
        elapsedMs = elapsedMs,
        unlocked = elapsedMs >= HARD_LOCK_MS,
        breathRemainingMs = (breathTotalMs - elapsedMs).coerceAtLeast(0L),
        progress = (elapsedMs.toFloat() / breathTotalMs).coerceIn(0f, 1f),
    )

    SpeedbreakerTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val layout = OverlayLayout.from(maxWidth, maxHeight)
                val header: @Composable () -> Unit = {
                    OverlayHeader(
                        targetLabel = targetLabel,
                        trigger = breaker.trigger,
                        unlocked = moment.unlocked,
                        pauseTokensLeft = pauseTokensLeft,
                        pauseResetLabel = pauseResetLabel,
                        pauseAvailable = pauseAvailable,
                        motionEnabled = motionEnabled,
                        onPause = { onChoice(Choice.Pause) },
                    )
                }
                val breath: @Composable () -> Unit = {
                    BreathColumn(moment, layout, motionEnabled)
                }
                val choices: @Composable () -> Unit = {
                    Choices(
                        moment = moment,
                        redirects = redirects.takeIf { it.size == REDIRECT_COUNT }.orEmpty(),
                        motionEnabled = motionEnabled,
                        onChoice = onChoice,
                    )
                }
                if (layout.sideBySide) {
                    SideBySide(maxHeight, header, breath, choices, moment.unlocked)
                } else {
                    Stacked(maxHeight, header, breath, choices)
                }
            }
        }
    }
}

private data class BreathMoment(
    val elapsedMs: Long,
    val unlocked: Boolean,
    val breathRemainingMs: Long,
    val progress: Float,
) {
    val breathComplete: Boolean get() = breathRemainingMs == 0L
    val inhaling: Boolean get() = (elapsedMs / BREATH_HALF_MS) % 2 == 0L
}

private data class OverlayLayout(
    val sideBySide: Boolean,
    val lockedOrb: Dp,
    val openOrb: Dp,
    val showCue: Boolean,
) {
    companion object {
        fun from(width: Dp, height: Dp): OverlayLayout {
            val sideBySide = width > height && width >= 560.dp
            val short = height < 520.dp
            val basis = if (sideBySide) minOf(width * 0.4f, height * 0.62f) else minOf(width * 0.66f, height * 0.36f)
            val locked = basis.coerceIn(88.dp, 300.dp)
            val open = when {
                sideBySide -> locked
                short -> (locked * 0.5f).coerceAtLeast(72.dp)
                else -> (locked * 0.62f).coerceAtLeast(96.dp)
            }
            return OverlayLayout(sideBySide, locked, open, showCue = !short || sideBySide)
        }
    }
}

@Composable
private fun Stacked(
    maxHeight: Dp,
    header: @Composable () -> Unit,
    breath: @Composable () -> Unit,
    choices: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        header()
        Box(Modifier.padding(vertical = 20.dp), contentAlignment = Alignment.Center) { breath() }
        Box(Modifier.widthIn(max = 520.dp).fillMaxWidth()) { choices() }
    }
}

@Composable
private fun SideBySide(
    maxHeight: Dp,
    header: @Composable () -> Unit,
    breath: @Composable () -> Unit,
    choices: @Composable () -> Unit,
    unlocked: Boolean,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
        header()
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { breath() }
            if (unlocked) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight - 88.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { choices() }
            }
        }
    }
}

/* ---------- Header ---------- */

@Composable
private fun OverlayHeader(
    targetLabel: String,
    trigger: Trigger,
    unlocked: Boolean,
    pauseTokensLeft: Int,
    pauseResetLabel: String,
    pauseAvailable: Boolean,
    motionEnabled: Boolean,
    onPause: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Eyebrow(targetLabel)
            Text(
                text = if (trigger == Trigger.OPENING) "Opening" else "Checking in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Reveal(visible = unlocked, delayMs = 900, motionEnabled = motionEnabled, rise = false) {
            PausePill(pauseTokensLeft, pauseResetLabel, pauseAvailable, onPause)
        }
    }
}

@Composable
private fun PausePill(
    tokensLeft: Int,
    resetLabel: String,
    available: Boolean,
    onPause: () -> Unit,
) {
    val enabled = tokensLeft > 0 && available
    val label = when {
        tokensLeft <= 0 -> "No pauses left · $resetLabel"
        !available -> "Pause needs notifications"
        else -> "Pause 15 min · $tokensLeft left"
    }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onPause,
        enabled = enabled,
        shape = pressShape(interaction),
        interactionSource = interaction,
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .padding(start = 12.dp)
            .widthIn(max = 220.dp)
            .heightIn(min = 40.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/* ---------- Breath ---------- */

@Composable
private fun BreathColumn(moment: BreathMoment, layout: OverlayLayout, motionEnabled: Boolean) {
    val orbSize by animateDpAsState(
        targetValue = if (moment.unlocked) layout.openOrb else layout.lockedOrb,
        animationSpec = Motion.orSnap(motionEnabled, Motion.spatialSlow()),
        label = "orb size",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BreathOrb(
            size = orbSize,
            elapsedMs = moment.elapsedMs,
            progress = moment.progress,
            breathComplete = moment.breathComplete,
            motionEnabled = motionEnabled,
            modifier = Modifier.semantics {
                contentDescription = if (moment.breathComplete) {
                    "Breath complete"
                } else {
                    "Breathing. ${secondsLabel(moment.breathRemainingMs)} remaining"
                }
            },
        )
        if (layout.showCue) {
            Box(Modifier.padding(top = 20.dp).height(28.dp), contentAlignment = Alignment.Center) {
                BreathCue(moment, motionEnabled)
            }
        }
    }
}

@Composable
private fun BreathCue(moment: BreathMoment, motionEnabled: Boolean) {
    val cue = when {
        moment.breathComplete -> ""
        !motionEnabled -> "Breathe slowly"
        moment.inhaling -> "Breathe in"
        else -> "Breathe out"
    }
    Crossfade(
        targetState = cue,
        animationSpec = Motion.orSnap(motionEnabled, Motion.effectsSlow()),
        label = "breath cue",
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BreathOrb(
    size: Dp,
    elapsedMs: Long,
    progress: Float,
    breathComplete: Boolean,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val swell = if (LocalStillBreath.current) {
        stillSwell(elapsedMs)
    } else if (motionEnabled) {
        // Anchor the loop to the breaker's start so the cue words and the orb agree.
        val startOffset = remember { (elapsedMs % (BREATH_HALF_MS * 2)).toInt() }
        val transition = rememberInfiniteTransition(label = "breath")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(BREATH_HALF_MS.toInt(), easing = BreathEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(startOffset, StartOffsetType.FastForward),
            ),
            label = "swell",
        )
        value
    } else {
        0.6f
    }
    val shownProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (motionEnabled) tween(TICK_SMOOTHING_MS, easing = LinearEasing) else snap(),
        label = "breath progress",
    )
    val arcAlpha by animateFloatAsState(
        targetValue = if (breathComplete) 0f else 1f,
        animationSpec = Motion.orSnap(motionEnabled, Motion.effectsSlow()),
        label = "arc fade",
    )
    val ink = MaterialTheme.colorScheme.onBackground
    val track = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.size(size)) {
        val outer = this.size.minDimension / 2f
        val arcStroke = 2.dp.toPx()
        val arcRadius = outer - arcStroke
        val room = arcRadius - 10.dp.toPx()
        val disc = room * (0.56f + 0.44f * swell)

        drawCircle(color = ink.copy(alpha = 0.035f), radius = room)
        drawCircle(color = ink.copy(alpha = 0.07f + 0.05f * swell), radius = disc)
        drawCircle(
            color = ink.copy(alpha = 0.55f),
            radius = disc,
            style = Stroke(width = 1.25.dp.toPx()),
        )
        drawCircle(color = ink.copy(alpha = 0.85f), radius = 3.dp.toPx())

        if (arcAlpha > 0f) {
            val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2, arcRadius * 2)
            drawArc(
                color = track.copy(alpha = arcAlpha),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = arcStroke),
            )
            drawArc(
                color = ink.copy(alpha = 0.8f * arcAlpha),
                startAngle = -90f,
                sweepAngle = 360f * shownProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = arcStroke, cap = StrokeCap.Round),
            )
        }
    }
}

/* ---------- Reflection and choices ---------- */

@Composable
private fun Choices(
    moment: BreathMoment,
    redirects: List<RedirectDestination>,
    motionEnabled: Boolean,
    onChoice: (Choice) -> Unit,
) {
    // The block's height springs open so the orb glides up instead of jumping;
    // inside it, each idea fades in after the one before.
    val block = remember { MutableTransitionState(moment.unlocked) }
    block.targetState = moment.unlocked
    AnimatedVisibility(
        visibleState = block,
        enter = if (motionEnabled) {
            expandVertically(Motion.spatialSlow(), expandFrom = Alignment.Top)
        } else {
            EnterTransition.None
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Reveal(visible = true, initiallyVisible = false, delayMs = 150, motionEnabled = motionEnabled) {
                Text(
                    text = "What are you here for?",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp)
                        .semantics { heading() },
                )
            }
            Reveal(visible = true, initiallyVisible = false, delayMs = 650, motionEnabled = motionEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChoiceButton(
                        text = if (moment.breathComplete) {
                            "Continue"
                        } else {
                            "Continue in ${secondsLabel(moment.breathRemainingMs)}"
                        },
                        enabled = moment.breathComplete,
                        onClick = { onChoice(Choice.Continue) },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceButton(
                        text = "Leave",
                        enabled = true,
                        onClick = { onChoice(Choice.Leave) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (redirects.isNotEmpty()) {
                Reveal(visible = true, initiallyVisible = false, delayMs = 950, motionEnabled = motionEnabled) {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        Eyebrow("Or go to", Modifier.padding(start = 4.dp, bottom = 10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            redirects.forEach { destination ->
                                RedirectTile(
                                    destination = destination,
                                    onClick = { onChoice(Choice.Redirect(destination.packageName)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Continue and Leave share one weight: neither is the "right" answer. */
@Composable
private fun ChoiceButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 60.dp),
        shape = pressShape(interaction),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun RedirectTile(
    destination: RedirectDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = pressShapeDp(interaction, rest = 20.dp, pressed = 12.dp),
        interactionSource = interaction,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .heightIn(min = 92.dp)
            .semantics { onClick(label = "Open ${destination.label}") { onClick(); true } },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppGlyph(destination.packageName, destination.label, size = 36.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Fades (and, with motion on, gently rises) content in after [delayMs].
 * Springs have no built-in delay, so the stagger holds the target back instead.
 */
@Composable
private fun Reveal(
    visible: Boolean,
    delayMs: Int,
    motionEnabled: Boolean,
    rise: Boolean = true,
    initiallyVisible: Boolean = visible,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(initiallyVisible) }
    val stagger = motionEnabled && !LocalStillBreath.current
    LaunchedEffect(visible) {
        if (visible && stagger && !state.currentState) delay(delayMs.toLong())
        state.targetState = visible
    }
    val enter = if (!motionEnabled) {
        fadeIn(Motion.effectsFast())
    } else if (rise) {
        fadeIn(Motion.effectsSlow()) + slideInVertically(Motion.spatial()) { it / 5 }
    } else {
        fadeIn(Motion.effectsSlow())
    }
    AnimatedVisibility(visibleState = state, enter = enter, content = content)
}

/**
 * Holds the breath at the exact point [elapsedMs] implies instead of animating.
 * Previews and screenshot renders set this: an endless animation never lets a
 * test clock settle.
 */
internal val LocalStillBreath = staticCompositionLocalOf { false }

private fun stillSwell(elapsedMs: Long): Float {
    val half = BREATH_HALF_MS.toFloat()
    val cycle = elapsedMs % (BREATH_HALF_MS * 2)
    val fraction = if (cycle < BREATH_HALF_MS) cycle / half else 2f - cycle / half
    return BreathEasing.transform(fraction)
}

private fun secondsLabel(milliseconds: Long): String =
    ceil(milliseconds / 1_000.0).toInt().coerceAtLeast(0).let { "${it}s" }

private val BreathEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
private const val BREATH_HALF_MS = 4_000L
private const val TICK_SMOOTHING_MS = 250
private const val HARD_LOCK_MS = 8_000L
private const val REDIRECT_COUNT = 4
