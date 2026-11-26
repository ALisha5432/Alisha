package com.wpradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brend rəngləri (logo ilə eyni): radar yaşılı, səma mavisi,
 * kəhrəba — "tapılan mesaj", mərcan — problem. Gecə fonu radar panelində istifadə olunur.
 */
object Brand {
    val Mint = Color(0xFF4AF2C5)
    val Sky = Color(0xFF5CC8FF)
    val Amber = Color(0xFFFFB547)
    val AmberLight = Color(0xFFFFD978)
    val AmberDeep = Color(0xFFFF9A3D)
    val Coral = Color(0xFFFF7B7B)
    val Slate = Color(0xFF7D8BA6)
    val Night = Color(0xFF0C1838)
    val Abyss = Color(0xFF0A3B3E)
    val LeadLine = Color(0xFFEFFFFA)

    /** Radar paneli işıqlı temada da qaranlıq qalır — cihaz ekranı kimi. */
    val HeroTop = Color(0xFF0E1C3D)
    val HeroBottom = Color(0xFF0A3436)
    val OnHero = Color(0xFFFFFFFF)
    val OnHeroMuted = Color(0x9EFFFFFF)
}

/** Material rənglərindən əlavə, tətbiqin öz semantik rəngləri. */
@Immutable
data class RadarPalette(
    val isDark: Boolean,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val neutralContainer: Color,
    val onNeutral: Color,
    val card: Color,
    val cardBorder: Color,
    val shadow: Color,
    val navBar: Color,
    val highlight: Color,
    val cta: List<Color>,
    val onCta: Color,
)

private val LightPalette = RadarPalette(
    isDark = false,
    success = Color(0xFF00936B),
    successContainer = Color(0xFFD5F5EA),
    warning = Color(0xFFB86A00),
    warningContainer = Color(0xFFFFEBCF),
    danger = Color(0xFFD33B3B),
    dangerContainer = Color(0xFFFFE2DF),
    neutralContainer = Color(0xFFEAEFF6),
    onNeutral = Color(0xFF5B677D),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE3E8F0),
    shadow = Color(0x401E3A5F),
    navBar = Color(0xFFFFFFFF),
    highlight = Color(0x8CFFC266),
    cta = listOf(Color(0xFF00A87A), Color(0xFF0FA9C9)),
    onCta = Color(0xFFFFFFFF),
)

private val DarkPalette = RadarPalette(
    isDark = true,
    success = Color(0xFF4AF2C5),
    successContainer = Color(0xFF0F3A30),
    warning = Color(0xFFFFB547),
    warningContainer = Color(0xFF45300F),
    danger = Color(0xFFFF7B7B),
    dangerContainer = Color(0xFF4B1519),
    neutralContainer = Color(0xFF1A2438),
    onNeutral = Color(0xFF95A2BA),
    card = Color(0xFF0E1525),
    cardBorder = Color(0xFF1C2740),
    shadow = Color(0x00000000),
    navBar = Color(0xFF131C2F),
    highlight = Color(0x66FFB547),
    cta = listOf(Color(0xFF3BE8B6), Color(0xFF3FC3F2)),
    onCta = Color(0xFF04211A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00936B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFF7E8),
    onPrimaryContainer = Color(0xFF00382A),
    inversePrimary = Color(0xFF4AF2C5),
    secondary = Color(0xFF1E7FB8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6ECFA),
    onSecondaryContainer = Color(0xFF062F48),
    tertiary = Color(0xFFB8650A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE7C7),
    onTertiaryContainer = Color(0xFF3F2300),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF0E1626),
    surface = Color(0xFFF3F6FA),
    onSurface = Color(0xFF0E1626),
    surfaceVariant = Color(0xFFE4E9F1),
    onSurfaceVariant = Color(0xFF5B677D),
    surfaceTint = Color(0xFF00936B),
    inverseSurface = Color(0xFF1B2536),
    inverseOnSurface = Color(0xFFEEF2F8),
    error = Color(0xFFD33B3B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE2DF),
    onErrorContainer = Color(0xFF5E0B0F),
    outline = Color(0xFFA3AEC0),
    outlineVariant = Color(0xFFDCE2EC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEBF0F6),
    surfaceContainerHigh = Color(0xFFE5EBF3),
    surfaceContainerHighest = Color(0xFFDEE5EF),
    surfaceDim = Color(0xFFD9E0EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4AF2C5),
    onPrimary = Color(0xFF00291D),
    primaryContainer = Color(0xFF0E3A30),
    onPrimaryContainer = Color(0xFFA8F8DF),
    inversePrimary = Color(0xFF00936B),
    secondary = Color(0xFF7CD0FF),
    onSecondary = Color(0xFF00263B),
    secondaryContainer = Color(0xFF12324A),
    onSecondaryContainer = Color(0xFFCBE9FF),
    tertiary = Color(0xFFFFB547),
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = Color(0xFF4A3112),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFE8EDF6),
    surface = Color(0xFF070B14),
    onSurface = Color(0xFFE8EDF6),
    surfaceVariant = Color(0xFF1A2438),
    onSurfaceVariant = Color(0xFF95A2BA),
    surfaceTint = Color(0xFF4AF2C5),
    inverseSurface = Color(0xFFE8EDF6),
    inverseOnSurface = Color(0xFF121A2A),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF430006),
    errorContainer = Color(0xFF4B1519),
    onErrorContainer = Color(0xFFFFDAD7),
    outline = Color(0xFF3D4A63),
    outlineVariant = Color(0xFF222D43),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF222C40),
    surfaceContainerLowest = Color(0xFF05080F),
    surfaceContainerLow = Color(0xFF0C1220),
    surfaceContainer = Color(0xFF0F1626),
    surfaceContainerHigh = Color(0xFF141D30),
    surfaceContainerHighest = Color(0xFF1A2438),
    surfaceDim = Color(0xFF070B14),
)

private val Base = Typography()

private val RadarTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 23.sp, letterSpacing = 0.2.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall = Base.bodySmall.copy(lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val RadarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Rəqəmlər eyni enlikdə (saat, say) — dəyişəndə mətn "atılmasın". */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private val LocalRadarPalette = staticCompositionLocalOf { LightPalette }

object RadarTheme {
    val palette: RadarPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalRadarPalette.current
}

@Composable
fun WpRadarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalRadarPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = RadarTypography,
            shapes = RadarShapes,
            content = content,
        )
    }
}
