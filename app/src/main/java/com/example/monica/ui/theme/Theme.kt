package com.example.monica.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = MonicaDarkPrimary,
    onPrimary = MonicaDarkOnPrimary,
    secondary = MonicaDarkPrimary,
    background = MonicaDarkBg,
    surface = MonicaDarkSurface,
    surfaceVariant = MonicaDarkSurfaceVariant,
    onBackground = MonicaDarkText,
    onSurface = MonicaDarkText,
    onSurfaceVariant = MonicaDarkMuted,
    outline = Color(0xFF3C4048),
    error = Color(0xFFF28B82),
)

private val LightColors = lightColorScheme(
    primary = MonicaLightPrimary,
    onPrimary = MonicaLightOnPrimary,
    secondary = MonicaLightPrimary,
    background = MonicaLightBg,
    surface = MonicaLightSurface,
    surfaceVariant = MonicaLightSurfaceVariant,
    onBackground = MonicaLightText,
    onSurface = MonicaLightText,
    onSurfaceVariant = MonicaLightMuted,
    outline = Color(0xFFD0D5DD),
    error = Color(0xFFB3261E),
)

@Composable
fun MonicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
