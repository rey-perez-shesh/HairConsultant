package com.hairconsultant.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = VioletPrimary,
    onPrimary = Color.White,
    primaryContainer = VioletContainerLight,
    onPrimaryContainer = VioletPrimary,
    secondary = PinkAccent,
    onSecondary = Color.White,
    background = BackgroundLight,
    onBackground = OnColorLight,
    surface = SurfaceLight,
    onSurface = OnColorLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnColorLight,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = VioletPrimaryDark,
    onPrimary = Color(0xFF2A1B70),
    primaryContainer = VioletContainerDark,
    onPrimaryContainer = Color.White,
    secondary = PinkAccentDark,
    onSecondary = Color(0xFF3A0F1F),
    background = BackgroundDark,
    onBackground = OnColorDark,
    surface = SurfaceDark,
    onSurface = OnColorDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnColorDark,
    error = ErrorRed
)

@Composable
fun HairConsultantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HairConsultantTypography,
        content = content
    )
}
