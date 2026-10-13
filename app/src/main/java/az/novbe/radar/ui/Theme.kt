package az.novbe.radar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Radar rəngləri (qaranlıq tema). */
object RC {
    val Bg = Color(0xFF0A0E14)
    val Surface = Color(0xFF111821)
    val Surface2 = Color(0xFF18212D)
    val Line = Color(0xFF243041)
    val Cyan = Color(0xFF4FD8EB)
    val Green = Color(0xFF3DDC97)
    val Amber = Color(0xFFFFB547)
    val Red = Color(0xFFFF5C6C)
    val Text = Color(0xFFE6EDF3)
    val Muted = Color(0xFF8B98A9)
}

private val scheme = darkColorScheme(
    primary = RC.Cyan,
    onPrimary = RC.Bg,
    primaryContainer = Color(0xFF103A44),
    onPrimaryContainer = RC.Cyan,
    secondary = RC.Green,
    onSecondary = RC.Bg,
    secondaryContainer = Color(0xFF0F3A2C),
    onSecondaryContainer = RC.Green,
    tertiary = RC.Amber,
    onTertiary = RC.Bg,
    background = RC.Bg,
    onBackground = RC.Text,
    surface = RC.Surface,
    onSurface = RC.Text,
    surfaceVariant = RC.Surface2,
    onSurfaceVariant = RC.Muted,
    surfaceContainerLowest = RC.Bg,
    surfaceContainerLow = RC.Surface,
    surfaceContainer = RC.Surface,
    surfaceContainerHigh = RC.Surface2,
    surfaceContainerHighest = Color(0xFF1F2A38),
    outline = RC.Line,
    outlineVariant = RC.Line,
    error = RC.Red,
    onError = RC.Bg,
)

private val type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun RadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
