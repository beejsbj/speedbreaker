package dev.burooj.speedbreaker.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Ink and ivory. Two neutrals carry almost everything; moss marks "on" and
 * brick marks "needs repair". Nothing else is colored, so those two signals
 * stay legible without raising their voice.
 */
private object Palette {
    val Ink = Color(0xFF1C1B17)
    val InkSoft = Color(0xFF5F5A4F)
    val InkFaint = Color(0xFF8C8677)
    val Ivory = Color(0xFFF7F2E8)
    val IvoryDeep = Color(0xFFF0EADD)
    val Parchment = Color(0xFFE9E2D3)
    val ParchmentDeep = Color(0xFFE1D8C6)
    val Hairline = Color(0xFFDCD3C2)
    val Moss = Color(0xFF4F6450)
    val MossWash = Color(0xFFDFE5D6)
    val Brick = Color(0xFF9A4A36)
    val BrickWash = Color(0xFFF2DDD3)

    val NightInk = Color(0xFF131311)
    val NightRaised = Color(0xFF1C1B18)
    val NightRaisedHigh = Color(0xFF252420)
    val NightRaisedHighest = Color(0xFF2E2D28)
    val NightHairline = Color(0xFF3A3832)
    val NightIvory = Color(0xFFECE5D6)
    val NightIvorySoft = Color(0xFFABA595)
    val NightIvoryFaint = Color(0xFF7D786C)
    val NightMoss = Color(0xFFA9BDA6)
    val NightMossWash = Color(0xFF28302A)
    val NightBrick = Color(0xFFE2A591)
    val NightBrickWash = Color(0xFF3A2620)
}

private val LightColors = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.Ivory,
    primaryContainer = Palette.Parchment,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.InkSoft,
    onSecondary = Palette.Ivory,
    secondaryContainer = Palette.ParchmentDeep,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Moss,
    onTertiary = Palette.Ivory,
    tertiaryContainer = Palette.MossWash,
    onTertiaryContainer = Palette.Ink,
    error = Palette.Brick,
    onError = Palette.Ivory,
    errorContainer = Palette.BrickWash,
    onErrorContainer = Color(0xFF4D2016),
    background = Palette.Ivory,
    onBackground = Palette.Ink,
    surface = Palette.Ivory,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Parchment,
    onSurfaceVariant = Palette.InkSoft,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFBF8F2),
    surfaceContainerLow = Palette.IvoryDeep,
    surfaceContainer = Palette.IvoryDeep,
    surfaceContainerHigh = Palette.Parchment,
    surfaceContainerHighest = Palette.ParchmentDeep,
    outline = Palette.InkFaint,
    outlineVariant = Palette.Hairline,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Ivory,
    scrim = Color(0x66131311),
)

private val DarkColors = darkColorScheme(
    primary = Palette.NightIvory,
    onPrimary = Palette.NightInk,
    primaryContainer = Palette.NightRaisedHighest,
    onPrimaryContainer = Palette.NightIvory,
    secondary = Palette.NightIvorySoft,
    onSecondary = Palette.NightInk,
    secondaryContainer = Palette.NightRaisedHighest,
    onSecondaryContainer = Palette.NightIvory,
    tertiary = Palette.NightMoss,
    onTertiary = Palette.NightInk,
    tertiaryContainer = Palette.NightMossWash,
    onTertiaryContainer = Palette.NightIvory,
    error = Palette.NightBrick,
    onError = Palette.NightInk,
    errorContainer = Palette.NightBrickWash,
    onErrorContainer = Palette.NightIvory,
    background = Palette.NightInk,
    onBackground = Palette.NightIvory,
    surface = Palette.NightInk,
    onSurface = Palette.NightIvory,
    surfaceVariant = Palette.NightRaisedHigh,
    onSurfaceVariant = Palette.NightIvorySoft,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0E0E0C),
    surfaceContainerLow = Palette.NightRaised,
    surfaceContainer = Palette.NightRaised,
    surfaceContainerHigh = Palette.NightRaisedHigh,
    surfaceContainerHighest = Palette.NightRaisedHighest,
    outline = Palette.NightIvoryFaint,
    outlineVariant = Palette.NightHairline,
    inverseSurface = Palette.NightIvory,
    inverseOnSurface = Palette.NightInk,
    scrim = Color(0x99000000),
)

/*
 * A serif voice for the few sentences that ask something of the reader —
 * the reflection, page titles, the consent — and the system sans for
 * everything operational.
 */
private val Voice = FontFamily.Serif
private val Base = Typography()

private val SpeedbreakerTypography = Typography(
    displaySmall = TextStyle(fontFamily = Voice, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Voice, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Voice, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Voice, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
)

private val SpeedbreakerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
internal fun SpeedbreakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SpeedbreakerTypography,
        shapes = SpeedbreakerShapes,
        content = content,
    )
}
