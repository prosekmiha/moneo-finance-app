package si.moneo.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Semantične barve, ki jih Material shema nima. */
@Immutable
data class FinanceColors(
    val income: Color,
    val incomeBg: Color,
    val expense: Color,
    val expenseBg: Color,
    val warning: Color,
    val warningBg: Color,
    /** Prenosi med računi (nevtralno - ni prihodek ne strošek). */
    val transfer: Color,
    val chartIdle: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val selectedTab: Color,
    val onSelectedTab: Color,
    val isDark: Boolean,
) {
    val heroGradient: Brush get() = Brush.linearGradient(listOf(heroStart, heroEnd))
}

// Barve besedil dosegajo WCAG AA na beli in na svojih pastelnih ozadjih (*Bg)
val LightFinanceColors = FinanceColors(
    income = Color(0xFF0A8259),
    incomeBg = Color(0xFFE3F7EF),
    expense = Color(0xFFDC2626),
    expenseBg = Color(0xFFFDE8E8),
    warning = Color(0xFFB45309),
    warningBg = Color(0xFFFEF6E0),
    transfer = Color(0xFF2563EB),
    chartIdle = Color(0xFFE3E7E5),
    heroStart = Color(0xFF087A55),
    heroEnd = Color(0xFF065F45),
    selectedTab = Color(0xFF0E1B16),
    onSelectedTab = Color.White,
    isDark = false,
)

val DarkFinanceColors = FinanceColors(
    income = Color(0xFF34D399),
    incomeBg = Color(0xFF0F3327),
    expense = Color(0xFFF87171),
    expenseBg = Color(0xFF3A1A1A),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0xFF3A2E10),
    transfer = Color(0xFF60A5FA),
    chartIdle = Color(0xFF2A3530),
    heroStart = Color(0xFF0B7553),
    heroEnd = Color(0xFF08543C),
    selectedTab = Color(0xFF2BB585),
    onSelectedTab = Color(0xFF04261B),
    isDark = true,
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val screen = 20.dp
}

/** Lestvica zaobljenosti; enaka kot `MaterialTheme.shapes` (extraSmall .. extraLarge). */
object Radius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 28.dp
}

/** Paleta za kategorije/cilje brez lastne barve (deterministično po naslovu). */
val AccentPalette = listOf(
    Color(0xFF16B17E), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEF4444),
    Color(0xFF3B82F6), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF97316),
)

fun accentFor(key: String, stored: Int? = null): Color =
    stored?.let { Color(it) } ?: AccentPalette[Math.floorMod(key.hashCode(), AccentPalette.size)]

/** Razmerje kontrasta po WCAG (1..21). */
fun Color.contrastWith(other: Color): Float {
    val a = luminance()
    val b = other.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/**
 * Barva z enakim odtenkom, ki na podlagi [bg] doseže kontrast vsaj [min]: na svetli podlagi
 * jo potemni, na temni posvetli (najmanj, kolikor je treba). Uporabniške barve (kategorije,
 * cilji, računi) so shranjene nespremenjene - prilagodijo se šele ob prikazu.
 * Meje WCAG: 4.5 za besedilo, 3 za ikone in grafične elemente.
 */
fun Color.ensureContrast(bg: Color, min: Float): Color {
    if (contrastWith(bg) >= min) return this
    val target = if (bg.contrastWith(Color.Black) >= bg.contrastWith(Color.White)) Color.Black else Color.White
    var lo = 0f
    var hi = 1f
    repeat(12) {
        val mid = (lo + hi) / 2f
        if (lerp(this, target, mid).contrastWith(bg) >= min) hi = mid else lo = mid
    }
    return lerp(this, target, hi)
}

/** Ikone, obroči, progress in grafi v barvi kategorije na kartici (>= 3:1). */
@Composable
fun Color.asGraphic(on: Color = MaterialTheme.colorScheme.surface): Color = ensureContrast(on, 3f)

/** Besedilo v barvi kategorije (>= 4.5:1). */
@Composable
fun Color.asText(on: Color = MaterialTheme.colorScheme.surface): Color = ensureContrast(on, 4.5f)

/** Ozadje (npr. gradient kartice računa), na katerem je belo besedilo (>= 4.5:1). */
fun Color.underWhiteText(): Color = ensureContrast(Color.White, 4.5f)

/** Shranjena izbira teme (preprosto, brez DataStore). */
object ThemePrefs {
    private const val FILE = "ui_prefs"
    private const val KEY = "theme_mode"
    private const val KEY_ACCENT = "theme_accent"

    fun load(context: Context): ThemeMode =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }

    fun loadAccent(context: Context): ThemeAccent =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_ACCENT, null)
            ?.let { runCatching { ThemeAccent.valueOf(it) }.getOrNull() }
            // Material You je na voljo šele od Androida 12 (npr. obnovljena varnostna kopija s novejše naprave)
            ?.takeIf { it in ThemeAccent.available } ?: ThemeAccent.GREEN

    fun saveAccent(context: Context, accent: ThemeAccent) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_ACCENT, accent.name).apply()
    }
}
