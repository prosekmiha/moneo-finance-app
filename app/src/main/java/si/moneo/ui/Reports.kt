package si.moneo.ui

import si.moneo.R
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.DebtDirection
import si.moneo.data.db.entity.DebtEntity
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.domain.subscriptions.hasEnded
import si.moneo.domain.subscriptions.occurrencesUntil
import si.moneo.domain.subscriptions.paymentsUntil
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// ---------- Oznake ----------

/** "Dopust, hrana ,dopust" -> [Dopust, hrana] (brez praznih in podvojenih, ne glede na velike črke). */
fun parseTags(raw: String): List<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

fun joinTags(tags: List<String>): String = tags.joinToString(", ")

data class TagSummary(
    val tag: String,
    val count: Int,
    val expenseCents: Long,
    val incomeCents: Long,
    val first: LocalDate,
    val last: LocalDate,
)

/** Vse oznake z vsotami, najnovejše najprej. */
fun tagSummaries(txs: List<TransactionUi>): List<TagSummary> =
    txs.flatMap { tx -> tx.tags.map { it to tx } }
        .groupBy({ it.first.lowercase() }, { it })
        .map { (_, pairs) ->
            val list = pairs.map { it.second }
            val confirmed = list.filter { it.confirmed }
            TagSummary(
                tag = pairs.first().first,
                count = list.size,
                expenseCents = confirmed.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
                incomeCents = confirmed.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                first = list.minOf { it.date },
                last = list.maxOf { it.date },
            )
        }
        .sortedByDescending { it.last }

data class TagDetailState(
    val tag: String = "",
    val summary: TagSummary? = null,
    val transactions: List<TransactionUi> = emptyList(),
    val byCategory: List<CategorySpend> = emptyList(),
)

// ---------- Prihajajoča plačila ----------

enum class UpcomingKind { SUBSCRIPTION, RECURRING, DEBT_I_OWE, DEBT_OWED_TO_ME }

data class UpcomingPayment(
    val title: String,
    val amountCents: Long,
    val date: LocalDate,
    val kind: UpcomingKind,
    val type: TransactionType,
    val categoryUid: String? = null,
)

/** Naročnine, ponavljajoča pravila in roki dolgov v naslednjih [days] dneh (vključno z danes). */
fun upcomingPayments(
    subscriptions: List<SubscriptionEntity>,
    rules: List<RecurringRuleEntity>,
    debts: List<DebtEntity>,
    today: LocalDate,
    days: Long = 7,
): List<UpcomingPayment> {
    val until = today.plusDays(days)
    fun inWindow(d: LocalDate) = !d.isBefore(today) && !d.isAfter(until)
    val out = mutableListOf<UpcomingPayment>()
    subscriptions.filter { it.active && !it.hasEnded }.forEach { s ->
        val d = millisToLocalDate(s.nextPaymentDate)
        if (inWindow(d)) out += UpcomingPayment(s.title, s.amountCents, d, UpcomingKind.SUBSCRIPTION, TransactionType.EXPENSE, s.categoryUid)
    }
    rules.filter { it.enabled && !it.deleted }.forEach { r ->
        val d = millisToLocalDate(r.nextDueDate)
        if (inWindow(d)) out += UpcomingPayment(r.title, r.amountCents, d, UpcomingKind.RECURRING, r.type, r.categoryUid)
    }
    debts.filter { !it.settled && it.dueDate != null }.forEach { debt ->
        val d = millisToLocalDate(debt.dueDate!!)
        // Zapadle dolgove pokaži tudi, ko je rok že mimo (še niso vrnjeni)
        if (!d.isAfter(until)) {
            val owe = debt.direction == DebtDirection.BORROWED
            out += UpcomingPayment(
                debt.person, debt.remainingCents, d,
                if (owe) UpcomingKind.DEBT_I_OWE else UpcomingKind.DEBT_OWED_TO_ME,
                if (owe) TransactionType.EXPENSE else TransactionType.INCOME,
            )
        }
    }
    return out.sortedBy { it.date }
}

/** "danes", "jutri", "čez 3 dni", "zamuja 2 dni" ... */
fun relativeDayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, d)
    return when {
        days < 0 -> qty(R.plurals.overdue_days, (-days).toInt(), (-days).toInt())
        days == 0L -> str(R.string.relative_today)
        days == 1L -> str(R.string.relative_tomorrow)
        days == 2L -> str(R.string.relative_day_after)
        else -> qty(R.plurals.in_days, days.toInt(), days.toInt())
    }
}

// ---------- Načrt: kaj še pride ta mesec ----------

/** Kar se bo do konca meseca še zapisalo samodejno (od danes naprej, vključno z danes). */
data class MonthOutlook(
    val subscriptionsCents: Long,
    val recurringExpenseCents: Long,
    val recurringIncomeCents: Long,
) {
    val outgoingCents: Long get() = subscriptionsCents + recurringExpenseCents
}

fun monthOutlook(subscriptions: List<SubscriptionEntity>, rules: List<RecurringRuleEntity>, today: LocalDate): MonthOutlook {
    val monthEnd = YearMonth.from(today).atEndOfMonth()
    val subs = subscriptions.filter { it.active && !it.deleted && !it.hasEnded }
        .sumOf { s -> s.paymentsUntil(monthEnd).count { !it.isBefore(today) } * s.amountCents }
    fun rulesSum(type: TransactionType) = rules.filter { it.enabled && !it.deleted && it.type == type }
        .sumOf { r -> r.occurrencesUntil(monthEnd).count { !it.isBefore(today) } * r.amountCents }
    return MonthOutlook(subs, rulesSum(TransactionType.EXPENSE), rulesSum(TransactionType.INCOME))
}

// ---------- Skupni mesečni proračun ----------

data class OverallBudget(val spentCents: Long, val budgetCents: Long, val forecastCents: Long) {
    val fraction: Float get() = if (budgetCents <= 0) 0f else spentCents.toFloat() / budgetCents
    val leftCents: Long get() = budgetCents - spentCents
}

/** Poraba tekočega meseca proti skupnemu proračunu + napoved do konca meseca po dosedanjem tempu. */
fun overallBudget(txs: List<TransactionUi>, budgetCents: Long?, today: LocalDate): OverallBudget? {
    if (budgetCents == null || budgetCents <= 0) return null
    val month = YearMonth.from(today)
    val spent = txs.filter { it.type == TransactionType.EXPENSE && it.confirmed && YearMonth.from(it.date) == month }
        .sumOf { it.amountCents }
    val forecast = spent * month.lengthOfMonth() / today.dayOfMonth
    return OverallBudget(spent, budgetCents, forecast)
}

// ---------- Letni pregled ----------

data class MonthTotal(val month: YearMonth, val incomeCents: Long, val expenseCents: Long)

data class YearReview(
    val year: Int,
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val transactionCount: Int = 0,
    val months: List<PeriodBucket> = emptyList(),
    val monthTotals: List<MonthTotal> = emptyList(),
    val topCategories: List<CategorySpend> = emptyList(),
    val topIncome: List<CategorySpend> = emptyList(),
    val mostExpensiveMonth: MonthTotal? = null,
    val cheapestMonth: MonthTotal? = null,
    val biggestExpense: TransactionUi? = null,
    val subscriptionsCents: Long = 0,
    val averagePerDayCents: Long = 0,
    /** Sprememba stroškov glede na prejšnje leto v %, null = ni podatkov. */
    val expenseDeltaPct: Int? = null,
    val topTag: TagSummary? = null,
    val availableYears: List<Int> = emptyList(),
) {
    val netCents: Long get() = incomeCents - expenseCents
    val savingsRate: Int? get() = if (incomeCents <= 0) null else ((netCents.toDouble() / incomeCents) * 100).roundToInt()
    val hasData: Boolean get() = transactionCount > 0
}

fun buildYearReview(all: List<TransactionUi>, categories: List<CategoryEntity>, year: Int, today: LocalDate): YearReview {
    val years = (all.map { it.date.year } + today.year).distinct().sortedDescending()
    val txs = all.filter { it.date.year == year }
    val confirmed = txs.filter { it.confirmed }
    val expenses = confirmed.filter { it.type == TransactionType.EXPENSE }
    val income = confirmed.filter { it.type == TransactionType.INCOME }
    val expenseTotal = expenses.sumOf { it.amountCents }

    // Meseci do vključno tekočega (za tekoče leto) oz. vseh 12
    val lastMonth = if (year == today.year) today.monthValue else 12
    val monthTotals = (1..lastMonth).map { m ->
        val ym = YearMonth.of(year, m)
        val inMonth = confirmed.filter { YearMonth.from(it.date) == ym }
        MonthTotal(
            ym,
            inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
            inMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
        )
    }
    val withSpending = monthTotals.filter { it.expenseCents > 0 }
    val days = if (year == today.year) today.dayOfYear else LocalDate.of(year, 12, 31).dayOfYear
    val previousExpense = all.filter { it.date.year == year - 1 && it.confirmed && it.type == TransactionType.EXPENSE }
        // Za tekoče leto primerjaj isto obdobje lani (do istega dne)
        .filter { year != today.year || it.date.dayOfYear <= today.dayOfYear }
        .sumOf { it.amountCents }

    return YearReview(
        year = year,
        incomeCents = income.sumOf { it.amountCents },
        expenseCents = expenseTotal,
        transactionCount = txs.size,
        months = buildBuckets(txs, StatsPeriod.MONTH, 12, LocalDate.of(year, 12, 1)),
        monthTotals = monthTotals,
        topCategories = categorySpends(txs, categories, TransactionType.EXPENSE).take(5),
        topIncome = categorySpends(txs, categories, TransactionType.INCOME).take(3),
        mostExpensiveMonth = withSpending.maxByOrNull { it.expenseCents },
        cheapestMonth = withSpending.takeIf { it.size >= 2 }?.minByOrNull { it.expenseCents },
        biggestExpense = expenses.maxByOrNull { it.amountCents },
        subscriptionsCents = expenses.filter { it.subscriptionUid != null || it.source == TransactionSource.SUBSCRIPTION }
            .sumOf { it.amountCents },
        averagePerDayCents = expenseTotal / days.coerceAtLeast(1),
        expenseDeltaPct = percentDelta(expenseTotal, previousExpense),
        topTag = tagSummaries(txs).maxByOrNull { it.expenseCents }?.takeIf { it.expenseCents > 0 },
        availableYears = years,
    )
}
