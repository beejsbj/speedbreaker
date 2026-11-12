package dev.burooj.speedbreaker.presentation.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/* ---------- App identity ---------- */

/** Resolves installed-app labels and icons; previews substitute a fixed catalog. */
internal interface AppCatalog {
    fun label(packageName: String): String
    fun icon(packageName: String): ImageBitmap?
}

internal val LocalAppCatalog = compositionLocalOf<AppCatalog?> { null }

@Composable
internal fun appCatalog(): AppCatalog {
    LocalAppCatalog.current?.let { return it }
    val context = LocalContext.current
    return remember(context) { PackageManagerCatalog(context.applicationContext) }
}

private class PackageManagerCatalog(private val context: Context) : AppCatalog {
    private val labels = mutableMapOf<String, String>()
    private val icons = mutableMapOf<String, ImageBitmap?>()

    override fun label(packageName: String): String = labels.getOrPut(packageName) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    override fun icon(packageName: String): ImageBitmap? = icons.getOrPut(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(144, 144).asImageBitmap()
        }.getOrNull()
    }
}

/** The app's own icon, or a quiet monogram when none is available. */
@Composable
internal fun AppGlyph(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val icon = appCatalog().icon(packageName)
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ---------- Page structure ---------- */

/**
 * A settings page: quiet top bar, serif title, and a single scrolling column. A page may pass a
 * null [title] and its own [header] instead; the eyebrow then carries the heading semantics.
 * A page with no way back can put one [action] at the end of the bar; its eyebrow then sits
 * in the bar beside it.
 */
@Composable
internal fun Page(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    action: (@Composable () -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    val eyebrowInBar = eyebrow != null && onBack == null && action != null
    val eyebrowHeading = if (title == null) Modifier.semantics { heading() } else Modifier
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(Modifier.heightIn(min = 56.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Glyphs.Back, contentDescription = "Back")
                    }
                }
                if (eyebrowInBar) {
                    Eyebrow(eyebrow.orEmpty(), Modifier.weight(1f).then(eyebrowHeading))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                action?.invoke()
            }
            if (eyebrowInBar) {
                Spacer(Modifier.height(12.dp))
            } else if (eyebrow != null) {
                Eyebrow(
                    eyebrow,
                    Modifier
                        .padding(top = 8.dp, bottom = if (title == null) 14.dp else 6.dp)
                        .then(eyebrowHeading),
                )
            }
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .padding(top = if (eyebrow == null || eyebrowInBar) 8.dp else 0.dp, bottom = 24.dp)
                        .semantics { heading() },
                )
            }
            if (header != null) {
                Column(Modifier.padding(bottom = 28.dp), content = header)
            }
            Column(verticalArrangement = Arrangement.spacedBy(28.dp), content = content)
        }
    }
}

internal val ContentMaxWidth = 560.dp

/** Small spaced capitals that name a group without competing with it. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * A titled group, after Expressive's segmented lists: each row or [Tile] is its own soft surface
 * with a hair of space between them. The group's outer corners are large and the corners where
 * tiles meet are small, so a group still reads as one thing.
 */
@Composable
internal fun Section(
    title: String?,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        if (title != null) {
            Eyebrow(title, Modifier.padding(start = 4.dp, bottom = 10.dp).semantics { heading() })
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
            content = content,
        )
        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
            )
        }
    }
}

private val SegmentGap = 2.dp
private val TileShape = RoundedCornerShape(6.dp)

/** One segment of a [Section] for anything that is not a row: a slider, a chart, a step. */
@Composable
internal fun Tile(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/** A hairline between parts of one [Tile]. Rows in a [Section] need none: the gap separates them. */
@Composable
internal fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** A row that opens something. The trailing value reads before the chevron. */
@Composable
internal fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    RowFrame(modifier.clickable(role = Role.Button, onClick = onClick), leading) {
        RowText(title, summary, Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        trailing?.invoke(this)
        Icon(
            Glyphs.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 8.dp).size(18.dp),
        )
    }
}

/** A row that does something where it stands, so it carries no chevron. */
@Composable
internal fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    RowFrame(modifier.clickable(role = Role.Button, onClick = onClick), leading) {
        RowText(title, summary, Modifier.weight(1f))
    }
}

/** A row whose whole surface toggles a switch. */
@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    RowFrame(
        modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        leading,
    ) {
        RowText(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
internal fun RowFrame(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        // Its own tile, painted before the click so the ripple stays inside the tile's corners.
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        content()
    }
}

@Composable
internal fun RowText(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ---------- Actions ---------- */

/** The one filled action on a screen. */
@Composable
internal fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = pressShape(interaction),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A round tonal button for the one thing a page keeps in its top bar. */
@Composable
internal fun BarAction(glyph: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(glyph, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** A soft, tonal action for everything that is not the one thing to do next. */
@Composable
internal fun QuietAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = pressShape(interaction),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        elevation = null,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A row's line glyph on a quiet round ground, so a list can be scanned by shape as well as by word. */
@Composable
internal fun RowIcon(glyph: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            glyph,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/* ---------- Glyphs ---------- */

/** Hand-drawn line glyphs, so the app needs no icon library. */
internal object Glyphs {
    val Back: ImageVector by lazy {
        line("Back") {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }
    val Chevron: ImageVector by lazy {
        line("Chevron") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    }
    val Check: ImageVector by lazy {
        line("Check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    }
    val Plus: ImageVector by lazy {
        line("Plus") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    }
    val Settings: ImageVector by lazy {
        line("Settings") {
            moveTo(10.19f, 5.24f); lineTo(10.66f, 2.49f); lineTo(13.34f, 2.49f); lineTo(13.81f, 5.24f)
            lineTo(15.5f, 5.94f); lineTo(17.78f, 4.33f); lineTo(19.67f, 6.22f); lineTo(18.06f, 8.5f)
            lineTo(18.76f, 10.19f); lineTo(21.51f, 10.66f); lineTo(21.51f, 13.34f); lineTo(18.76f, 13.81f)
            lineTo(18.06f, 15.5f); lineTo(19.67f, 17.78f); lineTo(17.78f, 19.67f); lineTo(15.5f, 18.06f)
            lineTo(13.81f, 18.76f); lineTo(13.34f, 21.51f); lineTo(10.66f, 21.51f); lineTo(10.19f, 18.76f)
            lineTo(8.5f, 18.06f); lineTo(6.22f, 19.67f); lineTo(4.33f, 17.78f); lineTo(5.94f, 15.5f)
            lineTo(5.24f, 13.81f); lineTo(2.49f, 13.34f); lineTo(2.49f, 10.66f); lineTo(5.24f, 10.19f)
            lineTo(5.94f, 8.5f); lineTo(4.33f, 6.22f); lineTo(6.22f, 4.33f); lineTo(8.5f, 5.94f); close()
            moveTo(12f, 9.1f)
            arcTo(2.9f, 2.9f, 0f, true, true, 11.99f, 9.1f)
        }
    }
    val Breath: ImageVector by lazy {
        line("Breath") {
            moveTo(12f, 3.5f); arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
            moveTo(12f, 9.5f); arcTo(2.5f, 2.5f, 0f, true, true, 11.99f, 9.5f)
        }
    }
    val Detour: ImageVector by lazy {
        line("Detour") {
            moveTo(5f, 19f); lineTo(5f, 12.5f); quadTo(5f, 8.5f, 9f, 8.5f); lineTo(18.5f, 8.5f)
            moveTo(14.8f, 4.8f); lineTo(18.5f, 8.5f); lineTo(14.8f, 12.2f)
        }
    }
    val Bell: ImageVector by lazy {
        line("Bell") {
            moveTo(6.5f, 16.5f); lineTo(6.5f, 10.5f); arcTo(5.5f, 5.5f, 0f, false, true, 17.5f, 10.5f)
            lineTo(17.5f, 16.5f); lineTo(19f, 18f); lineTo(5f, 18f); close()
            moveTo(10.5f, 20.8f); lineTo(13.5f, 20.8f)
        }
    }
    val Phone: ImageVector by lazy {
        line("Phone") {
            moveTo(4f, 11f); curveTo(6.5f, 5.8f, 17.5f, 5.8f, 20f, 11f); lineTo(18f, 13.8f)
            lineTo(14.6f, 12.2f); lineTo(14.6f, 9.9f); curveTo(13f, 9.3f, 11f, 9.3f, 9.4f, 9.9f)
            lineTo(9.4f, 12.2f); lineTo(6f, 13.8f); close()
            moveTo(7.5f, 18.5f); lineTo(16.5f, 18.5f)
        }
    }
    val Battery: ImageVector by lazy {
        line("Battery") {
            moveTo(4f, 8.5f); lineTo(17.5f, 8.5f); lineTo(17.5f, 15.5f); lineTo(4f, 15.5f); close()
            moveTo(20.2f, 11f); lineTo(20.2f, 13f)
            moveTo(7f, 11.3f); lineTo(7f, 12.7f); moveTo(10f, 11.3f); lineTo(10f, 12.7f)
        }
    }
    val Refresh: ImageVector by lazy {
        line("Refresh") {
            moveTo(17f, 7.1f); arcTo(7f, 7f, 0f, true, false, 19f, 12f)
            moveTo(17.3f, 3.4f); lineTo(17.3f, 7.4f); lineTo(13.3f, 7.4f)
        }
    }
    val Reset: ImageVector by lazy {
        line("Reset") {
            moveTo(7f, 7.1f); arcTo(7f, 7f, 0f, true, true, 5f, 12f)
            moveTo(6.7f, 3.4f); lineTo(6.7f, 7.4f); lineTo(10.7f, 7.4f)
        }
    }
    val Help: ImageVector by lazy {
        line("Help") {
            moveTo(12f, 3.5f); arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
            moveTo(9.6f, 9.7f); curveTo(9.6f, 7f, 14.4f, 7f, 14.4f, 9.7f); curveTo(14.4f, 11.4f, 12f, 11.3f, 12f, 13.4f)
            moveTo(12f, 16.4f); lineTo(12f, 16.5f)
        }
    }
    val Shield: ImageVector by lazy {
        line("Shield") {
            moveTo(12f, 3.5f); lineTo(18.5f, 6f); lineTo(18.5f, 11.5f); curveTo(18.5f, 16f, 15.5f, 19f, 12f, 20.5f)
            curveTo(8.5f, 19f, 5.5f, 16f, 5.5f, 11.5f); lineTo(5.5f, 6f); close()
        }
    }
    val Heart: ImageVector by lazy {
        line("Heart") {
            moveTo(12f, 19.5f); curveTo(6f, 15.5f, 4f, 12.5f, 4f, 9.5f); curveTo(4f, 5.5f, 9.5f, 4.5f, 12f, 8.2f)
            curveTo(14.5f, 4.5f, 20f, 5.5f, 20f, 9.5f); curveTo(20f, 12.5f, 18f, 15.5f, 12f, 19.5f); close()
        }
    }
    val Close: ImageVector by lazy {
        line("Close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    }

    val Clock: ImageVector by lazy {
        line("Clock") {
            moveTo(12f, 3.5f)
            arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
            moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f)
        }
    }
    val Keyboard: ImageVector by lazy {
        line("Keyboard") {
            moveTo(4f, 6.5f); lineTo(20f, 6.5f); lineTo(20f, 17.5f); lineTo(4f, 17.5f); close()
            moveTo(7.5f, 10f); lineTo(8f, 10f); moveTo(11.75f, 10f); lineTo(12.25f, 10f)
            moveTo(16f, 10f); lineTo(16.5f, 10f); moveTo(8.5f, 14f); lineTo(15.5f, 14f)
        }
    }

    private fun line(
        name: String,
        block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()
}
