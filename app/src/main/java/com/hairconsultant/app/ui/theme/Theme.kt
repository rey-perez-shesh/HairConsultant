package com.hairconsultant.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// primary = oxblood, reserved for each screen's one primary action; everything else is neutral.
private val LightColors = lightColorScheme(
    primary = Oxblood,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3DADE),
    onPrimaryContainer = Oxblood,
    secondary = Color(0xFF3A3A3A),
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantLight,
    onSecondaryContainer = InkLight,
    tertiary = WarnAmber,
    onTertiary = Color.White,
    background = GroundLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkMutedLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = GroundLight,
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = SurfaceVariantLight,
    outline = TileLine,
    outlineVariant = TileLine,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = OxbloodDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1620),
    onPrimaryContainer = Color(0xFFF3DADE),
    secondary = Color(0xFFC7C7C7),
    onSecondary = InkLight,
    secondaryContainer = ShellIndicatorDark,
    onSecondaryContainer = Color.White,
    tertiary = WarnAmberDark,
    onTertiary = Color(0xFF2A1A00),
    background = GroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = GroundDark,
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = Color(0xFF303032),
    outline = TileLineDark,
    outlineVariant = TileLineDark,
    error = ErrorRedDark
)

/** The shop colours Material's roles have no slot for: the shell, its indicator, the fit/warn marks, the image well. */
@Immutable
data class ShopColors(
    val shell: Color,
    val onShell: Color,
    val onShellMuted: Color,
    val shellLine: Color,
    val indicator: Color,
    val fit: Color,
    val warn: Color,
    val imageWell: Color
)

private val LightShopColors = ShopColors(ShellBlack, OnShell, OnShellMuted, ShellLine, ShellIndicator, MarkInk, WarnAmber, ImageWell)
private val DarkShopColors = ShopColors(ShellGrey, OnShell, OnShellMutedDark, ShellLineDark, ShellIndicatorDark, MarkInkDark, WarnAmberDark, ImageWellDark)

val LocalShopColors = staticCompositionLocalOf { LightShopColors }

/** Tile-like corners: small and square-ish, never pill-round. */
private val ShopShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun HairConsultantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalShopColors provides if (darkTheme) DarkShopColors else LightShopColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HairConsultantTypography,
            shapes = ShopShapes,
            content = content
        )
    }
}
