package si.moneo.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import si.moneo.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Svetla shema: toni izbrani za kontrast WCAG AA (besedilo >= 4.5:1 na beli)
private val LightColors = lightColorScheme(
    primary = Color(0xFF087A55),
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
    onSurfaceVariant = Color(0xFF677069),
    surfaceContainerLow = Color(0xFFF7F9F8),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFE2E8E5),
    outlineVariant = Color(0xFFEEF2F0),
    error = Color(0xFFDC2626),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF052E22),
    primaryContainer = Color(0xFF0D3B2D),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFE6EDEA),
    onSecondary = Color(0xFF0E1311),
    background = Color(0xFF0B0F0D),
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
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/** Plus Jakarta Sans (OFL); znaki brez glifa (npr. cirilica) padejo na sistemsko pisavo. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

/** Tabularne številke, da zneski ne "plešejo" med animacijo. */
private const val TNUM = "tnum"

private fun TextStyle.j() = copy(fontFamily = Jakarta)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp, fontFeatureSettings = TNUM).j(),
        displayMedium = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, fontFeatureSettings = TNUM).j(),
        displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFeatureSettings = TNUM).j(),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFeatureSettings = TNUM).j(),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, fontFeatureSettings = TNUM).j(),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, fontFeatureSettings = TNUM).j(),
        titleLarge = t.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp).j(),
        titleMedium = t.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp).j(),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold).j(),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, letterSpacing = 0.sp).j(),
        bodyMedium = t.bodyMedium.copy(letterSpacing = 0.sp).j(),
        bodySmall = t.bodySmall.copy(letterSpacing = 0.1.sp).j(),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp).j(),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium).j(),
        labelSmall = t.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp).j(),
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
    val context = LocalContext.current
    val (scheme, financeColors) = remember(accent, dark) { appColors(accent, dark, context) }
    CompositionLocalProvider(LocalFinanceColors provides financeColors) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Barve teme zunaj Compose (npr. widgeti na domačem zaslonu); [context] je potreben za DYNAMIC. */
fun appColors(accent: ThemeAccent, dark: Boolean, context: Context? = null): Pair<ColorScheme, FinanceColors> {
    val spec = resolveAccentSpec(accent, dark, context)
    val scheme = colorSchemeFor(accent, dark, if (dark) DarkColors else LightColors, spec)
    return scheme to financeColorsFor(accent, dark, scheme, spec)
}

/** Kratka dostopna točka: `Finance.colors.income`. */
object Finance {
    val colors: FinanceColors
        @Composable get() = LocalFinanceColors.current
}
