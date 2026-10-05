package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.domain.subscriptions.hasEnded
import si.moneo.domain.subscriptions.monthlyCents
import si.moneo.domain.subscriptions.nextPaymentAfter
import si.moneo.domain.subscriptions.paymentsUntil
import java.time.LocalDate
import java.time.ZoneId

class SubscriptionScheduleTest {

    private fun LocalDate.millis() = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun sub(
        next: LocalDate,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        billingDay: Int = next.dayOfMonth,
        end: LocalDate? = null,
        amount: Long = 999,
    ) = SubscriptionEntity(
        title = "Netflix", amountCents = amount, frequency = frequency, interval = interval,
        nextPaymentDate = next.millis(), billingDay = billingDay, endDate = end?.millis(),
    )

    @Test fun monthlyKeepsBillingDayAfterShortMonth() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb = nextPaymentAfter(jan31, RecurrenceFrequency.MONTHLY, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        // Po februarju se vrne na 31., ne ostane na 28.
        assertEquals(LocalDate.of(2026, 3, 31), nextPaymentAfter(feb, RecurrenceFrequency.MONTHLY, 1, 31))
    }

    @Test fun quarterlyAndYearly() {
        val d = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 4, 15), nextPaymentAfter(d, RecurrenceFrequency.MONTHLY, 3, 15))
        assertEquals(LocalDate.of(2027, 1, 15), nextPaymentAfter(d, RecurrenceFrequency.YEARLY, 1, 15))
        assertEquals(LocalDate.of(2026, 1, 29), nextPaymentAfter(d, RecurrenceFrequency.WEEKLY, 2, 15))
    }

    @Test fun catchesUpMissedPaymentsIncludingToday() {
        val s = sub(LocalDate.of(2026, 7, 10))
        val dates = s.paymentsUntil(LocalDate.of(2026, 9, 10))
        assertEquals(listOf(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10)), dates)
    }

    @Test fun futurePaymentIsNotCharged() {
        assertTrue(sub(LocalDate.of(2026, 10, 5)).paymentsUntil(LocalDate.of(2026, 9, 30)).isEmpty())
    }

    @Test fun noPaymentsAfterEndDate() {
        val s = sub(LocalDate.of(2026, 7, 10), end = LocalDate.of(2026, 8, 20))
        assertEquals(2, s.paymentsUntil(LocalDate.of(2026, 12, 1)).size)
        assertFalse(s.hasEnded)
        assertTrue(s.copy(nextPaymentDate = LocalDate.of(2026, 9, 10).millis()).hasEnded)
    }

    @Test fun monthlyEquivalent() {
        assertEquals(999, sub(LocalDate.of(2026, 1, 1)).monthlyCents)
        assertEquals(1000, sub(LocalDate.of(2026, 1, 1), RecurrenceFrequency.YEARLY, amount = 12000).monthlyCents)
        assertEquals(1000, sub(LocalDate.of(2026, 1, 1), RecurrenceFrequency.MONTHLY, interval = 3, amount = 3000).monthlyCents)
    }
}
