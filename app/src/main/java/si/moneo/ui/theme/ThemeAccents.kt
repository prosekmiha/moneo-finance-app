package si.moneo.ui.theme

import si.moneo.R
import si.moneo.ui.str
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Barve poudarka za eno različico (svetla ali temna). */
data class AccentSpec(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

/**
 * Barvna tema aplikacije. Menja poudarke (gumbi, izbrani elementi, glava) in rahlo obarva ozadja;
 * barve prihodkov (zelena) in odhodkov (rdeča) ostanejo enake v vseh temah.
 */
enum class ThemeAccent(private val labelRes: Int, val light: AccentSpec, val dark: AccentSpec) {
    GREEN(
        R.string.accent_green,
        AccentSpec(Color(0xFF16B17E), Color.White, Color(0xFFD7F5EA), Color(0xFF064E3B), Color(0xFF1BC48C), Color(0xFF0E9F6E)),
        AccentSpec(Color(0xFF34D399), Color(0xFF052E22), Color(0xFF0D3B2D), Color(0xFFA7F3D0), Color(0xFF15986D), Color(0xFF0B6B4C)),
    ),
    BLUE(
        R.string.accent_blue,
        AccentSpec(Color(0xFF2563EB), Color.White, Color(0xFFDCE7FD), Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF1D4ED8)),
        AccentSpec(Color(0xFF60A5FA), Color(0xFF0B2150), Color(0xFF16284A), Color(0xFFBFDBFE), Color(0xFF2563EB), Color(0xFF1E3A8A)),
    ),
    TEAL(
        R.string.accent_teal,
        AccentSpec(Color(0xFF0891B2), Color.White, Color(0xFFD5F3FA), Color(0xFF164E63), Color(0xFF06B6D4), Color(0xFF0E7490)),
        AccentSpec(Color(0xFF22D3EE), Color(0xFF083344), Color(0xFF0E3A45), Color(0xFFA5F3FC), Color(0xFF0891B2), Color(0xFF155E75)),
    ),
    PURPLE(
        R.string.accent_purple,
        AccentSpec(Color(0xFF7C3AED), Color.White, Color(0xFFEDE4FE), Color(0xFF4C1D95), Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        AccentSpec(Color(0xFFA78BFA), Color(0xFF2A1261), Color(0xFF2E1F52), Color(0xFFDDD6FE), Color(0xFF7C3AED), Color(0xFF4C1D95)),
    ),
    PINK(
        R.string.accent_pink,
        AccentSpec(Color(0xFFDB2777), Color.White, Color(0xFFFCE3EF), Color(0xFF831843), Color(0xFFEC4899), Color(0xFFBE185D)),
        AccentSpec(Color(0xFFF472B6), Color(0xFF4A0D2A), Color(0xFF4A1731), Color(0xFFFBCFE8), Color(0xFFDB2777), Color(0xFF9D174D)),
    ),
    ORANGE(
        R.string.accent_orange,
        AccentSpec(Color(0xFFEA580C), Color.White, Color(0xFFFFE7D6), Color(0xFF7C2D12), Color(0xFFF97316), Color(0xFFC2410C)),
        AccentSpec(Color(0xFFFB923C), Color(0xFF431407), Color(0xFF4A2410), Color(0xFFFED7AA), Color(0xFFEA580C), Color(0xFF9A3412)),
    ),
    GRAPHITE(
        R.string.accent_graphite,
        AccentSpec(Color(0xFF334155), Color.White, Color(0xFFE2E8F0), Color(0xFF0F172A), Color(0xFF475569), Color(0xFF1E293B)),
        AccentSpec(Color(0xFFCBD5E1), Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFFE2E8F0), Color(0xFF334155), Color(0xFF0F172A)),
    ),
    ;

    val label: String get() = str(labelRes)
}

/** Material shema za poudarek; zelena ostane natanko izvirna shema. */
internal fun colorSchemeFor(accent: ThemeAccent, dark: Boolean, green: ColorScheme): ColorScheme {
    if (accent == ThemeAccent.GREEN) return green
    val a = if (dark) accent.dark else accent.light
    // Nevtralne barve rahlo obarvamo s poudarkom, da ozadje ni "tuje" temi
    fun tint(base: Long, amount: Float) = lerp(Color(base), a.primary, amount)
    return if (dark) green.copy(
        primary = a.primary, onPrimary = a.onPrimary,
        primaryContainer = a.primaryContainer, onPrimaryContainer = a.onPrimaryContainer,
        secondary = tint(0xFFE8EAEC, 0.04f), onSecondary = tint(0xFF0F1113, 0.05f),
        background = tint(0xFF0F1113, 0.05f), onBackground = tint(0xFFE8EAEC, 0.04f),
        surface = tint(0xFF191C1F, 0.06f), onSurface = tint(0xFFE8EAEC, 0.04f),
        surfaceVariant = tint(0xFF23272B, 0.07f), onSurfaceVariant = tint(0xFF8F969C, 0.06f),
        surfaceContainerLow = tint(0xFF15181A, 0.05f), surfaceContainer = tint(0xFF191C1F, 0.06f),
        surfaceContainerHigh = tint(0xFF1F2327, 0.07f),
        outline = tint(0xFF2C3136, 0.07f), outlineVariant = tint(0xFF23272B, 0.07f),
    ) else green.copy(
        primary = a.primary, onPrimary = a.onPrimary,
        primaryContainer = a.primaryContainer, onPrimaryContainer = a.onPrimaryContainer,
        secondary = tint(0xFF111418, 0.10f), onSecondary = Color.White,
        background = tint(0xFFF4F5F6, 0.04f), onBackground = tint(0xFF111418, 0.10f),
        onSurface = tint(0xFF111418, 0.10f),
        surfaceVariant = tint(0xFFEEF0F2, 0.05f), onSurfaceVariant = tint(0xFF8A9096, 0.08f),
        surfaceContainerLow = tint(0xFFF7F8F9, 0.03f),
        outline = tint(0xFFE2E5E8, 0.05f), outlineVariant = tint(0xFFEEF0F2, 0.05f),
    )
}

/** Semantične barve za poudarek: glava in izbrani zavihki sledijo temi, prihodki/odhodki ne. */
internal fun financeColorsFor(accent: ThemeAccent, dark: Boolean, scheme: ColorScheme): FinanceColors {
    val base = if (dark) DarkFinanceColors else LightFinanceColors
    if (accent == ThemeAccent.GREEN) return base
    val a = if (dark) accent.dark else accent.light
    return base.copy(
        chartIdle = scheme.outline,
        heroStart = a.heroStart,
        heroEnd = a.heroEnd,
        selectedTab = if (dark) a.primary else scheme.onSurface,
        onSelectedTab = if (dark) a.onPrimary else Color.White,
    )
}
