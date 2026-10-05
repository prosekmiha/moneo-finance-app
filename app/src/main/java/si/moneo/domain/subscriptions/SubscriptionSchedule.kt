package si.moneo.domain.subscriptions

import si.moneo.R
import si.moneo.ui.str
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SubscriptionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/** Obračunska obdobja, ki jih ponuja urejevalnik naročnin. */
enum class BillingCycle(val frequency: RecurrenceFrequency, val interval: Int, private val labelRes: Int) {
    WEEKLY(RecurrenceFrequency.WEEKLY, 1, R.string.freq_weekly),
    BIWEEKLY(RecurrenceFrequency.WEEKLY, 2, R.string.freq_biweekly),
    MONTHLY(RecurrenceFrequency.MONTHLY, 1, R.string.freq_monthly),
    BIMONTHLY(RecurrenceFrequency.MONTHLY, 2, R.string.freq_bimonthly),
    QUARTERLY(RecurrenceFrequency.MONTHLY, 3, R.string.freq_quarterly),
    HALF_YEARLY(RecurrenceFrequency.MONTHLY, 6, R.string.freq_half_yearly),
    YEARLY(RecurrenceFrequency.YEARLY, 1, R.string.freq_yearly),
    ;

    val label: String get() = str(labelRes)

    companion object {
        fun of(frequency: RecurrenceFrequency, interval: Int): BillingCycle? =
            entries.firstOrNull { it.frequency == frequency && it.interval == interval }
    }
}

/** "mesečno", "četrtletno", "vsakih 5 dni" ... */
fun billingLabel(frequency: RecurrenceFrequency, interval: Int): String =
    BillingCycle.of(frequency, interval)?.label ?: when (frequency) {
        RecurrenceFrequency.DAILY -> if (interval == 1) str(R.string.freq_daily) else str(R.string.freq_every_n_days, interval)
        RecurrenceFrequency.WEEKLY -> str(R.string.freq_every_n_weeks, interval)
        RecurrenceFrequency.MONTHLY -> str(R.string.freq_every_n_months, interval)
        RecurrenceFrequency.YEARLY -> str(R.string.freq_every_n_years, interval)
    }

val SubscriptionEntity.billingLabel: String get() = billingLabel(frequency, interval)

/**
 * Naslednji datum plačila za [from]. Pri mesečnem/letnem obračunu se ohrani [billingDay]
 * (naročnina 31. se februarja plača 28./29., marca pa spet 31.).
 */
fun nextPaymentAfter(from: LocalDate, frequency: RecurrenceFrequency, interval: Int, billingDay: Int): LocalDate {
    val n = interval.coerceAtLeast(1).toLong()
    fun LocalDate.onBillingDay() = withDayOfMonth(min(billingDay.coerceIn(1, 31), lengthOfMonth()))
    return when (frequency) {
        RecurrenceFrequency.DAILY -> from.plusDays(n)
        RecurrenceFrequency.WEEKLY -> from.plusWeeks(n)
        RecurrenceFrequency.MONTHLY -> from.plusMonths(n).onBillingDay()
        RecurrenceFrequency.YEARLY -> from.plusYears(n).onBillingDay()
    }
}

fun SubscriptionEntity.nextPaymentAfter(from: LocalDate): LocalDate = nextPaymentAfter(from, frequency, interval, billingDay)

/**
 * Vsi termini od [first] do vključno [until], brez terminov po [end]. Skupno za naročnine
 * in ponavljajoča pravila (tudi zamujeni termini, npr. ko aplikacija nekaj dni ni tekla).
 */
fun occurrencesUntil(
    first: LocalDate,
    frequency: RecurrenceFrequency,
    interval: Int,
    billingDay: Int,
    end: LocalDate?,
    until: LocalDate,
): List<LocalDate> {
    val out = mutableListOf<LocalDate>()
    var d = first
    while (!d.isAfter(until) && (end == null || !d.isAfter(end)) && out.size < MAX_CATCH_UP) {
        out += d
        d = nextPaymentAfter(d, frequency, interval, billingDay)
    }
    return out
}

/** Vsa plačila od [SubscriptionEntity.nextPaymentDate] do vključno [until] (brez plačil po koncu naročnine). */
fun SubscriptionEntity.paymentsUntil(until: LocalDate): List<LocalDate> =
    occurrencesUntil(nextPaymentDate.toLocalDate(), frequency, interval, billingDay, endDate?.toLocalDate(), until)

/** Dan obračuna pravila; pri starih pravilih (brez shranjenega dneva) dan naslednjega termina. */
val RecurringRuleEntity.effectiveBillingDay: Int
    get() = billingDay ?: nextDueDate.toLocalDate().dayOfMonth

fun RecurringRuleEntity.nextAfter(from: LocalDate): LocalDate = nextPaymentAfter(from, frequency, interval, effectiveBillingDay)

/** Zapadli termini pravila do vključno [until]. */
fun RecurringRuleEntity.occurrencesUntil(until: LocalDate): List<LocalDate> =
    occurrencesUntil(nextDueDate.toLocalDate(), frequency, interval, effectiveBillingDay, endDate?.toLocalDate(), until)

/** true = naročnina se ne bo več zaračunala (naslednje plačilo je po koncu naročnine). */
val SubscriptionEntity.hasEnded: Boolean
    get() = endDate != null && nextPaymentDate > endDate

/** Strošek naročnine, preračunan na mesec (za povzetek). */
val SubscriptionEntity.monthlyCents: Long
    get() {
        val n = interval.coerceAtLeast(1)
        return when (frequency) {
            RecurrenceFrequency.DAILY -> amountCents * 365 / 12 / n
            RecurrenceFrequency.WEEKLY -> amountCents * 52 / 12 / n
            RecurrenceFrequency.MONTHLY -> amountCents / n
            RecurrenceFrequency.YEARLY -> amountCents / (12L * n)
        }
    }

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Varovalka pred neskončno zanko pri napačnih podatkih (npr. tedenska naročnina izpred 20 let). */
private const val MAX_CATCH_UP = 500
