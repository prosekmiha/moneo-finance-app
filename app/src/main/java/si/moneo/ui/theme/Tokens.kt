package si.moneo.ui.theme

import android.content.Context
import androidx.compose.runtime.Immutable
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
    val chartIdle: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val selectedTab: Color,
    val onSelectedTab: Color,
) {
    val heroGradient: Brush get() = Brush.linearGradient(listOf(heroStart, heroEnd))
}

val LightFinanceColors = FinanceColors(
    income = Color(0xFF16B17E),
    incomeBg = Color(0xFFE3F7EF),
    expense = Color(0xFFEF4444),
    expenseBg = Color(0xFFFDE8E8),
    warning = Color(0xFFF59E0B),
    warningBg = Color(0xFFFEF6E0),
    chartIdle = Color(0xFFE3E7E5),
    heroStart = Color(0xFF1BC48C),
    heroEnd = Color(0xFF0E9F6E),
    selectedTab = Color(0xFF0E1B16),
    onSelectedTab = Color.White,
)

val DarkFinanceColors = FinanceColors(
    income = Color(0xFF34D399),
    incomeBg = Color(0xFF0F3327),
    expense = Color(0xFFF87171),
    expenseBg = Color(0xFF3A1A1A),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0xFF3A2E10),
    chartIdle = Color(0xFF2A3530),
    heroStart = Color(0xFF15986D),
    heroEnd = Color(0xFF0B6B4C),
    selectedTab = Color(0xFF2BB585),
    onSelectedTab = Color(0xFF04261B),
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val screen = 20.dp
}

/** Paleta za kategorije/cilje brez lastne barve (deterministično po naslovu). */
val AccentPalette = listOf(
    Color(0xFF16B17E), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEF4444),
    Color(0xFF3B82F6), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF97316),
)

fun accentFor(key: String, stored: Int? = null): Color =
    stored?.let { Color(it) } ?: AccentPalette[Math.floorMod(key.hashCode(), AccentPalette.size)]

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
            ?.let { runCatching { ThemeAccent.valueOf(it) }.getOrNull() } ?: ThemeAccent.GREEN

    fun saveAccent(context: Context, accent: ThemeAccent) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_ACCENT, accent.name).apply()
    }
}
