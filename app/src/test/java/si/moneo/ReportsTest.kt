package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.data.db.entity.DebtDirection
import si.moneo.data.db.entity.DebtEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.TransactionUi
import si.moneo.ui.UpcomingKind
import si.moneo.ui.buildYearReview
import si.moneo.ui.joinTags
import si.moneo.ui.overallBudget
import si.moneo.ui.parseTags
import si.moneo.ui.tagSummaries
import si.moneo.ui.upcomingPayments
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

class ReportsTest {

    private val today = LocalDate.of(2026, 9, 15)
    private fun LocalDate.millis() = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun tx(
        cents: Long, date: LocalDate, type: TransactionType = TransactionType.EXPENSE,
        tags: List<String> = emptyList(), subscription: String? = null,
    ) = TransactionUi(
        uid = "$cents-$date-$type", type = type, amountCents = cents, date = date, comment = "",
        categoryUid = null, categoryTitle = null, categoryColor = null, accountUid = null, accountTitle = null,
        source = TransactionSource.MANUAL, confirmed = true, subscriptionUid = subscription, tags = tags,
    )

    @Test fun tagsAreTrimmedAndDeduplicated() {
        assertEquals(listOf("Dopust", "hrana"), parseTags(" Dopust, hrana ,dopust,, "))
        assertEquals("Dopust, hrana", joinTags(listOf("Dopust", "hrana")))
    }

    @Test fun tagSummaryIgnoresCase() {
        val s = tagSummaries(listOf(tx(1000, today, tags = listOf("Dopust")), tx(500, today.minusDays(2), tags = listOf("dopust"))))
        assertEquals(1, s.size)
        assertEquals(1500, s.first().expenseCents)
        assertEquals(2, s.first().count)
    }

    @Test fun upcomingWithinSevenDaysOnly() {
        val subs = listOf(
            SubscriptionEntity(title = "Netflix", amountCents = 1299, nextPaymentDate = today.plusDays(2).millis(), billingDay = 17),
            SubscriptionEntity(title = "Spotify", amountCents = 1099, nextPaymentDate = today.plusDays(20).millis(), billingDay = 5),
        )
        val debts = listOf(
            DebtEntity(person = "Marko", direction = DebtDirection.LENT, amountCents = 5000, date = 0, dueDate = today.minusDays(1).millis()),
            DebtEntity(person = "Ana", direction = DebtDirection.BORROWED, amountCents = 2000, repaidCents = 2000, date = 0, dueDate = today.millis()),
        )
        val up = upcomingPayments(subs, emptyList(), debts, today)
        assertEquals(listOf("Marko", "Netflix"), up.map { it.title }) // zapadel dolg + naročnina; Spotify predaleč, Ana poravnano
        assertEquals(UpcomingKind.DEBT_OWED_TO_ME, up.first().kind)
    }

    @Test fun overallBudgetForecast() {
        val b = overallBudget(listOf(tx(30000, today), tx(99999, today.minusMonths(1))), 100000, today)!!
        assertEquals(30000, b.spentCents)
        assertEquals(60000, b.forecastCents) // 15. v mesecu s 30 dnevi -> dvakratnik
        assertNull(overallBudget(emptyList(), null, today))
    }

    @Test fun yearReviewTotals() {
        val txs = listOf(
            tx(200000, LocalDate.of(2026, 1, 10), TransactionType.INCOME),
            tx(50000, LocalDate.of(2026, 3, 5)),
            tx(20000, LocalDate.of(2026, 5, 5)),
            tx(1299, LocalDate.of(2026, 5, 17), subscription = "s1"),
            tx(40000, LocalDate.of(2025, 3, 5)),
        )
        val r = buildYearReview(txs, emptyList(), 2026, today)
        assertEquals(200000, r.incomeCents)
        assertEquals(71299, r.expenseCents)
        assertEquals(Month.MARCH, r.mostExpensiveMonth!!.month.month)
        assertEquals(Month.MAY, r.cheapestMonth!!.month.month)
        assertEquals(1299, r.subscriptionsCents)
        assertEquals(50000, r.biggestExpense!!.amountCents)
        assertTrue(r.expenseDeltaPct!! > 0)
        assertEquals(listOf(2026, 2025), r.availableYears)
    }
}
