package dev.burooj.speedbreaker.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Choice
import dev.burooj.speedbreaker.enforcement.Trigger
import kotlin.math.ceil

internal data class RedirectDestination(
    val packageName: String,
    val label: String,
)

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
) {
    val elapsedMs = (nowElapsedMs - breaker.startedElapsedMs).coerceAtLeast(0L)
    val hardLockComplete = elapsedMs >= HARD_LOCK_MS
    val breathRemainingMs = (breaker.breathSeconds * 1_000L - elapsedMs).coerceAtLeast(0L)
    val continueEnabled = breathRemainingMs == 0L

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Ink,
            onPrimary = Ivory,
            surface = Ivory,
            onSurface = Ink,
            background = Ivory,
            onBackground = Ink,
        ),
    ) {
        Surface(color = Ivory, contentColor = Ink, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val compact = maxHeight < 520.dp
                val horizontalPadding = if (maxWidth < 420.dp) 18.dp else 32.dp
                val ringSize = when {
                    compact -> 92.dp
                    maxWidth < 420.dp -> 156.dp
                    else -> 188.dp
                }

                if (!hardLockComplete) {
                    LockedBreath(
                        targetLabel = targetLabel,
                        remainingMs = HARD_LOCK_MS - elapsedMs,
                        ringSize = ringSize,
                        compact = compact,
                        motionEnabled = motionEnabled,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = horizontalPadding),
                    )
                } else {
                    ChoiceStage(
                        breaker = breaker,
                        targetLabel = targetLabel,
                        breathRemainingMs = breathRemainingMs,
                        continueEnabled = continueEnabled,
                        pauseTokensLeft = pauseTokensLeft,
                        pauseResetLabel = pauseResetLabel,
                        pauseAvailable = pauseAvailable,
                        redirects = redirects.takeIf { it.size == 4 }.orEmpty(),
                        ringSize = ringSize,
                        compact = compact,
                        motionEnabled = motionEnabled,
                        scrollState = rememberScrollState(),
                        onChoice = onChoice,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedBreath(
    targetLabel: String,
    remainingMs: Long,
    ringSize: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TargetLabel(targetLabel)
        Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
        BreathRing(ringSize, motionEnabled)
        Spacer(Modifier.height(if (compact) 10.dp else 22.dp))
        Text(
            text = "Breathe",
            fontSize = if (compact) 25.sp else 31.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = secondsLabel(remainingMs),
            modifier = Modifier.padding(top = 6.dp),
            color = InkMuted,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun ChoiceStage(
    breaker: Breaker,
    targetLabel: String,
    breathRemainingMs: Long,
    continueEnabled: Boolean,
    pauseTokensLeft: Int,
    pauseResetLabel: String,
    pauseAvailable: Boolean,
    redirects: List<RedirectDestination>,
    ringSize: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    motionEnabled: Boolean,
    scrollState: ScrollState,
    onChoice: (Choice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        TextButton(
            onClick = { onChoice(Choice.Pause) },
            enabled = pauseTokensLeft > 0 && pauseAvailable,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text(
                text = if (pauseTokensLeft <= 0) {
                    "Pause · $pauseResetLabel"
                } else if (!pauseAvailable) {
                    "Enable notifications for Pause"
                } else {
                    "Pause 15m · $pauseTokensLeft left"
                },
                maxLines = 2,
                textAlign = TextAlign.End,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 54.dp, bottom = 12.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TargetLabel(targetLabel)
            Text(
                text = if (breaker.trigger == Trigger.OPENING) "Opening pause" else "Continuous-use pause",
                color = InkMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            BreathRing(ringSize, motionEnabled)
            Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
            Text(
                text = "What are you here for?",
                textAlign = TextAlign.Center,
                fontSize = if (compact) 23.sp else 28.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!continueEnabled) {
                Text(
                    text = "Continue in ${secondsLabel(breathRemainingMs)}",
                    color = InkMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(if (compact) 14.dp else 26.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onChoice(Choice.Continue) },
                    enabled = continueEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink,
                        contentColor = Ivory,
                        disabledContainerColor = Ink.copy(alpha = 0.10f),
                        disabledContentColor = Ink.copy(alpha = 0.38f),
                    ),
                ) {
                    Text("Continue", maxLines = 2, textAlign = TextAlign.Center)
                }
                Button(
                    onClick = { onChoice(Choice.Leave) },
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink,
                        contentColor = Ivory,
                    ),
                ) {
                    Text("Leave", maxLines = 2, textAlign = TextAlign.Center)
                }
            }
            if (redirects.size == 4) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    redirects.forEach { destination ->
                        OutlinedButton(
                            onClick = { onChoice(Choice.Redirect(destination.packageName)) },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = destination.label,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetLabel(label: String) {
    Text(
        text = label,
        color = InkMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BreathRing(size: androidx.compose.ui.unit.Dp, motionEnabled: Boolean) {
    val scale = if (motionEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "breathing ring")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 0.76f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4_000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath scale",
        )
        animatedScale
    } else {
        0.88f
    }
    val description = remember { "Breathing timer" }
    Canvas(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val radius = this.size.minDimension * 0.44f * scale
        drawCircle(
            color = Ink.copy(alpha = 0.12f),
            radius = radius,
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(
            color = Ink.copy(alpha = 0.72f),
            radius = radius,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(color = Ink, radius = 4.dp.toPx())
    }
}

private fun secondsLabel(milliseconds: Long): String =
    ceil(milliseconds / 1_000.0).toInt().coerceAtLeast(0).let { "$it sec" }

private val Ink = Color(0xFF171714)
private val InkMuted = Color(0x99171714)
private val Ivory = Color(0xFFF7F2E8)
private const val HARD_LOCK_MS = 8_000L
