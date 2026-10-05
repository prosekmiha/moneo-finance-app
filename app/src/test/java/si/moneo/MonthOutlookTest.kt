package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Test
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.monthOutlook
import java.time.LocalDate
import java.time.ZoneId

class MonthOutlookTest {

    private val today = LocalDate.of(2026, 10, 15)
    private fun LocalDate.millis() = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun onlyRemainingPaymentsThisMonth() {
        val subs = listOf(
            SubscriptionEntity(title = "Netflix", amountCents = 1299, nextPaymentDate = LocalDate.of(2026, 10, 20).millis(), billingDay = 20),
            // tedenska: 15., 22., 29. oktober
            SubscriptionEntity(title = "Časopis", amountCents = 300, frequency = RecurrenceFrequency.WEEKLY, nextPaymentDate = today.millis(), billingDay = 15),
            SubscriptionEntity(title = "Spotify", amountCents = 1099, nextPaymentDate = LocalDate.of(2026, 11, 3).millis(), billingDay = 3),
            SubscriptionEntity(title = "Ustavljena", amountCents = 999, nextPaymentDate = LocalDate.of(2026, 10, 25).millis(), billingDay = 25, active = false),
        )
        val rules = listOf(
            RecurringRuleEntity(title = "Najemnina", type = TransactionType.EXPENSE, amountCents = 50000, frequency = RecurrenceFrequency.MONTHLY, nextDueDate = LocalDate.of(2026, 10, 31).millis()),
            RecurringRuleEntity(title = "Plača", type = TransactionType.INCOME, amountCents = 200000, frequency = RecurrenceFrequency.MONTHLY, nextDueDate = LocalDate.of(2026, 11, 10).millis()),
            RecurringRuleEntity(title = "Bonus", type = TransactionType.INCOME, amountCents = 10000, frequency = RecurrenceFrequency.MONTHLY, nextDueDate = LocalDate.of(2026, 10, 25).millis()),
        )
        val o = monthOutlook(subs, rules, today)
        assertEquals(1299L + 3 * 300L, o.subscriptionsCents)
        assertEquals(50000L, o.recurringExpenseCents)
        assertEquals(10000L, o.recurringIncomeCents)
        assertEquals(1299L + 900L + 50000L, o.outgoingCents)
    }
}
