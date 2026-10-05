package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.domain.goals.autoSavingPlan
import java.time.LocalDate
import java.time.YearMonth

class AutoSavingTest {

    @Test fun freshlyEnabledDoesNotPayRetroactively() {
        // Vklopljeno 20. 9., dan vplačila 1. -> ta mesec ne, prvo vplačilo 1. 10.
        val p = autoSavingPlan(null, 1, LocalDate.of(2026, 9, 20))
        assertTrue(p.months.isEmpty())
        assertEquals(YearMonth.of(2026, 9), p.lastMonth)
        val next = autoSavingPlan(p.lastMonth.toString(), 1, LocalDate.of(2026, 10, 1))
        assertEquals(listOf(YearMonth.of(2026, 10)), next.months)
    }

    @Test fun enabledBeforeDayPaysThisMonth() {
        // Vklopljeno 5. 9., dan vplačila 10. -> prvo vplačilo 10. 9.
        val p = autoSavingPlan(null, 10, LocalDate.of(2026, 9, 5))
        assertTrue(p.months.isEmpty())
        assertEquals(listOf(YearMonth.of(2026, 9)), autoSavingPlan(p.lastMonth.toString(), 10, LocalDate.of(2026, 9, 10)).months)
    }

    @Test fun catchesUpMissedMonthsOnce() {
        val p = autoSavingPlan("2026-06", 1, LocalDate.of(2026, 9, 15))
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9)), p.months)
        // isti dan znova: nič novega
        assertTrue(autoSavingPlan(p.lastMonth.toString(), 1, LocalDate.of(2026, 9, 15)).months.isEmpty())
    }

    @Test fun catchUpLimitedToTwelveMonths() {
        assertEquals(12, autoSavingPlan("2020-01", 1, LocalDate.of(2026, 9, 15)).months.size)
    }
}
