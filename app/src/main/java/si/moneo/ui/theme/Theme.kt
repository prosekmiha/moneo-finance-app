package si.moneo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF16B17E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5EA),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0E1B16),
    onSecondary = Color.White,
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF0E1B16),
    surface = Color.White,
    onSurface = Color(0xFF0E1B16),
    surfaceVariant = Color(0xFFEEF2F0),
    onSurfaceVariant = Color(0xFF8A938F),
    surfaceContainerLow = Color(0xFFF7F9F8),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFE2E8E5),
    outlineVariant = Color(0xFFEEF2F0),
    error = Color(0xFFEF4444),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF052E22),
    primaryContainer = Color(0xFF0D3B2D),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFE6EDEA),
    onSecondary = Color(0xFF0E1311),
    background = Color(0xFF0E1311),
    onBackground = Color(0xFFE6EDEA),
    surface = Color(0xFF18201C),
    onSurface = Color(0xFFE6EDEA),
    surfaceVariant = Color(0xFF222C27),
    onSurfaceVariant = Color(0xFF8C9892),
    surfaceContainerLow = Color(0xFF141B18),
    surfaceContainer = Color(0xFF18201C),
    surfaceContainerHigh = Color(0xFF1E2823),
    outline = Color(0xFF2A3530),
    outlineVariant = Color(0xFF222C27),
    error = Color(0xFFF87171),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Tabularne številke, da zneski ne "plešejo" med animacijo. */
private const val TNUM = "tnum"

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, fontFeatureSettings = TNUM),
        displayMedium = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, fontFeatureSettings = TNUM),
        displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

@Composable
fun MoneoTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accent: ThemeAccent = ThemeAccent.GREEN,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val (scheme, financeColors) = remember(accent, dark) { appColors(accent, dark) }
    CompositionLocalProvider(LocalFinanceColors provides financeColors) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Barve teme zunaj Compose (npr. widgeti na domačem zaslonu). */
fun appColors(accent: ThemeAccent, dark: Boolean): Pair<ColorScheme, FinanceColors> {
    val scheme = colorSchemeFor(accent, dark, if (dark) DarkColors else LightColors)
    return scheme to financeColorsFor(accent, dark, scheme)
}

/** Kratka dostopna točka: `Finance.colors.income`. */
object Finance {
    val colors: FinanceColors
        @Composable get() = LocalFinanceColors.current
}
