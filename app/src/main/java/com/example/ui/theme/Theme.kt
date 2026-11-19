package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkStudioColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanAccent,
    secondary = AmberAccent,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberAccent,
    tertiary = CoralAccent,
    onTertiary = OnCoral,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = CoralAccent,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = StudioSurface,
    surfaceContainerHigh = StudioSurfaceVariant,
    surfaceContainerHighest = StudioElevated,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = CoralAccent,
    onError = OnCoral
)

private val LightStudioColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDF4EA),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = LightAmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = LightCoralTertiary,
    onTertiary = Color.White,
    background = LightStudioBg,
    onBackground = Color(0xFF111520),
    surface = LightStudioSurface,
    onSurface = Color(0xFF111520),
    surfaceVariant = LightStudioSurfaceVariant,
    onSurfaceVariant = Color(0xFF475066),
    outline = Color(0xFFC8D0E0)
)

@Composable
fun OctaCanvasTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkStudioColorScheme else LightStudioColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
