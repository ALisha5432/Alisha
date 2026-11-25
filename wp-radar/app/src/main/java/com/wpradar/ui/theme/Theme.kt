package com.wpradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1D5),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4B635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DC),
    onSecondaryContainer = Color(0xFF072019),
    background = Color(0xFFF6FBF8),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FBF8),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FDBAE),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513B),
    onPrimaryContainer = Color(0xFFB4F1D5),
    secondary = Color(0xFFB1CCC0),
    onSecondary = Color(0xFF1D352D),
    secondaryContainer = Color(0xFF344C43),
    onSecondaryContainer = Color(0xFFCDE9DC),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun WpRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
