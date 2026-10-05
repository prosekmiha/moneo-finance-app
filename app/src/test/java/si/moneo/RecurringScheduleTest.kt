package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Test
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.repo.FinanceRepository
import si.moneo.domain.subscriptions.effectiveBillingDay
import si.moneo.domain.subscriptions.nextAfter
import si.moneo.domain.subscriptions.occurrencesUntil
import java.time.LocalDate
import java.time.ZoneId

class RecurringScheduleTest {

    private fun LocalDate.millis() = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun rule(next: LocalDate, billingDay: Int? = null, end: LocalDate? = null) = RecurringRuleEntity(
        title = "Najemnina", type = TransactionType.EXPENSE, amountCents = 50000,
        frequency = RecurrenceFrequency.MONTHLY, nextDueDate = next.millis(), billingDay = billingDay, endDate = end?.millis(),
    )

    @Test fun allMissedOccurrencesAtOnce() {
        val dates = rule(LocalDate.of(2026, 7, 1)).occurrencesUntil(LocalDate.of(2026, 9, 30))
        assertEquals(listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)), dates)
    }

    @Test fun keepsDay31AfterFebruary() {
        val r = rule(LocalDate.of(2026, 2, 28), billingDay = 31)
        assertEquals(LocalDate.of(2026, 3, 31), r.nextAfter(LocalDate.of(2026, 2, 28)))
    }

    @Test fun oldRulesUseDayOfNextDue() {
        assertEquals(15, rule(LocalDate.of(2026, 10, 15)).effectiveBillingDay)
    }

    @Test fun stopsAtEndDate() {
        val dates = rule(LocalDate.of(2026, 7, 1), end = LocalDate.of(2026, 8, 15)).occurrencesUntil(LocalDate.of(2026, 12, 1))
        assertEquals(2, dates.size)
    }

    @Test fun sameOccurrenceSameTransactionUid() {
        val d = LocalDate.of(2026, 9, 1)
        assertEquals(FinanceRepository.recurringTxUid("r1", d), FinanceRepository.recurringTxUid("r1", d))
        assert(FinanceRepository.recurringTxUid("r1", d) != FinanceRepository.recurringTxUid("r1", d.plusMonths(1)))
    }
}
