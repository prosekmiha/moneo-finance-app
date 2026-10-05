package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.InsightKind
import si.moneo.ui.PeriodBucket
import si.moneo.ui.TransactionUi
import si.moneo.ui.categoryKey
import si.moneo.ui.categoryYearComparison
import si.moneo.ui.home.DEFAULT_HOME_LAYOUT
import si.moneo.ui.home.HomeSection
import si.moneo.ui.home.HomeSectionState
import si.moneo.ui.home.parseHomeLayout
import si.moneo.ui.home.serializeHomeLayout
import si.moneo.ui.lastYearByCategory
import si.moneo.ui.lastYearRange
import si.moneo.ui.lastYearTotals
import si.moneo.ui.yearOverYearInsights
import java.time.LocalDate

class YearOverYearTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val october = PeriodBucket(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), "okt", "Oktober 2026", 0, 0)
    private val elektrika = CategoryEntity(uid = "e", title = "Elektrika", type = TransactionType.EXPENSE)

    private fun tx(cents: Long, date: LocalDate, cat: String? = "e", type: TransactionType = TransactionType.EXPENSE) = TransactionUi(
        uid = "$cents-$date", type = type, amountCents = cents, date = date, comment = "", categoryUid = cat,
        categoryTitle = null, categoryColor = null, accountUid = null, accountTitle = null,
        source = TransactionSource.MANUAL, confirmed = true,
    )

    @Test fun currentPeriodComparesToSameDayLastYear() {
        val (from, to) = lastYearRange(october, today)
        assertEquals(LocalDate.of(2025, 10, 1), from)
        assertEquals(LocalDate.of(2025, 10, 16), to) // do vključno 15. 10. 2025
        // pretekel mesec: cel lanski mesec
        val sept = PeriodBucket(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), "sep", "September 2026", 0, 0)
        assertEquals(LocalDate.of(2025, 10, 1), lastYearRange(sept, today).second)
    }

    @Test fun totalsAndCategories() {
        val txs = listOf(
            tx(8500, LocalDate.of(2025, 10, 5)),
            tx(9000, LocalDate.of(2025, 10, 25)), // po istem dnevu lani - ne šteje
            tx(10400, LocalDate.of(2026, 10, 5)),
            tx(300000, LocalDate.of(2025, 10, 10), cat = null, type = TransactionType.INCOME),
        )
        val t = lastYearTotals(txs, october, today)
        assertEquals(8500L, t.expenseCents)
        assertEquals(300000L, t.incomeCents)
        assertEquals(8500L, lastYearByCategory(txs, october, today)[categoryKey(TransactionType.EXPENSE, "e")])
        val insight = yearOverYearInsights(txs, listOf(elektrika), october, today).single()
        assertEquals(InsightKind.TREND_UP, insight.kind)
        // Besedilo je iz virov (v JVM testu "#id argumenti"): preveri le argumente
        assertTrue(insight.text, insight.text.contains("Elektrika") && insight.text.contains("22"))
    }

    @Test fun smallChangesAreNotInsights() {
        val txs = listOf(tx(1000, LocalDate.of(2025, 10, 5)), tx(1300, LocalDate.of(2026, 10, 5)))
        assertTrue(yearOverYearInsights(txs, listOf(elektrika), october, today).isEmpty())
    }

    @Test fun categoryYearToDate() {
        val own = listOf(
            tx(5000, LocalDate.of(2026, 1, 10)), tx(4000, LocalDate.of(2025, 1, 10)),
            tx(7000, LocalDate.of(2025, 12, 1)), // po istem dnevu lani - ne šteje v "lani do danes"
        )
        val c = categoryYearComparison(own, today)
        assertEquals(5000L, c.ytdCents)
        assertEquals(4000L, c.ytdLastYearCents)
        assertEquals(25, c.deltaPct)
        assertEquals(10, c.months.size)
        assertEquals(10, c.months.first().month.monthValue) // najnovejši mesec najprej
    }

    @Test fun homeLayoutRoundTripAndNewSections() {
        val custom = listOf(
            HomeSectionState(HomeSection.GOALS, true),
            HomeSectionState(HomeSection.FAVORITES, false),
        )
        val parsed = parseHomeLayout(serializeHomeLayout(custom))
        assertEquals(HomeSection.GOALS, parsed[0].section)
        assertFalse(parsed[1].visible)
        // kartice, ki jih shranjena postavitev ne pozna, se dodajo na konec kot vidne
        assertEquals(HomeSection.entries.size, parsed.size)
        assertTrue(parsed.drop(2).all { it.visible })
        assertEquals(DEFAULT_HOME_LAYOUT, parseHomeLayout(null))
        assertEquals(DEFAULT_HOME_LAYOUT, parseHomeLayout("NEZNANO"))
    }
}
