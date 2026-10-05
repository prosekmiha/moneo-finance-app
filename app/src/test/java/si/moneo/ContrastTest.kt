package si.moneo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.ui.theme.AccentPalette
import si.moneo.ui.theme.DarkFinanceColors
import si.moneo.ui.theme.LightFinanceColors
import si.moneo.ui.theme.contrastWith
import si.moneo.ui.theme.ensureContrast
import si.moneo.ui.theme.underWhiteText

/** Barve morajo dosegati WCAG AA: 4.5:1 za besedilo, 3:1 za ikone in grafične elemente. */
class ContrastTest {
    private val lightCard = Color.White
    private val darkCard = Color(0xFF18201C)

    @Test fun categoryIconsReadableOnTheirTintedCircle() {
        for (surface in listOf(lightCard, darkCard)) {
            for (c in AccentPalette) {
                val circle = c.copy(alpha = 0.14f).compositeOver(surface)
                val icon = c.ensureContrast(circle, 3f)
                val letter = c.ensureContrast(circle, 4.5f)
                assertTrue("ikona $c na $surface: ${icon.contrastWith(circle)}", icon.contrastWith(circle) >= 3f)
                assertTrue("črka $c na $surface: ${letter.contrastWith(circle)}", letter.contrastWith(circle) >= 4.5f)
            }
        }
    }

    @Test fun accountCardsReadableUnderWhiteText() {
        for (c in AccentPalette) {
            val bg = c.underWhiteText()
            assertTrue("belo na $c: ${Color.White.contrastWith(bg)}", Color.White.contrastWith(bg) >= 4.5f)
        }
    }

    @Test fun alreadyReadableColorsStayUnchanged() {
        val dark = Color(0xFF0F172A)
        assertTrue(dark.ensureContrast(lightCard, 3f) == dark)
    }

    @Test fun financeTextColorsMeetAa() {
        val l = LightFinanceColors
        for ((name, c) in listOf("income" to l.income, "expense" to l.expense, "transfer" to l.transfer)) {
            assertTrue("$name svetlo: ${c.contrastWith(lightCard)}", c.contrastWith(lightCard) >= 4.5f)
        }
        assertTrue(l.warning.contrastWith(l.warningBg) >= 4.5f)
        assertTrue(Color.White.contrastWith(l.heroStart) >= 4.5f)
        val d = DarkFinanceColors
        for ((name, c) in listOf("income" to d.income, "expense" to d.expense, "transfer" to d.transfer)) {
            assertTrue("$name temno: ${c.contrastWith(darkCard)}", c.contrastWith(darkCard) >= 4.5f)
        }
    }
}
