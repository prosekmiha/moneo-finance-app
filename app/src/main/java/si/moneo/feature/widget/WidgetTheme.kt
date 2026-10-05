package si.moneo.feature.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import si.moneo.ui.theme.ThemeMode
import si.moneo.ui.theme.ThemePrefs
import si.moneo.ui.theme.appColors

/** Barve widgetov po izbrani temi aplikacije (svetla/temna + barvna tema). */
data class WidgetColors(
    val background: Int,
    /** Ozadje tabov (kot SegmentedTabs v aplikaciji). */
    val track: Int,
    val text: Int,
    val subtext: Int,
    val accent: Int,
    val onAccent: Int,
    val accentBg: Int,
    val onAccentBg: Int,
    val income: Int,
    val incomeBg: Int,
    val expense: Int,
    val expenseBg: Int,
)

fun widgetColors(context: Context): WidgetColors {
    val dark = when (ThemePrefs.load(context)) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    val (scheme, finance) = appColors(ThemePrefs.loadAccent(context), dark)
    return WidgetColors(
        background = scheme.surface.toArgb(),
        track = scheme.surfaceVariant.toArgb(),
        text = scheme.onSurface.toArgb(),
        subtext = scheme.onSurfaceVariant.toArgb(),
        accent = scheme.primary.toArgb(),
        onAccent = scheme.onPrimary.toArgb(),
        accentBg = scheme.primaryContainer.toArgb(),
        onAccentBg = scheme.onPrimaryContainer.toArgb(),
        income = finance.income.toArgb(),
        incomeBg = finance.incomeBg.toArgb(),
        expense = finance.expense.toArgb(),
        expenseBg = finance.expenseBg.toArgb(),
    )
}

/** Obarva ImageView z belo obliko (ozadje gumba/widgeta). */
fun android.widget.RemoteViews.tint(viewId: Int, color: Int) = setInt(viewId, "setColorFilter", color)
