package si.moneo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.ui.theme.ThemeAccent
import si.moneo.ui.theme.appColors
import si.moneo.ui.theme.contrastWith

/**
 * Vse barvne teme iz nastavitev (razen sistemske, ki je odvisna od naprave) v svetlem in temnem
 * načinu: pari barv, ki se v aplikaciji pojavijo kot besedilo (>= 4.5) ali ikona/element (>= 3).
 */
class ThemeContrastTest {

    private data class Check(val what: String, val fg: Color, val bg: Color, val min: Float)

    private fun checks(accent: ThemeAccent, dark: Boolean): List<Check> {
        val (s, f) = appColors(accent, dark)
        val heroText = Color.White.copy(alpha = 0.9f).compositeOver(f.heroStart)
        return listOf(
            Check("besedilo na kartici", s.onSurface, s.surface, 4.5f),
            Check("besedilo na ozadju", s.onBackground, s.background, 4.5f),
            Check("sivo besedilo na kartici", s.onSurfaceVariant, s.surface, 4.5f),
            Check("sivo besedilo na ozadju", s.onSurfaceVariant, s.background, 4.5f),
            Check("neizbran zavihek (sivo na sledi)", s.onSurfaceVariant, s.surfaceVariant, 4.5f),
            Check("primary besedilo na kartici", s.primary, s.surface, 4.5f),
            Check("primary besedilo na ozadju", s.primary, s.background, 4.5f),
            Check("besedilo na gumbu (onPrimary)", s.onPrimary, s.primary, 4.5f),
            Check("primary na primaryContainer", s.primary, s.primaryContainer, 4.5f),
            Check("onPrimaryContainer na primaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5f),
            Check("izbran zavihek", f.onSelectedTab, f.selectedTab, 4.5f),
            Check("izbran zavihek proti sledi", f.selectedTab, s.surfaceVariant, 3f),
            Check("belo na hero kartici", Color.White, f.heroStart, 4.5f),
            Check("belo 90 % na hero kartici", heroText, f.heroStart, 4.5f),
            Check("ikona + na gumbu", Color.White, f.heroStart, 3f),
            Check("prihodek na kartici", f.income, s.surface, 4.5f),
            Check("odhodek na kartici", f.expense, s.surface, 4.5f),
            Check("prenos na kartici", f.transfer, s.surface, 4.5f),
            Check("opozorilo na svojem ozadju", f.warning, f.warningBg, 4.5f),
            Check("prihodek na svojem ozadju", f.income, f.incomeBg, 3f),
            Check("odhodek na svojem ozadju", f.expense, f.expenseBg, 3f),
        )
    }

    @Test fun allAccentThemesMeetWcagAa() {
        val failures = mutableListOf<String>()
        for (accent in ThemeAccent.entries.filter { it != ThemeAccent.DYNAMIC }) {
            for (dark in listOf(false, true)) {
                for (c in checks(accent, dark)) {
                    val r = c.fg.contrastWith(c.bg)
                    if (r < c.min) failures += "${accent.name} ${if (dark) "temno" else "svetlo"}: ${c.what} = ${"%.2f".format(r)} (< ${c.min})"
                }
            }
        }
        assertTrue(failures.joinToString("\n", prefix = "\n"), failures.isEmpty())
    }
}
