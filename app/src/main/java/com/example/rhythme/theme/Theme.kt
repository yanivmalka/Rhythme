package com.example.rhythme.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

// Light theme: the app's own blues on a pale background with dark, high-contrast text. It does
// not use the phone's wallpaper colors, which were too faint for the controls.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1668C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FA),
    onPrimaryContainer = Color(0xFF0A2A52),
    secondary = Color(0xFF3F5F8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F4),
    onSecondaryContainer = Color(0xFF14202E),
    tertiary = Color(0xFF9A6A00),
    onTertiary = Color.White,
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF14202E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14202E),
    surfaceVariant = Color(0xFFDDE5F0),
    onSurfaceVariant = Color(0xFF3A4656),
    surfaceContainer = Color(0xFFE9EEF6),
    surfaceContainerHigh = Color(0xFFE2E9F3),
    surfaceContainerHighest = Color(0xFFDDE5F0),
    surfaceContainerLow = Color(0xFFF0F4FA),
    outline = Color(0xFF6B7686),
    outlineVariant = Color(0xFFB4BFCD),
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    Palette.light = !darkTheme // the hand-drawn colors follow the theme
    val colorScheme = when {
        !darkTheme -> LightColorScheme
        dynamicColor && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> dynamicDarkColorScheme(LocalContext.current)
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}