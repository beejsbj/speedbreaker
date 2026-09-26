package dev.burooj.speedbreaker.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import dev.burooj.speedbreaker.presentation.theme.Motion

/*
 * The few Material 3 Expressive behaviors this app adopts, built on stable
 * Compose until material3 1.5 is stable: shapes that answer a press, a
 * connected button group, and a shape-morphing loading indicator.
 */

/* ---------- Press shape morph ---------- */

/**
 * A rounded shape that squares off slightly while pressed and springs back
 * on release. Corners are percentages so a pill stays a pill at any height.
 */
@Composable
internal fun pressShape(
    interactionSource: MutableInteractionSource,
    restPercent: Float = 50f,
    pressedPercent: Float = 28f,
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val percent by animateFloatAsState(
        targetValue = if (pressed) pressedPercent else restPercent,
        animationSpec = Motion.spatialFast(),
        label = "press shape",
    )
    // CornerSize(Float) is pixels; the percent overload takes whole numbers.
    return RoundedCornerShape(CornerSize(percent.toInt()))
}

/** The same idea for fixed-radius surfaces such as tiles. */
@Composable
internal fun pressShapeDp(
    interactionSource: MutableInteractionSource,
    rest: Dp,
    pressed: Dp,
): Shape {
    val isPressed by interactionSource.collectIsPressedAsState()
    val radius by animateFloatAsState(
        targetValue = if (isPressed) pressed.value else rest.value,
        animationSpec = Motion.spatialFast(),
        label = "press radius",
    )
    return RoundedCornerShape(radius.dp)
}

/* ---------- Connected button group ---------- */

/**
 * Expressive's connected button group: segments sit 2dp apart with small
 * inner corners; the chosen one rounds fully into a pill, and every segment
 * squares off a little under the finger.
 */
@Composable
internal fun ConnectedChoice(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val outer = 50f
            val inner = when {
                pressed -> 8f
                chosen -> 50f
                else -> 12f
            }
            val start by animateFloatAsState(
                if (index == 0) outer else inner,
                Motion.spatialFast(),
                label = "start corners",
            )
            val end by animateFloatAsState(
                if (index == options.lastIndex) outer else inner,
                Motion.spatialFast(),
                label = "end corners",
            )
            val container by animateColorAsState(
                if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                Motion.effects(),
                label = "segment color",
            )
            val content by animateColorAsState(
                if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                Motion.effects(),
                label = "segment content",
            )
            Surface(
                selected = chosen,
                onClick = { if (!chosen) onSelect(index) },
                shape = RoundedCornerShape(
                    topStartPercent = start.toInt(),
                    bottomStartPercent = start.toInt(),
                    topEndPercent = end.toInt(),
                    bottomEndPercent = end.toInt(),
                ),
                color = container,
                contentColor = content,
                interactionSource = interaction,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton },
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* ---------- Loading indicator ---------- */

/**
 * A slow, turning shape that melts between soft polygons, after Expressive's
 * loading indicator — unhurried, in ink rather than accent color.
 */
@Composable
internal fun MorphingLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    still: Boolean = false,
) {
    val morphs = remember {
        val shapes = LoaderShapes
        shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) }
    }
    val progress: Float
    val turn: Float
    if (still) {
        progress = 0.7f
        turn = 20f
    } else {
        val transition = rememberInfiniteTransition(label = "loader")
        val cycle by transition.animateFloat(
            initialValue = 0f,
            targetValue = morphs.size.toFloat(),
            animationSpec = infiniteRepeatable(tween(MORPH_MS * morphs.size, easing = LinearEasing), RepeatMode.Restart),
            label = "morph cycle",
        )
        progress = cycle
        turn = cycle * 90f
    }
    val ink = MaterialTheme.colorScheme.onBackground
    val path = remember { android.graphics.Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier.size(size).semantics { contentDescription = "Loading" }) {
        val index = progress.toInt() % morphs.size
        val local = settle(progress - progress.toInt())
        path.rewind()
        morphs[index].toPath(local, path)
        val composePath = path.asComposePath()
        matrix.reset()
        matrix.scale(this.size.width, this.size.height)
        composePath.transform(matrix)
        rotate(turn) { drawPath(composePath, ink.copy(alpha = 0.85f)) }
    }
}

/** Holds each shape for a beat, then eases into the next. */
private fun settle(t: Float): Float {
    val moving = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
    return moving * moving * (3 - 2 * moving)
}

private val LoaderShapes: List<RoundedPolygon> by lazy {
    listOf(
        RoundedPolygon(numVertices = 8, rounding = CornerRounding(1f)),
        RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.72f, rounding = CornerRounding(0.35f)),
        RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.8f, rounding = CornerRounding(0.25f)),
        RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.45f)),
    ).map { it.normalized() }
}

private const val MORPH_MS = 1_300
