package com.example.solarsoilingadvisor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SolarBlue,
    onPrimary = Color.White,
    primaryContainer = SkyTint,
    onPrimaryContainer = SolarBlueDark,
    secondary = SunAmber,
    onSecondary = Color(0xFF3A2A00),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEDF1F6),
    onSurfaceVariant = Color(0xFF566270),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93BEF0),
    onPrimary = SolarBlueDark,
    primaryContainer = SolarBlueDark,
    onPrimaryContainer = SkyTint,
    secondary = SunAmber,
    onSecondary = Color(0xFF3A2A00),
    background = Color(0xFF0F1418),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF161B21),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF273039),
    onSurfaceVariant = Color(0xFFAEBAC6),
)

@Composable
fun SolarSoilingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
